# Como montar o front: telas, chamadas e conexões

Este guia mostra **a ordem das coisas**: o que cada tela chama, quando, e quais conexões ficam abertas. Não define o
visual. Os JSON completos de cada rota estão em:
- `backend/README.md`: rotas em geral (cadastro, rota, van, contrato, chat, arquivos);
- `docs/FLUXO_TRIP.md`: execução da rota, WebSocket da trip e notificações;
- `docs/FLUXO_CONTRATO.md`: contrato passo a passo.

Base: `http://HOST:9090` (HTTP) e `ws://HOST:9090` (WebSocket).

---

## 1. As três peças que vêm antes das telas

Monte estas três peças primeiro. Toda tela usa elas.

### 1.1 Cliente HTTP (um só pro app todo)
- Guarda o `token` e o `refreshToken` no **armazenamento seguro** do celular (Expo: `expo-secure-store`). Nunca no AsyncStorage.
- Põe `Authorization: Bearer <token>` em toda chamada.
- **Deu 401:**
  1. chama `POST /api/auth/refresh` `{"refreshToken"}` **uma vez só**, mesmo que várias chamadas tenham falhado juntas (as outras esperam essa terminar);
  2. guarda o **par novo** (os dois tokens mudam);
  3. repete as chamadas que falharam;
  4. se o refresh também der 401, apaga os tokens e vai pro login.
- Erro do backend vem sempre como `{"timestamp", "message", "details"}`. Mostre o `message` pro usuário: ele já vem em português.

| Status | O que a tela faz |
|---|---|
| 400 | dado inválido: mostra o `message` perto do campo |
| 401 | o cliente renova sozinho; se não der, volta pro login |
| 403 | "você não tem acesso a isso" |
| 404 | "não encontrado" (ou trata como lista vazia, quando fizer sentido) |
| 409 | regra de negócio (ex.: "A van está a 5.6 km do ponto do aluno"): mostra o `message` |
| 503 | serviço de mapas fora/cota: "tente mais tarde" |

### 1.2 Gerenciador de WebSocket (reaproveitado pelas 3 conexões)
- URL com o token: `ws://HOST/<caminho>?token=<token atual>`.
- **Caiu** (rede, app em segundo plano): reconecta com espera crescente (1 s, 2 s, 5 s, 10 s) e com o **token atual**. Se o token venceu, renova antes.
- **Código 1003:** o usuário não tem acesso (não é do chat, não é da trip). **Não reconecte.**
- **Ao reconectar, chega o estado atual na hora**: o front não precisa pedir nada.

### 1.3 As conexões e quando ficam abertas

| WebSocket | Abre quando | Fecha quando | O que chega |
|---|---|---|---|
| `/notifications?token=` | o app abre, logado | logout | `unread-count` ao conectar e `notification` a cada aviso |
| `/chat/{chatId}?token=` | a tela do chat abre | sai da tela do chat | cada mensagem nova (dos dois lados e do sistema) |
| `/trip/{tripId}?token=` | motorista executando a rota, ou pai acompanhando | a trip termina, ou sai da tela | `trip-update` a cada mudança (GPS, embarque, falta...) |

**Não tem polling em lugar nenhum.** Atualizações de tempo real chegam pelos WebSockets. O HTTP é usado só ao abrir uma tela e nas ações do usuário.

---

## 2. Abrir o app

```
splash
 ├─ tem refreshToken salvo?
 │    não → tela de login
 │    sim → GET /api/users/me  (se der 401, o cliente renova sozinho; se não der, login)
 ├─ roles contém ROLE_DRIVER → app do motorista, senão → app do responsável
 ├─ abre ws /notifications   (badge = unread-count)
 └─ home do perfil
```

`GET /api/users/me` devolve `{id, name, email, phone, cpf, roles, photoKey, linkCode}`. Guarde no estado global:
- `id`: pra saber quais mensagens do chat são suas (`senderId === me.id`);
- `linkCode`: só vem pro motorista. É o código que ele passa pros pais.

---

## 3. Cadastro e login

### Responsável
1. `POST /api/responsible/auth/signup` com nome, e-mail, CPF, telefone, senha, confirmação e **endereço**. A senha precisa de maiúscula, minúscula, número e símbolo.
2. Tela "digite o código do e-mail": `POST /api/auth/verify-email` `{email, code}`.
   - Botão "reenviar": `POST /api/auth/send-verification-code?email=...`.
3. Login.

### Motorista
1. Escolhe o PDF da **CNH digital**. Pra validar antes do resto do formulário: `POST /api/driver/auth/verify-CNH` (multipart `documentPdf`). **422** = não é uma CNH digital válida.
2. `POST /api/driver/auth/signup` (multipart): `documentPdf`, `name`, `email`, `password`, `confirmPassword`, `phone`. O CPF sai da própria CNH.
3. Código do e-mail e login, igual ao responsável.

### Login
`POST /api/auth/login` `{cpf, passwordHash}` (o campo se chama `passwordHash`, mas vai a senha digitada)
→ `{token, expiresIn, refreshToken, refreshExpiresIn}`. Salva os dois e segue o fluxo da seção 2.

### Sair
`POST /api/auth/logout` `{refreshToken}`, apaga os tokens e fecha os WebSockets.

---

## 4. Perfil e foto (os dois perfis)

| Ação | Chamadas |
|---|---|
| ver foto de alguém | `photoKey` (do `/me` ou do chat) → `GET /api/storage/download-url?fileKey=` → usa o `downloadUrl` no `<Image>`. O link vale 15 min, então guarde em cache por pouco tempo |
| trocar a foto | `GET /api/users/photo-upload-url?fileName=&contentType=image/jpeg` → `PUT <uploadUrl>` com a imagem (direto no MinIO, sem token) → `PUT /api/users/update-photo` `{fileKey}` |
| editar e-mail/telefone | `POST /api/users/send-code-updateUser` (manda um código pro e-mail) → `PUT /api/users/update-user` `{email, phone, code}` |

---

## 5. App do motorista

### 5.1 Primeiro uso (preparação)

| Tela | Ao abrir | Ações |
|---|---|---|
| Minhas escolas | `GET /api/school/my-schools` | adicionar: `POST /api/school/add-school`. Se a escola já existe, só vincula |
| Minhas vans | `GET /api/vehicle/my-vehicles` | adicionar pelo **CRLV digital**: `POST /api/vehicle/add-vehicle` (multipart `documentPdf`; **422** = PDF inválido). Parar de usar: `PUT /api/vehicle/{id}/unlink` |
| Meu código | `/me.linkCode` | mostrar pra passar pros pais (copiar/compartilhar) |

### 5.2 Conversar e fechar contrato
1. **Lista de conversas:** `GET /api/chats`, com `otherUserId`, `otherUserName` e `otherUserPhotoKey`. As conversas nascem quando o pai usa o código do motorista.
2. **Tela do chat:**
   - ao abrir: `GET /api/chats/{chatId}/messages` e abre o `ws /chat/{chatId}`;
   - enviar: `POST /api/chats/{chatId}/messages` `{content}` (com anexo: seção 8).
3. **Mensagens do sistema:** `messageType` diferente de `REGULAR_CHAT` vira um **card**:
   - `PROPOSAL`: proposta (botão "ver");
   - `APPROVAL` e `REJECTION`: resposta da proposta;
   - `ABSENCE`: falta avisada.
4. **Propor contrato** (botão no chat, `docs/FLUXO_CONTRATO.md`):
   - o aluno sai de `GET /api/chats/{chatId}/students`;
   - a escola sai de `GET /api/school/my-schools`;
   - o PDF é opcional (seção 8);
   - envia com `POST /api/chats/{chatId}/contract`.
5. **Meus contratos:** `GET /api/contract/my-contracts?status=ACTIVE`. Detalhe: `GET /api/contract/{id}`.

### 5.3 Montar uma rota (uma vez, e quando mudar)

```
Minhas rotas ── GET /api/route/get-all-routes-and-stops
 └─ Nova rota ── POST /api/route/create-route {name}       (ex.: "Ida manhã"; a volta é outra rota)
     └─ Editor da rota ── GET /api/route/{routeId}/stops    (pontos em ordem, com alunos e escolas)
         ├─ + aluno na rota .... GET /api/route/stop-options   (alunos dele com os endereços)
         │                        POST /api/route/{routeId}/add-student/{studentId}
         │                        {action, locationType, studentAddressId?, weekdays?}   → devolve {stops, pending}
         │                        o backend cria/reaproveita o ponto (SCHOOL = a escola do contrato) e põe na ordem certa
         │                        (tela: "Ana · Casa · embarca · seg-sex"; depois "Ana · Escola · desce")
         ├─ trocar ordem ....... PUT /api/route/stop/{stopId}/swap-order {orderIndex}
         ├─ mover aluno ........ PUT /api/route/student-stop/{id}/move {routeStopId}
         ├─ dias do aluno ...... PUT /api/route/student-stop/{id}/weekdays {weekdays}
         ├─ remover ............ DELETE student-stop / school-stop / stop
         ├─ van padrão ......... PUT /api/route/{routeId}/set-vehicle/{vehicleId}
         └─ pendências ......... GET /api/route/{routeId}/pending   (lista vazia = rota pronta)
```

**Pro editor (design):**
- mostre as pendências como aviso ("Ana sem desembarque na sexta");
- a rota só pode ser iniciada sem pendências;
- cada aluno precisa de um **embarque** e um **desembarque**;
- na ida, normalmente o embarque é em casa e o desembarque na escola.

### 5.4 Executar a rota (tela estilo iFood)

```
Tela da rota (abre)
 └─ GET /api/trip/route/{routeId}/today
     ├─ 404 ............ botão "Iniciar rota"
     ├─ 200 ativa ...... "Continuar" (só abre o WebSocket) | "Recomeçar do zero"
     └─ 200 FINISHED ... "Rota de hoje finalizada"

Iniciar
 ├─ pede permissão de localização (de preferência "sempre", pra seguir com a tela apagada)
 ├─ pega o GPS atual
 ├─ POST /api/trip/start/{routeId} {latitude, longitude, vehicleId?}   (sem vehicleId = van padrão)
 ├─ abre ws /trip/{tripId}
 └─ liga o watchPosition: a cada 3–5 s manda {"type":"position","latitude","longitude"} pelo WebSocket

Durante (tudo que aparece na tela vem do trip-update do WebSocket)
 ├─ mapa: a van (latitude/longitude), a linha (path, [lng,lat] → inverter), os pontos (stops)
 ├─ barra do topo: "Próximo ponto em X min" (targetStopId + etaSeconds)
 ├─ lista de pontos: alunos de cada ponto com o status
 ├─ "embarcou" → PUT /api/trip/{tripId}/student/{id}/board
 ├─ "desceu"   → PUT /api/trip/{tripId}/student/{id}/alight
 ├─ "ausente"  → PUT /api/trip/{tripId}/student/{id}/absent   (não apareceu)
 │     embarcou/desceu só funcionam com a van a até 1 km do ponto: o 409 traz a distância
 ├─ "navegar" → abre Google Maps/Waze com os pontos em ordem (lat/lng dos stops)
 └─ todos atendidos (targetStopId === null): aviso + botão grande "Finalizar rota"

Finalizar → POST /api/trip/{tripId}/finish → fecha o WebSocket e para o GPS
```

**Pontos de atenção:**
- **O app precisa mandar GPS com a tela apagada**, porque o motorista dirige com o celular no suporte. No Expo, isso é o `expo-location` com tarefa em segundo plano (`startLocationUpdatesAsync`) e a permissão "sempre".
- **Recomeçar** cria uma trip nova, com **id novo**: troque o WebSocket.
- **Os botões não mudam a tela sozinhos:** depois de um PUT, espere o `trip-update` (ele chega em menos de 1 s) ou use a resposta do PUT.

---

## 6. App do responsável

### 6.1 Filhos

| Tela | Chamadas |
|---|---|
| Meus filhos | `GET /api/student/my-children` (paginado: `content[]`) |
| Cadastrar filho | `POST /api/student/create-student` `{name, birthDate, relationType, notes?, address \| addressId, addressLabel?}`. `relationType`: `FINANCIAL`, `LEGAL` ou `WITHDRAWAL`. O endereço vira o "Casa" do filho. Pro irmão, mande o `addressId` da casa do outro (sem digitar de novo) |
| Endereços do filho (onde é buscado/deixado) | `GET /api/student/{id}/addresses`; novo: `POST .../addresses {label, address}` ou reaproveitado: `{label, addressId}`; tirar: `DELETE .../addresses/{id}` (apaga só a ligação; o motorista é avisado e escolhe o novo ponto). No cadastro do filho dá pra mandar vários de uma vez (`addresses: [...]`). **Dica de tela:** antes do campo de endereço, mostre os endereços que a família já tem (o seu de cobrança e os dos outros filhos) pra escolher, em vez de digitar de novo. Se digitar igual, o backend reaproveita sozinho |
| Dividir com outro responsável | quem cadastrou: `POST /api/student/generate-link` `{id, relationType}` → devolve um código. O outro responsável: `POST /api/student/confirm-link` `{code}` |
| Rotas do filho | `GET /api/student/{id}/routes` → `[{id, name}]` |

### 6.2 Achar o motorista e fechar contrato
1. Tela "Tenho um código": `POST /api/invite-code/redeem` `{inviteCode}` → devolve o `chatId` (abre o chat).
2. No chat, a proposta chega como card `PROPOSAL`. O `payload` é texto com JSON dentro: `JSON.parse(payload).contractId`.
3. **Ver proposta:**
   - `GET /api/contract/{contractId}`;
   - o PDF: `GET /api/storage/download-url?fileKey=<documentFileKey>`.
4. **Responder:**
   - aceitar: `PUT /api/chats/{chatId}/contracts/{contractId}/accept`;
   - recusar: `.../reject`.

### 6.3 Dia a dia: a home do responsável
```
Home (cards por filho)
 ├─ GET /api/student/{id}/today-trips  → trips de hoje desse filho
 │     []  → "A van ainda não saiu"
 │     [ ] → card da trip com o status do filho e o botão "Acompanhar"
 ├─ GET /api/student/{id}/absences     → faltas de hoje em diante
 └─ escuta o ws /notifications:
       TRIP_STARTED (payload.tripId) → atualiza o card (sem polling) e pode abrir o acompanhamento direto
```
Chame o `today-trips` **ao abrir a home**, ao voltar pro app e quando chegar uma notificação de trip. **Não fique repetindo**: quem avisa que a van saiu é a notificação.

**Faltas:**

| Botão | Quando mostrar | Chamada |
|---|---|---|
| "Não vai hoje" / "não vai dia X" | antes da viagem, ou durante ela **enquanto o filho não embarcou** | `POST /api/student/{id}/absences` `{routeId, date}` |
| "Ele vai sim" | **só antes de a viagem do dia começar** | `DELETE /api/student/{id}/absences/{absenceId}` |

### 6.4 Acompanhar (tela estilo Uber)
Abre o `ws /trip/{tripId}`. Cada `trip-update` traz um `mode`, e a tela inteira é decidida por ele:

| `mode` | O que desenhar |
|---|---|
| `FOLLOWING` | mapa com a van, a **linha até o destino do filho** (`path`), o pino do destino (casa ou escola, pelo `action`/`locationType` do filho) e a caixa "**X MIN**" (`etaSeconds`) |
| `ON_BOARD` | "Seu filho está na van": **só a van** no mapa (sem linha, sem tempo); a van ainda vai buscar outros alunos |
| `DONE` | "Acompanhamento encerrado: Ana foi entregue às 07:32" (`students[].deliveredAt`); pode fechar o WebSocket |
| `STATUS` | sem mapa: só o status do filho ("aguardando", "embarcou", "ausente") |

Mais duas regras:
- `status: CANCELED` = o motorista recomeçou a rota: chame `today-trips` de novo e abra a trip nova.
- `status: FINISHED`: feche o WebSocket.

Pro mapa:
- `path` vem como `[longitude, latitude]`;
- a van **vira** pela direção do movimento (posição anterior → nova) e **desliza** entre as posições;
- o tempo do `etaSeconds` já vem descontado; entre as mensagens, pode ir diminuindo no relógio do celular.

---

## 7. Notificações (os dois perfis)

| Tela/lugar | Chamadas |
|---|---|
| badge do sino | `unread-count` que chega ao conectar no `ws /notifications` (+1 a cada `notification`) |
| lista | `GET /api/notifications?page=0&size=20` (`?unread=true` só as não lidas) |
| tocar numa | `PUT /api/notifications/{id}/read` e navega pelo `type` (tabela abaixo) |
| "marcar todas" | `PUT /api/notifications/read-all` |

Pra onde cada notificação leva (`payload` já é um objeto):

| `type` | Quem recebe | Abre |
|---|---|---|
| `CONTRACT_PROPOSED` | responsável | contrato (`payload.contractId`) ou o chat (`payload.chatId`) |
| `CONTRACT_ACCEPTED` / `CONTRACT_REJECTED` | motorista | contrato / chat |
| `TRIP_STARTED` | responsável | acompanhamento (`payload.tripId`) |
| `VAN_ON_THE_WAY` | responsável | acompanhamento (`payload.tripId`, com `etaSeconds`) |
| `STUDENT_BOARDED` / `STUDENT_DELIVERED` / `STUDENT_ABSENT` | responsável | acompanhamento / home do filho |
| `ABSENCE_REPORTED` | motorista | chat com o pai (`payload.chatId`) |

Com o app **aberto**, mostre um aviso no topo (toast) quando chegar uma `notification`. Com o app **fechado**, ainda não há push (seção 10).

---

## 8. Arquivos (anexo do chat e PDF do contrato)
```
1. GET /api/storage/presigned-url?fileName=foto.jpg&contentType=image/jpeg  → {uploadUrl, fileKey}
2. PUT <uploadUrl> com o arquivo e o mesmo Content-Type   (direto no MinIO, sem Authorization)
3. usa o fileKey:
   - chat:     POST /api/chats/{chatId}/messages {content?, attachmentType: IMAGE|CONTRACT_DOCUMENT|OTHER, attachmentUrl: fileKey}
   - contrato: document.url da proposta
Pra mostrar: GET /api/storage/download-url?fileKey=... → downloadUrl (vale 15 min)
```

---

## 9. Mapa de telas → chamadas (resumo)

| Tela | Ao abrir | Conexão aberta |
|---|---|---|
| Splash | `/api/users/me` | `ws /notifications` |
| Login / cadastro | — | — |
| Chats | `GET /api/chats` | — |
| Chat | `GET /api/chats/{id}/messages` | `ws /chat/{id}` |
| Contrato | `GET /api/contract/{id}` | — |
| Motorista: rotas | `GET /api/route/get-all-routes-and-stops` | — |
| Motorista: editor da rota | `GET /api/route/{id}/stops`, `/pending` | — |
| Motorista: executar | `GET /api/trip/route/{id}/today` | `ws /trip/{tripId}` + GPS |
| Responsável: home | `my-children`, `today-trips`, `absences` | — |
| Responsável: acompanhar | — (o estado chega ao conectar) | `ws /trip/{tripId}` |
| Notificações | `GET /api/notifications` | (o `ws /notifications` já está aberto) |

---

## 10. O que o front ainda não tem do backend
- **Push com o app fechado** (Firebase Cloud Messaging / Expo Push). Hoje, a notificação fica guardada e aparece ao abrir o app.
- **"Esqueci a senha"** não está nas rotas atuais.
- **Cota da API de mapas:** a chave grátis tem um limite diário (200 rotas). Quando ele acaba:
  - o cadastro de endereço dá **503**;
  - a trip continua funcionando, mas **sem a linha e sem o tempo**: trate `path`/`etaSeconds` `null` mostrando só a van.

## 11. O que o front precisa mudar (endereços e rota)
O backend da branch `feature/student-addresses` mudou endereços e montagem da rota. Hoje o front **não chama nenhuma rota de ponto**, então nada quebrou; a lista abaixo é o que falta fazer para usar o que mudou. Detalhes de cada endpoint: `backend/README.md`, seção 1, e `docs/FLUXO_ROTA.md`.

### Responsável: filho e endereços
| Onde | O que mudar |
|---|---|
| `services/dto/student/student.dto.ts` (`createStudentRequest`) | `address` passou a ser **opcional**. Novos campos: `addressId?` (reaproveita um endereço da família), `addressLabel?` (apelido, padrão "Casa") e `addresses?: { label?, address?, addressId? }[]` (vários de uma vez). Mande **um** jeito: `address`, `addressId` ou `addresses` |
| `services/dto/student/student.dto.ts` (`studentResponsibleResponse`) | `studentResponseDTO` agora traz `studentAddressResponseDTO: { id, label, address }` (o endereço "Casa") |
| `components/student/add-student` | continua funcionando como está (`address` vira o "Casa"). Melhoria: antes do formulário, mostrar os endereços que a família já tem (`GET /api/student/{id}/addresses` dos outros filhos) e mandar `addressId` em vez de digitar de novo |
| tela nova: **endereços do filho** | `GET /api/student/{id}/addresses` → `[{ id, label, address }]`; adicionar: `POST .../addresses` `{ label, address }` ou `{ label, addressId }` (`409` se o filho já tem esse endereço); tirar: `DELETE .../addresses/{id}` (`409` se é o último ou tem viagem rodando com ele) |
| mensagens | mesmo **CEP + número** = mesmo endereço: o backend reaproveita sozinho. A latitude/longitude mandada pelo app é ignorada |

### Motorista: montar a rota (tela ainda não existe; `pages/driver-route` tem só o TODO)
| Ação | Chamada |
|---|---|
| criar a rota | `POST /api/route/create-route` `{ name }` |
| escolher aluno e endereço | `GET /api/route/stop-options` → `{ students: [{ studentId, studentName, addresses: [{ id, label, address }] }] }`. **Não tem mais `schools`** |
| pôr o aluno na rota | `POST /api/route/{routeId}/add-student/{studentId}` `{ action, locationType, studentAddressId?, weekdays?, orderIndex? }`. **Uma chamada por ação**: embarque e desembarque são duas. `SCHOOL` não manda endereço (é a escola do contrato, criada se ainda não está na rota) |
| trocar ordem / mover / dias | `PUT /api/route/stop/{id}/swap-order`, `PUT /api/route/student-stop/{id}/move`, `PUT /api/route/student-stop/{id}/weekdays` |
| excluir | `DELETE /api/route/student-stop/{id}`, `/school-stop/{id}`, `/stop/{id}`: agora devolvem **`200` com corpo**, não `204` |

**Resposta de toda alteração:** `{ stops: RouteStopResponse[], pending: RoutePendingResponse[] }`. A tela redesenha a rota com `stops` (não precisa de outro GET) e mostra `pending` como aviso: "Ana: falta desembarque na sexta". `pending` vazio = rota pronta.

**Iniciar a viagem** com pendência dá `400` com a mensagem dizendo quem e o que falta: mostre a `message`.

**Não use mais** (foram removidas): `POST /api/route/{routeId}/stops`, `POST /api/route/stop/{id}/add-student/{studentId}`, `POST /api/route/stop/{id}/add-school/{schoolId}`.

**Tipos sugeridos** (`services/dto/route/route.dto.ts`):
```ts
export interface RouteEditResponse { stops: RouteStop[]; pending: RoutePending[]; }
export interface RoutePending { studentId: string; studentName: string; missingBoarding: WeekDay[]; missingAlighting: WeekDay[]; }
export interface AddStudentToRouteRequest {
  action: 'BOARDING' | 'ALIGHTING';
  locationType: 'HOME' | 'SCHOOL' | 'OTHER';
  studentAddressId?: string;   // só HOME/OTHER
  weekdays?: WeekDay[];        // sem = todos os dias
  orderIndex?: number;         // sem = automático
}
```

