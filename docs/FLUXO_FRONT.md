# Guia do front: o que existe, o que está quebrado, o que falta e a ordem das chamadas

Este é o documento de construção do front. Ele diz:
- o que já existe;
- o que está quebrado e como consertar;
- quais telas faltam;
- para cada tela, **quais rotas da API chamar e em que ordem**.

O formato completo dos JSON de cada domínio está nos outros docs. Aqui fica só o necessário para montar as telas:

| Doc | Assunto |
|---|---|
| `backend/README.md` | todas as rotas, erros e perfis |
| `docs/FLUXO_CONTRATO.md` | proposta, aceite, renovação, cancelamento com aviso prévio, perfil do responsável |
| `docs/FLUXO_PAGAMENTO.md` | chave Pix e parcelas |
| `docs/FLUXO_ROTA.md` | montar a rota (pontos, alunos, ordem, pendências) |
| `docs/FLUXO_TRIP.md` | executar e acompanhar a viagem, WebSocket da viagem, faltas, notificações |
| `docs/WORKER.md` | o que muda sozinho todo dia (o front só vê o resultado) |

**Como trabalhar:**
1. Siga os padrões da seção 1: serviços, DTOs, interceptor, guardas, pop-up e sinais. Não crie um padrão novo.
2. Antes de mexer numa tela existente, corrija os itens da seção 3 (quebrados).
3. Depois construa as telas da seção 6, na ordem da seção 9.
4. Cada chamada nova vai num serviço em `services/` com o DTO em `services/dto/<dominio>/`.

---

## 1. Como o front é feito (siga esses padrões)

- **Angular 21**, componentes `standalone`, estado com `signal`/`computed`, `inject()`. Testes em Vitest (`*.spec.ts`).
- **Pastas:**
  - `pages/` = tela com rota própria;
  - `components/` = pedaços reaproveitados ou telas antigas;
  - `shared/` = header, footer, `trail-header` (cabeçalho com "voltar"), botão, `code-input`, máscaras (`shared/directives`: CPF, CEP com busca, telefone, data, nome) e `pop-up`.
- **API:**
  - `services/api.ts` define `API_URL = '/api'`;
  - o `proxy.conf.json` do `ng serve` repassa `/api` pro backend, `/ws` pros WebSockets e `/storage` pro MinIO;
  - WebSocket: `wsUrl('/chat/' + id)` (vira `ws(s)://<host>/ws/chat/...`);
  - links do MinIO: passe por `urlDoStorage(url)` antes de usar.
- **Sessão** (`services/auth.service.ts` + `interceptor/auth.interceptor.ts`):
  - `token` e `refreshToken` ficam no `localStorage`;
  - o interceptor põe o `Authorization` em tudo menos `/auth/*`;
  - com o token vencido, ele renova antes; com 401, renova **uma vez** e repete;
  - se a renovação também falhar, limpa a sessão e volta pra `/`.
- **Erros:** o interceptor mostra o `message` do backend num pop-up (`PopUpService.showError`). Se a tela trata o erro sozinha, mande `context: new HttpContext().set(SKIP_ERROR_POPUP, true)` na chamada.
- **Guardas** (`interceptor/route.interceptor.ts`):
  - `guestGuard` nas telas públicas (quem já está logado vai pra sua home);
  - `routeGuard` + `roleGuard` com `data: { rolesPermitted: ['ROLE_DRIVER' | 'ROLE_RESPONSIBLE'] }` nas telas logadas.

  **Toda tela nova usa isso.**
- **Rotas novas** em `app.routes.ts` com `loadComponent` (lazy), igual a `contracts`, `contract-view` e `notifications`.
- **Ícones:** Bootstrap Icons (`bi-...`).
- **Datas:** a API manda e recebe `AAAA-MM-DD`, e horários sem fuso no horário de São Paulo. Use os helpers que já existem em `contract.dto.ts` (`isoParaData`, `valorEmReais`, `periodicidadeLabel`).
- **IDs:** o front **nunca inventa ID**. Todo ID veio de uma resposta anterior da API.

### É web, não app nativo
O doc antigo falava de Expo e `expo-secure-store`; **não vale**. Consequências:
- **GPS do motorista:** `navigator.geolocation.watchPosition`.
  - Só funciona em **HTTPS** (ou `localhost`). No celular, use o túnel (cloudflared).
  - O navegador **não manda GPS com a aba fechada ou a tela apagada**. Use a Wake Lock API (`navigator.wakeLock.request('screen')`) na tela da viagem e avise o motorista pra deixar a tela aberta.
- **Notificação com o app fechado (push):** não existe ainda (issue #150). As notificações chegam pelo WebSocket com a aba aberta e ficam guardadas.
- **Mapa:** o front ainda não tem biblioteca de mapa. Leaflet com OpenStreetMap resolve. O `path` da viagem vem como `[longitude, latitude]`: inverta pro Leaflet.

---

## 2. O que já existe

| Rota Angular | Arquivo | Perfil | O que faz | Situação |
|---|---|---|---|---|
| `/` | `pages/home` | público | escolher motorista ou responsável | ok |
| `/login/:tipo` | `pages/auth/login` + `components/login-signup/login-form` | público | login, "esqueci a senha" (`forgot-password`) | ok; falta tratar o **429** (seção 3) |
| `/signup/:tipo` | `pages/auth/signup` + `signup-driver` / `signup-responsible` | público | cadastro | **quebrado**: falta o aceite dos termos (seção 3.1) |
| `/email-code` | `components/login-signup/email-code` | público | código do e-mail e reenvio | ok |
| `/responsible-home` | `pages/responsible-home` | responsável | home com os filhos | parcial: sem viagens do dia, sem faltas |
| `/add-student` | `components/student/add-student` | responsável | cadastrar filho, gerar código de vínculo | ok |
| `/student-code` | `components/student/student-code` | responsável | usar o código de vínculo de outro responsável | ok |
| `/dependentes` | `components/student/dependentes` | responsável | lista de filhos | parcial: sem detalhe do filho |
| `/driver-code` | `components/driver-code` | responsável | resgatar o código do motorista (abre o chat) | ok |
| `/driver-home` | `pages/driver-home` | motorista | home | parcial: o botão de rota abre a tela de rota com dados fixos |
| `/driver-route` | `pages/driver-route` | motorista | executar a rota | **dados fixos** (TODO no código): refazer (seção 6.2.6) |
| `/driver-link-code` | `pages/driver-link-code` | motorista | mostrar o código de conversa | ok |
| `/driver-schools` | `pages/driver-schools` | motorista | escolas | ok |
| `/driver-contract/:chatId` | `pages/driver-contract` | motorista | montar e enviar a proposta (aluno → contrato → PDF → revisão) | ok; ajustes na seção 3.6 |
| `/chat`, `/chat-details/:id` | `pages/chat`, `components/chat/*` | os dois | lista de conversas, conversa, card da proposta (aceitar/recusar) | ok; falta "retirar proposta" (seção 6.2.3) |
| `/contracts` | `pages/contracts` | os dois | lista de contratos com abas | **quebrado**: a lista é paginada (seção 3.2) |
| `/contract/:contractId` | `pages/contract-view` | os dois | detalhe e PDF | parcial: sem cancelamento, assinaturas, parcelas e responsáveis |
| `/responsible-route` | `pages/responsible-route` | responsável | acompanhar a viagem | **dados fixos** (TODO no código): refazer (seção 6.3.6) |
| `/notifications` | `pages/notifications` | os dois | lista e navegação pelo tipo | parcial: tipos novos (seção 3.5) |
| `/profile-info/:tipo` | `pages/profile-info` | os dois | dados, foto, trocar e-mail/telefone | parcial: sem exportar dados e excluir conta |
| `/profile-select` | `components/profile-select` | os dois | menu do perfil | ok; ganha itens novos (seção 6) |

**Serviços que existem:** `auth`, `chat` (HTTP + WebSocket do chat), `contract`, `notification` (HTTP + WebSocket de notificações), `school`, `storage`, `student`, `student-photo`, `register`.

**Foto do filho:** fica só no `localStorage` (`student-photo.service.ts`). O backend não guarda foto de aluno; deixe assim.

---

## 3. Quebrado ou desatualizado: consertar primeiro

### 3.1 Cadastro sem o aceite dos termos (cadastro dá 400)
O backend exige `acceptedTermsVersion` igual à versão atual dos termos (hoje `"2"`).
1. Na tela de cadastro (os dois perfis), antes de enviar, chame `GET /api/terms` → `{ version, content }`. O `content` é markdown: renderize num modal ou numa área rolável.
2. Checkbox "Li e aceito os Termos de Uso e a Política de Privacidade", obrigatório.
3. Mande a `version` recebida, **nunca fixa no código**:
   - responsável (JSON): `"acceptedTermsVersion": version` no `createUserRequest` (`services/dto/auth/auth.dto.ts`) e no `createUser`;
   - motorista (multipart): `formData.append('acceptedTermsVersion', version)` no `createDriver`.

### 3.2 Lista de contratos paginada (`/contracts` quebrada)
`GET /api/contract/my-contracts` devolve **página**: `{ content: [...], totalElements, totalPages, number, size }`.
- `contract.service.ts` → `getMyContracts(params)`: tipar como `Page<contractSummaryResponse>` e ler `content`.
- Aceita `?status=&studentId=&driverId=&page=&size=` (padrão 20). Use `totalPages` pra "carregar mais".
- Crie um tipo genérico `Page<T>` em `services/dto` (vale pra chats, mensagens, notificações, parcelas, histórico, `my-children` e rotas).

### 3.3 `deleteUser` chama uma rota que não existe
`auth.service.ts` → `deleteUser(id)` faz `DELETE /api/users/{id}`, que não existe mais. Troque por `deleteMyAccount(password)` → `DELETE /api/users/me` com corpo `{ "password": "..." }` (seção 6.4.4).

### 3.4 Status e campos do contrato (`services/dto/contract/contract.dto.ts`)
- `ContractStatus` passa a ser `'PENDING' | 'SCHEDULED' | 'ACTIVE' | 'INACTIVE' | 'CANCELED'`. `SUSPENDED` e `OVERDUE` não existem mais.
- Na tela `/contracts`:
  - tabela `STATUS`: `SCHEDULED: { texto: 'Agendado', icone: 'bi-calendar-check' }`;
  - tire `SUSPENDED` e `OVERDUE`;
  - abas: "Aprovados" = `['SCHEDULED', 'ACTIVE']`; "Bloqueados" = `['CANCELED', 'INACTIVE']`.
- `contractSummaryResponse` ganha:
  - `pendingCancellation: cancellationResponse | null`;
  - `overduePayments: number`.
- `contractDetailsResponse` ganha:
  - `documentStatus: 'PENDING' | 'SIGNED' | 'REJECTED' | null`;
  - `signatures: { userId, name, signedAt, valid }[]`;
  - `cancellations: cancellationResponse[]`;
  - `overduePayments: number`;
  - `studentResponsibles: { responsibleId, name, relationType, admin }[]`;
  - `schoolAddress`.
- `cancellationResponse`:
  ```ts
  {
    id, status: 'PENDING' | 'ACCEPTED' | 'COMPLETED' | 'WITHDRAWN' | 'REJECTED',
    unilateral, reason, requestedById, requestedByName, requestedAt,
    effectiveDate, decidedById, decidedByName, decidedAt
  }
  ```

### 3.5 Notificações (`pages/notifications/notifications.ts`)
- Tire `CANCELLATION_REJECTED` (ninguém recusa cancelamento mais; pode deixar o ícone pra notificação antiga).
- Acrescente ícone e destino dos tipos da seção 7: `CONTRACT_STARTED`, `CONTRACT_EXPIRED`, `CONTRACT_ENDED`, `SYSTEM`, `OCCURRENCE`, `DRIVER_NOTICE` e `ABSENCE_REPORTED`.

### 3.6 Proposta de contrato (`pages/driver-contract`)
- O comentário no topo diz "PDF opcional": o PDF é **obrigatório** (o backend recusa sem ele).
- Erros do backend pra mostrar com a `message`:
  - **400**: não é PDF de verdade, passa de 10 MB, não existe;
  - **409**: já existe proposta pendente, contrato agendado ou contrato no mesmo período pra esse aluno.
- Cada proposta aceita **consome** o PDF: o backend copia pra `contracts/{id}.pdf` e apaga o enviado. Depois de uma proposta criada com sucesso, a próxima precisa de um upload novo; o `draft.limpar()` já faz isso.
- Se a proposta **falhar**, o arquivo continua no lugar e o `fileKey` guardado pelo `draft` pode ser reenviado.
- Datas: `startDate` pode ser hoje ou depois. Se for depois, o contrato aceito fica `SCHEDULED` até o dia (seção 6.2.4).

### 3.7 Erros novos que as telas precisam mostrar

| Onde | Status | O que fazer |
|---|---|---|
| qualquer chamada logada | **403** com `{"code":"TERMS_NOT_ACCEPTED","version":"2"}` | abrir a tela de reaceite dos termos (seção 4.3); não mostrar pop-up genérico |
| login | **429** "Muitas tentativas de login..." | mostrar a mensagem; não tentar de novo sozinho (bloqueio de 15 min) |
| login | **400** "Verifique seu e-mail..." | só aparece com a senha certa: oferecer reenviar o código e ir pro `/email-code` |
| cadastro do motorista | **422** | mostrar a `message`: assinatura inválida, nome diferente do da CNH, CNH vencida, categoria sem D/E, menos de 21 anos |
| cadastro de van | **422** | CRLV inválido ou licenciamento vencido |
| anexo do chat | **400** "O arquivo pode ter no máximo 10 MB" | conferir o tamanho antes de subir |
| chat de outra pessoa | **403** (antes era 400) | voltar pra lista de conversas |

### 3.8 TODO errado no acompanhamento
`pages/responsible-route` tem um TODO dizendo que o backend devia filtrar as paradas: **ele já filtra**. O WebSocket da viagem manda pro pai só as paradas do próprio filho. A tela inteira é refeita na seção 6.3.6.

---

## 4. Regras que valem pro app inteiro

### 4.1 Status de erro

| Status | O que a tela faz |
|---|---|
| 400 | dado inválido ou regra: mostrar o `message` perto do campo |
| 401 | o interceptor renova; se não der, volta pro login (já feito) |
| 403 | sem acesso ("você não tem acesso a isso"); com `code: TERMS_NOT_ACCEPTED`, seção 4.3 |
| 404 | não encontrado (ou lista vazia, quando fizer sentido) |
| 409 | regra de negócio (estado errado, duplicado, "a van está a 5,6 km do ponto"): mostrar o `message` |
| 422 | documento (CNH, CRLV) recusado: mostrar o `message` |
| 429 | muitas tentativas: mostrar o `message` (traz quanto esperar) |
| 503 | serviço de mapas fora ou sem cota: "tente mais tarde" |

O corpo de erro é `{ "timestamp", "message", "details" }`. O `message` já vem em português.

### 4.2 Páginas (listas)
Toda lista grande é paginada: `?page=` começa em 0 e `?size=` vai até 100. A resposta tem `content`, `totalElements`, `totalPages`, `number` e `size`. Isso vale pra:
- chats e mensagens;
- notificações;
- `my-contracts`;
- parcelas;
- histórico de viagens;
- `my-children`;
- `get-all-routes-and-stops`.

### 4.3 Termos de uso (reaceite)
- O `GET /api/users/me` traz `termsVersion` e `termsAccepted`.
- Se `termsAccepted` for `false` (saiu uma versão nova), **todas as outras rotas respondem 403 `TERMS_NOT_ACCEPTED`**. As exceções são: os termos, o próprio `/me`, sair, exportar os dados e excluir a conta.
- **Faça assim:**
  1. No `authInterceptor`, se o erro for 403 com `error.error?.code === 'TERMS_NOT_ACCEPTED'`, navegue pra `/terms` (sem pop-up).
  2. Também confira `termsAccepted` logo depois do login e ao abrir o app.
  3. Tela nova `/terms` (logada, os dois perfis):
     - `GET /api/terms`;
     - mostra o markdown;
     - "Aceito" → `POST /api/terms/accept` `{ "version": <version> }` → **204** → volta pra home;
     - "Não aceito" → explica que não dá pra usar sem aceitar e oferece **sair** ou **excluir a conta**.

### 4.4 Arquivos (upload e download)
```
upload:
1. GET /api/storage/presigned-url?fileName=<nome>&contentType=<mime>  → { uploadUrl, fileKey }
2. fetch(urlDoStorage(uploadUrl), { method: 'PUT', body: arquivo, headers: { 'Content-Type': <mime> } })   (sem token)
3. usar o fileKey (anexo do chat ou document.url da proposta)

download / exibir:
GET /api/storage/download-url?fileKey=<fileKey> → { downloadUrl }  → urlDoStorage(downloadUrl) no <img>/<a>. Vale 15 min
```
- **Limite:** 10 MB (anexo e PDF), conferido quando o arquivo é usado. Confira antes de subir.
- **Dono:** só dá pra usar um arquivo que a própria pessoa enviou.
- **Foto de perfil:** `GET /api/users/photo-upload-url?fileName=&contentType=image/...` → PUT → `PUT /api/users/update-photo` `{ fileKey }` (já feito em `profile-info`).

### 4.5 WebSockets (três conexões)

| Conexão | Abre | Fecha | Recebe | Manda |
|---|---|---|---|---|
| `wsUrl('/notifications')?token=` | ao entrar logado (já feito em `notification.service.ts`) | no logout | o total de não lidas ao conectar e cada notificação nova | nada |
| `wsUrl('/chat/' + chatId)?token=` | ao abrir a conversa (já feito) | ao sair da conversa | cada mensagem nova (inclusive as do sistema) | nada (envio é por HTTP) |
| `wsUrl('/trip/' + tripId)?token=` | motorista executando ou pai acompanhando (**falta**) | viagem `FINISHED`/`CANCELED` ou ao sair da tela | `trip-update` a cada mudança (formato em `docs/FLUXO_TRIP.md`) | o motorista: `{"type":"position","latitude":..,"longitude":..}` a cada 3 a 5 s |

Regras de reconexão:
- **Caiu:** reconecta com espera crescente (1 s, 2 s, 5 s, 10 s), sempre com um token válido (`authService.getValidToken()`). Ao reconectar, o estado atual chega sozinho.
- **Fechou com o código 1003** (sem acesso) ou **1008** (conta excluída): **não reconecta**.

**Não existe polling.** O tempo real vem dos WebSockets. HTTP só ao abrir a tela e nas ações do usuário.

---

## 5. Abrir o app, cadastro e login

### 5.1 Abrir o app (já feito; conferir os termos)
```
/ (guestGuard)
 ├─ sem sessão → escolher perfil → /login/:tipo
 └─ com sessão → GET /api/users/me
       ├─ termsAccepted false → /terms
       ├─ ROLE_DRIVER → /driver-home      ROLE_RESPONSIBLE → /responsible-home
       └─ abre o WebSocket de notificações
```
`/me` → `{ id, name, email, phone, cpf, roles, photoKey, linkCode, termsVersion, termsAccepted }`. Guarde o `id`: é como o chat sabe quais mensagens são suas (`senderId === me.id`).

### 5.2 Cadastro do responsável (consertar: seção 3.1)
1. `GET /api/terms`.
2. `POST /api/responsible/auth/signup` com:
   ```json
   { "name", "email", "password", "confirmPassword", "cpf", "phone",
     "address": { "street", "number", "neighborhood", "city", "state", "zipCode" },
     "acceptedTermsVersion" }
   ```
   A senha precisa de maiúscula, minúscula, número e símbolo.
3. `/email-code`:
   - `POST /api/auth/verify-email` `{ email, code }`;
   - reenviar: `POST /api/auth/send-verification-code?email=`. Dá **429** antes de 1 minuto (o cadastro conta como o primeiro envio).
   - O código errado responde **400** com "restam N tentativas". Na 5ª errada, **429** e o backend manda um código novo.
4. Login.

### 5.3 Cadastro do motorista (consertar: seções 3.1 e 3.7)
1. O motorista escolhe o PDF da **CNH digital**. Validação antecipada (opcional, já existe): `POST /api/driver/auth/verify-CNH` (multipart `documentPdf`); **422** = não é CNH digital assinada.
2. Avise na tela: **"use o nome completo exatamente como está na CNH"**. O backend compara, sem acentos e sem diferenciar maiúsculas, e exige CNH válida, categoria **D ou E** e **21 anos ou mais**.
3. `GET /api/terms`, depois `POST /api/driver/auth/signup` (multipart): `documentPdf`, `name`, `email`, `password`, `confirmPassword`, `phone`, `acceptedTermsVersion`. O CPF sai da CNH.
   - **422**: mostrar a `message`.
   - A leitura da CNH pode levar alguns segundos: mostre "lendo a CNH...".
4. `/email-code` e login, igual ao responsável.

### 5.4 Login, esqueci a senha e sair (já feitos; conferir os erros)
- `POST /api/auth/login` `{ cpf, passwordHash }`. O campo se chama `passwordHash`, mas vai a senha digitada.
  - Resposta: `{ token, expiresIn, refreshToken, refreshExpiresIn }`.
  - Erros: **401** credenciais inválidas; **400** "Verifique seu e-mail" (oferecer reenviar o código); **429** bloqueado por 15 min.
- Esqueci a senha:
  1. `POST /api/auth/forgot-password/send-code?email=`;
  2. `POST /api/auth/forgot-password` com o código e a senha nova.
- Sair: `POST /api/auth/logout` `{ refreshToken }`, limpa a sessão e fecha os WebSockets.

---

## 6. Telas por perfil (o que existe e o que falta, com a ordem das chamadas)

Legenda: ✅ existe · 🟡 existe mas falta parte · ❌ falta.

### 6.1 Menu do perfil (`/profile-select`)
Itens novos a acrescentar:

| Item | Perfil | Tela |
|---|---|---|
| Minhas vans | motorista | 6.2.1 |
| Chave Pix | motorista | 6.2.1 |
| Minhas rotas | motorista | 6.2.5 |
| Grupos | motorista | 6.2.8 |
| Parcelas | os dois | 6.2.7 / 6.3.7 |
| Histórico de viagens | os dois | 6.2.9 / 6.3.8 |
| Privacidade e dados (termos, exportar, excluir conta) | os dois | 6.4 |

### 6.2 Motorista

#### 6.2.1 Preparação (escolas ✅, vans ❌, Pix ❌, código ✅)

| Tela | Ao abrir | Ações |
|---|---|---|
| Escolas `/driver-schools` ✅ | `GET /api/school/my-schools` | adicionar: `POST /api/school/add-school` `{ name, phone, email, addressRequestDTO: {...} }`. Se a escola já existe, só vincula |
| **Minhas vans** `/driver-vehicles` ❌ | `GET /api/vehicle/my-vehicles` → `[{ id, plate, model, year, color, capacity, licensingYear, status, linkedSince, ... }]` | adicionar pelo **CRLV digital**: `POST /api/vehicle/add-vehicle` (multipart `documentPdf`) → 201 com a van. **422**: CRLV inválido ou licenciamento vencido. Parar de usar: `PUT /api/vehicle/{id}/unlink` (**204**) |
| **Chave Pix** `/driver-pix` ❌ | `GET /api/driver/pix-key` → `{ type, key }` (os dois `null` sem chave) | salvar: `PUT /api/driver/pix-key` `{ type: 'CPF' \| 'CNPJ' \| 'EMAIL' \| 'PHONE' \| 'RANDOM', key }`; mostre a `key` que voltar (já normalizada). **400**: a chave não bate com o tipo. Remover: `DELETE /api/driver/pix-key` |
| Meu código `/driver-link-code` ✅ | `GET /api/driver/link-code` (ou `/me.linkCode`) | copiar e compartilhar |

#### 6.2.2 Conversas ✅
- Lista: `GET /api/chats?page=&size=` → `otherUserId`, `otherUserName`, `otherUserPhotoKey`.
- Conversa:
  - ao abrir: `GET /api/chats/{chatId}/messages?page=&size=` e abre o WebSocket do chat;
  - enviar: `POST /api/chats/{chatId}/messages` `{ content?, attachmentType?: 'IMAGE' | 'CONTRACT_DOCUMENT' | 'OTHER', attachmentUrl?: fileKey }`.
- Mensagens do sistema (`messageType` ≠ `REGULAR_CHAT`) viram card:
  - `PROPOSAL`: card da proposta (`JSON.parse(payload).contractId`);
  - `APPROVAL` / `REJECTION`: resposta (também usado pra proposta retirada ou expirada);
  - `ABSENCE`: falta avisada pelo pai.
- **Novo: botão "Ver perfil"** no topo da conversa → 6.2.10.

#### 6.2.3 Proposta de contrato (✅ enviar; ❌ retirar)
Envio, já feito em `/driver-contract/:chatId`:
1. aluno: `GET /api/chats/{chatId}/students` → `[{ id, name }]`;
2. escola: `GET /api/school/my-schools`;
3. PDF: upload (seção 4.4);
4. `POST /api/chats/{chatId}/contract` `{ responsibleId, studentId, schoolId, contract: { periodicity, value, startDate, endDate }, document: { url: fileKey, type: 'TRANSPORT_CONTRACT' } }` → **201** `{ message, userDriverContractId, contract }`.

**❌ Retirar a proposta:** no card `PROPOSAL` do motorista, enquanto o contrato estiver `PENDING`, botão "Retirar proposta" → `PUT /api/chats/{chatId}/contracts/{contractId}/withdraw` (**200**). O chat recebe um `REJECTION` "Proposta Retirada" e o card fecha.

#### 6.2.4 Contratos (✅ lista e detalhe; ❌ cancelamento, assinaturas, parcelas)
- Lista `/contracts`: corrigir a paginação (3.2) e os status (3.4).
  - Cada item mostra `overduePayments` (selo vermelho se > 0).
  - Se tiver `pendingCancellation`: "Cancela em DD/MM" (`effectiveDate`).
- Detalhe `/contract/:contractId`: `GET /api/contract/{contractId}`. Mostrar:
  - status: `SCHEDULED` = "Agendado — começa em DD/MM";
  - PDF: `GET /api/storage/download-url?fileKey=<documentFileKey>` (a chave agora é `contracts/{id}.pdf`);
  - assinaturas ("assinado por Carlos em 20/01 e por Maria em 21/01"; `valid: false` = "termos alterados depois da assinatura");
  - `studentResponsibles` (todos os responsáveis do aluno, sem telefone);
  - histórico de `cancellations`;
  - botão **"Parcelas"** → lista de parcelas filtrada por `contractId` (6.2.7).
- **❌ Cancelar** (contrato `SCHEDULED` ou `ACTIVE`):
  1. botão "Cancelar contrato" → modal com o motivo (obrigatório, até 500 caracteres) e o aviso "o contrato será cancelado em 30 dias, ou antes se a outra parte aceitar; parcelas que vencerem até lá continuam devidas";
  2. `POST /api/contract/{contractId}/cancellation` `{ reason }` → **201** com o pedido;
  3. se voltar `status: ACCEPTED` e `unilateral: true`, o contrato **já foi cancelado** (havia parcela vencida há 30 dias ou mais). Senão, mostre "Cancela em `effectiveDate`".
- **❌ Pedido em andamento** (`pendingCancellation` no detalhe e na lista):
  - pedido da **outra parte**: "Aceitar e cancelar agora" → `PUT /api/contract/{contractId}/cancellation/accept`;
  - pedido **meu**: "Desistir" → `DELETE /api/contract/{contractId}/cancellation`;
  - **não existe "Recusar"**.
- Erros: **409** já existe pedido em andamento, contrato não está aceito, ou a van está rodando com o aluno agora ("tente depois que a viagem terminar").
- **Renovação:** com um contrato `ACTIVE`, o motorista pode propor **um** próximo contrato pro mesmo aluno, começando **depois** do fim do atual. Ofereça "Renovar" no detalhe de um contrato `ACTIVE` sem renovação: abre a proposta com o aluno preenchido e a data de início = fim + 1 dia. Se sobrepor, ou já existir outro pendente ou agendado: **409**.

#### 6.2.5 Rotas ❌ (montar a rota)
Detalhes, JSON e regras em `docs/FLUXO_ROTA.md`.
```
Minhas rotas /driver-routes
 └─ GET /api/route/get-all-routes-and-stops?active=true&name=&page=   (página; active=false inclui as que ele deixou)
     ├─ Nova rota → POST /api/route/create-route { name }   ("Ida manhã"; a volta é outra rota)
     └─ abrir uma → Editor /driver-routes/:routeId
          ├─ GET /api/route/{routeId}/stops      (pontos em ordem, com alunos e escolas)
          ├─ GET /api/route/{routeId}/pending    (pendências; [] = rota pronta)
          ├─ + aluno:
          │    1. GET /api/route/stop-options → { students: [{ studentId, studentName, addresses: [{ id, label, address }] }] }
          │       (só alunos com contrato ACTIVE; a escola não aparece: é sempre a do contrato)
          │    2. escolhe ação (embarque/desembarque), local (HOME/OTHER com um endereço, ou SCHOOL),
          │       dias da semana e o ponto: um que já existe naquele endereço (routeStopId) ou "novo ponto" (newStop: true, orderIndex opcional)
          │    3. POST /api/route/{routeId}/add-student/{studentId}
          │       { action, locationType, newStop? | routeStopId?, studentAddressId?, weekdays?, orderIndex? }
          │       → { stops, pending }   (redesenha com a resposta; não precisa de outro GET)
          ├─ trocar ordem ... PUT /api/route/stop/{routeStopId}/swap-order { orderIndex }
          ├─ mover aluno .... PUT /api/route/student-stop/{studentStopId}/move { routeStopId }
          ├─ dias ........... PUT /api/route/student-stop/{studentStopId}/weekdays { weekdays: ['MONDAY', ...] }
          ├─ remover ........ DELETE /api/route/student-stop/{id} | /school-stop/{id} | /stop/{id}  → { stops, pending }
          └─ van padrão ..... PUT /api/route/{routeId}/set-vehicle/{vehicleId}   (vans de GET /api/vehicle/my-vehicles)
```
- Mostre `pending` como aviso ("Ana: falta desembarque na sexta").
- A viagem só inicia sem pendência; o **400** traz quem e o que falta.
- Cada aluno precisa de embarque **e** desembarque em cada dia; o embarque vem antes na ordem.
- **403** no `add-student`: sem contrato `ACTIVE` com o aluno (contrato `SCHEDULED` ainda não serve), ou o motorista não está mais na rota.

#### 6.2.6 Executar a viagem ❌ (refazer `/driver-route`)
Detalhes e o formato do `trip-update` em `docs/FLUXO_TRIP.md`.
```
/driver-route/:routeId (abre)
 └─ GET /api/trip/route/{routeId}/today
     ├─ 404 ........... botão "Iniciar rota"
     ├─ 200 ativa ..... "Continuar" (só abre o WebSocket) | "Recomeçar do zero"
     └─ 200 FINISHED .. "Rota de hoje finalizada"

Iniciar
 ├─ pede a localização (navigator.geolocation; HTTPS) e liga a Wake Lock
 ├─ POST /api/trip/start/{routeId}  { latitude, longitude, vehicleId? }   (sem vehicleId = van padrão)
 │     recomeçar: POST /api/trip/start/{routeId}?restart=true  (id novo: troque o WebSocket)
 ├─ abre o WebSocket da viagem
 └─ watchPosition: a cada 3 a 5 s manda {"type":"position","latitude","longitude"} pelo WebSocket

Durante (o que aparece vem do trip-update)
 ├─ mapa: van, linha (path [lng,lat] → inverter), pontos
 ├─ "Próximo ponto em X min" (targetStopId + etaSeconds)
 ├─ alunos de cada ponto com o status
 ├─ embarcou ... PUT /api/trip/{tripId}/student/{studentId}/board
 ├─ desceu ..... PUT /api/trip/{tripId}/student/{studentId}/alight
 ├─ ausente .... PUT /api/trip/{tripId}/student/{studentId}/absent
 │     (embarcou e desceu só com a van a até 1 km do ponto: o 409 traz a distância)
 ├─ ocorrência . POST /api/trip/{tripId}/occurrences { type, description }
 │     type: VEHICLE | STUDENT | TRAFFIC | DISCIPLINARY | FINANCIAL | MEDICAL | OTHER  (os pais de todos os alunos são avisados)
 ├─ navegar .... abre Google Maps ou Waze com os pontos em ordem
 └─ todos atendidos (targetStopId === null): "Finalizar rota" em destaque

Finalizar → POST /api/trip/{tripId}/finish → fecha o WebSocket, para o GPS e solta a Wake Lock
```
- A home do motorista (`/driver-home`) precisa escolher **qual rota** executar: liste `GET /api/route/get-all-routes-and-stops?active=true` e abra `/driver-route/:routeId`.
- Só entram na viagem alunos com contrato **ACTIVE** com o motorista: aluno de contrato agendado, encerrado ou cancelado não aparece, mesmo que ainda esteja na rota.
- Viagem esquecida aberta: o sistema fecha sozinho depois de 20 horas e manda a notificação `SYSTEM` "Viagem fechada automaticamente".
- Os botões não mudam a tela por conta própria: espere o `trip-update` (chega em menos de 1 s) ou use a resposta do PUT.

#### 6.2.7 Parcelas ❌ `/payments`
Detalhes em `docs/FLUXO_PAGAMENTO.md`.
- Ao abrir: `GET /api/payments?status=&studentId=&responsibleId=&contractId=&from=&to=&page=&size=`. Cada item vem como:
  ```json
  { "id", "contractId", "studentName", "responsibleName", "value", "dueDate",
    "status", "paymentMethod", "paidAt", "driverPixKeyType", "driverPixKey" }
  ```
- Status:
  - `PENDING` em dia; `OVERDUE` atrasada (em vermelho);
  - `PAID` paga; `CANCELED` dispensada.
  - O filtro `status=OVERDUE` já traz as vencidas que o worker ainda não marcou.
- Nas `PENDING` e `OVERDUE`, dois botões, sempre com confirmação (**não têm volta**):
  - **"Recebi"** → pergunta o método (PIX, dinheiro, boleto, cartão) → `PUT /api/payments/{id}/paid` `{ method: 'PIX' | 'CASH' | 'BOLETO' | 'CREDIT_CARD' }`;
  - **"Dispensar"** → `PUT /api/payments/{id}/cancel`.
- **409**: já paga ou dispensada (ex.: dois cliques).
- Parcela vencida de contrato cancelado continua aparecendo e pode receber baixa.

#### 6.2.8 Grupos ❌ `/driver-groups`
- `GET /api/driver/groups` → `[{ id, name, students: [{ studentId, studentName }] }]`.
- Criar: `POST /api/driver/groups` `{ name }`. Renomear: `PUT /api/driver/groups/{id}` `{ name }`. Apagar: `DELETE /api/driver/groups/{id}`.
- Pôr aluno: escolha entre os alunos de `GET /api/contract/my-contracts?status=ACTIVE` → `POST /api/driver/groups/{id}/students/{studentId}`. Tirar: `DELETE .../students/{studentId}`.
  - **403**: sem contrato `ACTIVE` com o aluno.
- **"Avisar o grupo"** ("não vou buscar hoje"): `POST /api/driver/groups/{id}/notice` `{ reason? }` (até 300 caracteres) → `{ notifiedResponsibles }`: "X responsáveis avisados".

#### 6.2.9 Histórico de viagens ❌ `/trip-history`
- `GET /api/trip/history?studentId=&routeId=&date=` (ou `from`/`to`) `&page=` → página com:
  ```json
  { "tripId", "date", "tripStatus", "startTime", "endTime", "routeName", "studentName",
    "boardingStatus", "boardingTime", "unboardingTime", "occurrences" }
  ```
- `endTime` `null` com `FINISHED` = fechada pelo sistema.
- Ocorrências de uma viagem: `GET /api/trip/{tripId}/occurrences` → `[{ id, type, description, dateTime, driverName }]`.

#### 6.2.10 Perfil do responsável ❌ `/responsible-profile/:responsibleId`
- Abre do chat ou do detalhe do contrato: `GET /api/responsible/{responsibleId}/profile`. Resposta:
  ```json
  { "id", "name", "photoKey", "phone", "financialStatus", "overduePayments",
    "students": [{ "studentId", "studentName", "contractId", "contractStatus", "relationType", "admin" }] }
  ```
- `financialStatus` (`UP_TO_DATE`/`OVERDUE`) e `overduePayments` são **só das parcelas com esse motorista**.
- **403**: o motorista não conversa nem tem contrato com esse responsável.

### 6.3 Responsável

#### 6.3.1 Filhos (✅ cadastrar e listar; ❌ detalhe do filho)
- Lista: `GET /api/student/my-children?page=` (página).
- Cadastrar ✅: `POST /api/student/create-student` `{ name, birthDate, relationType: 'FINANCIAL' | 'LEGAL' | 'WITHDRAWAL', notes?, address | addressId, addressLabel? }`. Quem cadastra vira o **admin** do filho.
- **❌ Detalhe do filho** `/student/:studentId`. Ao abrir, em paralelo:
  - `GET /api/student/{id}/addresses`;
  - `GET /api/student/{id}/responsibles`;
  - `GET /api/student/{id}/routes` → `[{ id, name }]`;
  - `GET /api/student/{id}/absences`.

  Seções da tela: endereços (6.3.2), responsáveis (6.3.3), rotas, faltas (6.3.5) e "Histórico de viagens" (6.3.8).

#### 6.3.2 Endereços do filho ❌ (dentro do detalhe do filho)
- Listar: `GET /api/student/{id}/addresses` → `[{ id, label, address }]`.
- Adicionar (só o **admin**):
  - endereço novo: `POST /api/student/{id}/addresses` `{ label, address }`;
  - reaproveitar um que a família já tem: `{ label, addressId }`.

  **409**: o filho já tem esse endereço. Mesmo CEP e número = mesmo endereço (o backend reaproveita sozinho).
- Tirar (só o admin): `DELETE /api/student/{id}/addresses/{studentAddressId}`. O motorista é avisado.
- **Dica:** antes de digitar, mostre os endereços da família (o do responsável e os dos outros filhos) pra escolher.

#### 6.3.3 Responsáveis do filho ❌ (dentro do detalhe do filho)
- Listar: `GET /api/student/{id}/responsibles` → `[{ responsibleId, name, relationType, admin }]`.
- **Convidar** ✅ (já existe em `add-student` / `student-code`):
  1. o admin gera o código: `POST /api/student/generate-link` `{ id: studentId, relationType }` (vale 15 min);
  2. o outro responsável usa: `POST /api/student/confirm-link` `{ code }`.
- **❌ Remover** (só o admin; não pode remover a si mesmo): `DELETE /api/student/{id}/responsibles/{responsibleId}` (**204**). A pessoa removida perde **na hora** o acesso às rotas, viagens, localização e histórico do filho.
- **❌ Sair** (qualquer um, menos o admin): o mesmo `DELETE` com o próprio id. Depois volta pra lista de filhos.
- **❌ Passar o admin** (só o admin): `PUT /api/student/{id}/responsibles/{responsibleId}/admin` (**204**).
- Erros:
  - **403**: quem não é admin removendo outro ou passando o admin;
  - **409**: o admin tentando sair sem passar o admin, ou remover alguém com contrato ou proposta em andamento com o filho (cancele antes).
- Passar o admin não muda quem paga: as parcelas de um contrato continuam com quem assinou. As próximas propostas do motorista vão pro admin novo.

#### 6.3.4 Motorista: código, conversa, proposta, perfil e avaliação
- Código do motorista ✅ (`/driver-code`): `POST /api/invite-code/redeem` `{ inviteCode }` → `chatId` (texto) → abre a conversa.
- Proposta ✅ (card no chat): ver com `GET /api/contract/{contractId}`; aceitar e recusar já existem.
  - Aceitar: `PUT /api/chats/{chatId}/contracts/{contractId}/accept`. Se a data de início ainda não chegou, o contrato fica **`SCHEDULED`**; mostre "Agendado: começa em DD/MM".
  - Recusar: `PUT /api/chats/{chatId}/contracts/{contractId}/reject`.
  - **409** ao aceitar: a data de início já passou (peça outra proposta) ou já foi decidido.
- **❌ Perfil do motorista** `/driver-profile/:driverId` (do chat ou do contrato): `GET /api/driver/{driverId}/profile` → `{ driverId, name, photoKey, memberSince, schools, vehicles, ratingAverage, ratingCount, myRating, recentRatings }`.
  - **403**: sem conversa nem contrato com ele.
- **❌ Avaliar** (botão no perfil, só pra quem tem ou teve contrato `ACTIVE`/`INACTIVE` com ele): `PUT /api/driver/{driverId}/rating` `{ score: 1..5, comment? }`.
  - O `myRating` do perfil diz se já avaliou (aí o botão vira "editar").
  - Apagar: `DELETE /api/driver/{driverId}/rating`.

#### 6.3.5 Home e faltas (🟡 home; ❌ viagens do dia e faltas)
```
/responsible-home (cards por filho)
 ├─ GET /api/student/my-children
 ├─ por filho: GET /api/student/{id}/today-trips → [{ tripId, routeId, routeName, tripStatus, boardingStatus }]
 │     []  → "A van ainda não saiu"
 │     [..] → card com o status do filho e o botão "Acompanhar" (6.3.6)
 ├─ por filho: GET /api/student/{id}/absences → faltas de hoje em diante [{ id, routeId, routeName, date }]
 └─ notificação TRIP_STARTED pelo WebSocket (payload.tripId) → atualiza o card (sem polling)
```
Chame `today-trips` **ao abrir a home**, ao voltar pro app e quando chegar uma notificação de viagem. Não fique repetindo.

**Faltas:**

| Botão | Quando mostrar | Chamada |
|---|---|---|
| "Não vai hoje" / "Não vai dia X" (escolhe a rota em `GET /api/student/{id}/routes`) | antes da viagem, ou durante ela **enquanto o filho não embarcou** | `POST /api/student/{id}/absences` `{ routeId, date }` |
| "Ele vai sim" (desfazer) | **só antes de a viagem do dia começar** | `DELETE /api/student/{id}/absences/{absenceId}` |

**400**: data que já passou, ou o filho não está nessa rota. **409**: a falta já existe, o filho já embarcou, ou a viagem já começou (pra desfazer).

#### 6.3.6 Acompanhar a viagem ❌ (refazer `/responsible-route`)
- Rota: `/responsible-route/:tripId`.
- Abre só o WebSocket da viagem (`wsUrl('/trip/' + tripId)`); o estado chega ao conectar. Não precisa de GET.
- Cada `trip-update` traz um `mode`, e ele decide a tela inteira:

| `mode` | Desenhar |
|---|---|
| `FOLLOWING` | mapa com a van, a **linha até o destino do filho** (`path`), o pino do destino e "X MIN" (`etaSeconds`) |
| `ON_BOARD` | "Seu filho está na van": só a van no mapa |
| `DONE` | "Ana foi entregue às 07:32" (`students[].deliveredAt`); pode fechar o WebSocket |
| `STATUS` | sem mapa, só o status ("aguardando", "embarcou", "ausente") |

- `status: CANCELED` = o motorista recomeçou: chame `today-trips` de novo e abra a viagem nova. `status: FINISHED`: feche o WebSocket.
- O pai vê **só as paradas do próprio filho**; o backend já filtra.
- Fechou com o código 1003: o responsável não tem mais acesso (foi removido do filho). Volte pra home.

#### 6.3.7 Parcelas ❌ `/payments` (mesma tela do motorista, outro modo)
- `GET /api/payments?studentId=&driverId=&status=&page=`.
- Nas `PENDING` e `OVERDUE`, mostre a **chave Pix do motorista** (`driverPixKey`, com o tipo) e o botão **"Copiar chave Pix"** (`navigator.clipboard.writeText`).
  - Se `driverPixKey` vier `null`: "o motorista ainda não cadastrou a chave Pix".
- O pai **não dá baixa**: quem confirma é o motorista. Pra avisar que pagou, botão "Avisar no chat" → abre a conversa com o motorista.
- Só o responsável que **assinou** o contrato vê as parcelas dele.
- `OVERDUE` em vermelho.

#### 6.3.8 Histórico de viagens e ocorrências ❌ `/trip-history`
- O mesmo `GET /api/trip/history?studentId=&date=` (ou `from`/`to`) da seção 6.2.9: embarcou às, chegou às, faltou, ocorrências.
- Todos os responsáveis do filho veem.
- Ocorrências: `GET /api/trip/{tripId}/occurrences`. A notificação `OCCURRENCE` abre direto essa lista.

#### 6.3.9 Perfil do responsável (dele mesmo) ❌
`GET /api/responsible/{meuId}/profile`: situação financeira geral, todas as parcelas atrasadas e todos os contratos dos filhos. Pode ser uma seção do `/profile-info/:tipo`.

### 6.4 Os dois perfis: conta e privacidade

| # | Tela/ação | Situação | Chamadas |
|---|---|---|---|
| 6.4.1 | Dados e foto (`/profile-info`) | ✅ | `GET /api/users/me`; foto (4.4); e-mail/telefone: `POST /api/users/send-code-updateUser` → `PUT /api/users/update-user` `{ email, phone, code }` |
| 6.4.2 | Termos de uso | ❌ | `/terms` (4.3): ler `GET /api/terms` a qualquer hora, inclusive no menu |
| 6.4.3 | **Exportar meus dados** | ❌ | `GET /api/users/me/export` → JSON pra baixar (crie um `Blob` e baixe como `meus-dados.json`). **429**: de novo antes de 5 minutos |
| 6.4.4 | **Excluir minha conta** | ❌ | confirmação dupla com a senha → `DELETE /api/users/me` `{ "password": "..." }` → **204**: limpe a sessão, feche os WebSockets e vá pra `/`. **400** senha errada; **409** com contrato aceito (agendado ou ativo), proposta pendente ou parcela atrasada: mostre a `message` e um link pra lista de contratos/parcelas. Explique antes: os dados pessoais e os arquivos enviados somem; contratos, parcelas e mensagens ficam no histórico das outras partes sem o nome |

### 6.5 Notificações (`/notifications`, 🟡)
- Lista: `GET /api/notifications?unread=false&page=0&size=20` → `{ id, type, title, message, payload, createdAt, read }`.
- Tocar numa: `PUT /api/notifications/{id}/read` e navega pelo `type` (seção 7).
- Marcar todas: `PUT /api/notifications/read-all`.
- Badge: o total chega ao conectar no WebSocket de notificações (já feito).

---

## 7. Notificações: tipo → quem recebe → pra onde leva

| `type` | Quem recebe | `payload` | Abre |
|---|---|---|---|
| `CONTRACT_PROPOSED` | responsável | `chatId`, `contractId` | contrato ou chat |
| `CONTRACT_ACCEPTED` / `CONTRACT_REJECTED` | motorista | `chatId`, `contractId` | contrato |
| `CONTRACT_WITHDRAWN` | responsável | `chatId`, `contractId` | contrato |
| `CANCELLATION_REQUESTED` | a outra parte | `contractId` | contrato (com "Aceitar e cancelar agora") |
| `CANCELLATION_WITHDRAWN` | a outra parte | `contractId` | contrato |
| `CONTRACT_CANCELED` | a outra parte (no fim do aviso, os dois) | `contractId` | contrato |
| `CONTRACT_STARTED` | os dois | `contractId` | contrato (o motorista já pode pôr o aluno na rota) |
| `CONTRACT_EXPIRED` | os dois | `contractId` | contrato |
| `CONTRACT_ENDED` | os dois | `contractId` | contrato |
| `PAYMENT` | responsável | `paymentId`, `contractId` | parcelas filtradas pelo contrato |
| `TRIP_STARTED` / `VAN_ON_THE_WAY` | responsável | `tripId` (…) | acompanhar (`/responsible-route/:tripId`) |
| `STUDENT_BOARDED` / `STUDENT_DELIVERED` / `STUDENT_ABSENT` | responsável | `tripId`, `studentIds` | acompanhar ou home |
| `OCCURRENCE` | responsável | `tripId`, `occurrenceId`, `type` | ocorrências da viagem |
| `DRIVER_NOTICE` | responsável | `groupId`, `driverId`, `date` | só mostra o texto |
| `ABSENCE_REPORTED` | motorista | `chatId`, `studentId`, `routeId`, `date` | chat com o pai |
| `SYSTEM` | motorista ("Viagem fechada automaticamente", `tripId`) ou responsável ("Vínculo removido", `studentId`) | — | histórico de viagens / lista de filhos |
| `CANCELLATION_REJECTED` | — | — | só em notificação antiga; não é mais enviada |

As notificações gravadas pelo worker diário (`CONTRACT_STARTED`, `CONTRACT_EXPIRED`, `CONTRACT_ENDED`, `CONTRACT_CANCELED` do fim do aviso, `PAYMENT` "Parcela atrasada" e `SYSTEM` da viagem) **não chegam pelo WebSocket**: aparecem no próximo `GET /api/notifications` ou ao reabrir o app.

---

## 8. Referência de rotas da API (todas que o front usa)

Base `/api`. "M" = motorista, "R" = responsável, "—" = sem login. Detalhes nos docs da tabela do topo.

### Conta e acesso
| Método e rota | Quem | Corpo / parâmetros | Resposta |
|---|---|---|---|
| `GET /terms` | — | — | `{ version, content }` |
| `POST /terms/accept` | M, R | `{ version }` | 204 |
| `POST /responsible/auth/signup` | — | JSON (5.2) | 200 com o responsável |
| `POST /driver/auth/verify-CNH` | — | multipart `documentPdf` | 200 / 422 |
| `POST /driver/auth/signup` | — | multipart (5.3) | 201 |
| `POST /auth/verify-email` | — | `{ email, code }` | 200 |
| `POST /auth/send-verification-code?email=` | — | — | 200 / 429 |
| `POST /auth/login` | — | `{ cpf, passwordHash }` | `{ token, expiresIn, refreshToken, refreshExpiresIn }` |
| `POST /auth/refresh` | — | `{ refreshToken }` | par novo |
| `POST /auth/logout` | — | `{ refreshToken }` | 204 |
| `POST /auth/forgot-password/send-code?email=` · `POST /auth/forgot-password` | — | código e senha nova | 200 |
| `GET /users/me` | M, R | — | `{ id, name, email, phone, cpf, roles, photoKey, linkCode, termsVersion, termsAccepted }` |
| `POST /users/send-code-updateUser` · `PUT /users/update-user` | M, R | `{ email, phone, code }` | usuário |
| `GET /users/photo-upload-url?fileName=&contentType=` · `PUT /users/update-photo` | M, R | `{ fileKey }` | `{ photoKey }` |
| `GET /users/me/export` | M, R | — | JSON (429 em 5 min) |
| `DELETE /users/me` | M, R | `{ password }` | 204 / 400 / 409 |

### Arquivos
| `GET /storage/presigned-url?fileName=&contentType=` | M, R | — | `{ uploadUrl, fileKey }` |
|---|---|---|---|
| `GET /storage/download-url?fileKey=` | M, R | — | `{ downloadUrl, fileKey }` / 403 |

### Motorista: preparação
| `GET /school/my-schools` · `POST /school/add-school` | M | `{ name, phone, email, addressRequestDTO }` | escolas |
|---|---|---|---|
| `GET /vehicle/my-vehicles` · `POST /vehicle/add-vehicle` · `PUT /vehicle/{id}/unlink` | M | multipart `documentPdf` | vans / 422 |
| `GET` · `PUT` · `DELETE /driver/pix-key` | M | `{ type, key }` | `{ type, key }` |
| `GET /driver/link-code` | M | — | `{ linkCode }` |

### Chat e contrato
| `POST /invite-code/redeem` | R | `{ inviteCode }` | `chatId` |
|---|---|---|---|
| `GET /chats?page=&size=` | M, R | — | página de conversas |
| `GET /chats/{chatId}/messages?page=&size=` · `POST /chats/{chatId}/messages` | M, R | `{ content?, attachmentType?, attachmentUrl? }` | mensagem |
| `GET /chats/{chatId}/students` | M | — | `[{ id, name }]` |
| `POST /chats/{chatId}/contract` | M | proposta (6.2.3) | `{ message, userDriverContractId, contract }` |
| `PUT /chats/{chatId}/contracts/{contractId}/accept` · `/reject` | R | — | 200 |
| `PUT /chats/{chatId}/contracts/{contractId}/withdraw` | M | — | 200 |
| `GET /contract/my-contracts?status=&studentId=&driverId=&page=&size=` | M, R | — | página |
| `GET /contract/{contractId}` | partes | — | detalhe |
| `POST /contract/{contractId}/cancellation` | partes | `{ reason }` | pedido (201) |
| `PUT /contract/{contractId}/cancellation/accept` | a outra parte | — | pedido |
| `DELETE /contract/{contractId}/cancellation` | quem pediu | — | pedido |
| `GET /responsible/{responsibleId}/profile` | M (ou o próprio R) | — | perfil (6.2.10) |
| `GET /driver/{driverId}/profile` | R (ou o próprio M) | — | perfil (6.3.4) |
| `PUT` · `DELETE /driver/{driverId}/rating` | R | `{ score, comment? }` | avaliação |

### Parcelas
| `GET /payments?studentId=&driverId=&responsibleId=&contractId=&status=&from=&to=&page=&size=` | M, R | — | página |
|---|---|---|---|
| `PUT /payments/{id}/paid` | M | `{ method }` | parcela |
| `PUT /payments/{id}/cancel` | M | — | parcela |

### Filhos e responsáveis
| `GET /student/my-children?page=` · `POST /student/create-student` | R | (6.3.1) | página / filho |
|---|---|---|---|
| `GET` · `POST /student/{id}/addresses` · `DELETE /student/{id}/addresses/{studentAddressId}` | R (alterar: admin) | `{ label, address \| addressId }` | endereços |
| `GET /student/{id}/responsibles` | R | — | `[{ responsibleId, name, relationType, admin }]` |
| `DELETE /student/{id}/responsibles/{responsibleId}` | R (admin, ou a própria pessoa) | — | 204 |
| `PUT /student/{id}/responsibles/{responsibleId}/admin` | R (admin) | — | 204 |
| `POST /student/generate-link` · `POST /student/confirm-link` | R | `{ id, relationType }` / `{ code }` | código / 201 |
| `GET /student/{id}/routes` | R | — | `[{ id, name }]` |
| `GET` · `POST /student/{id}/absences` · `DELETE /student/{id}/absences/{absenceId}` | R | `{ routeId, date }` | faltas |
| `GET /student/{id}/today-trips` | R | — | viagens de hoje do filho |

### Rotas (motorista)
| `GET /route/get-all-routes-and-stops?name=&active=&page=` · `POST /route/create-route` | M | `{ name }` | página / rota |
|---|---|---|---|
| `GET /route/{id}` · `GET /route/{routeId}/stops` · `GET /route/{routeId}/pending` · `GET /route/stop-options` | M | — | rota, pontos, pendências, opções |
| `POST /route/{routeId}/add-student/{studentId}` | M | (6.2.5) | `{ stops, pending }` |
| `PUT /route/stop/{id}/swap-order` · `PUT /route/student-stop/{id}/move` · `PUT /route/student-stop/{id}/weekdays` | M | `{ orderIndex }` / `{ routeStopId }` / `{ weekdays }` | `{ stops, pending }` |
| `DELETE /route/student-stop/{id}` · `/route/school-stop/{id}` · `/route/stop/{id}` | M | — | `{ stops, pending }` |
| `PUT /route/{routeId}/set-vehicle/{vehicleId}` | M | — | rota |
| `GET` · `POST` · `PUT` · `DELETE /driver/groups...` · `POST /driver/groups/{id}/notice` | M | (6.2.8) | grupos / `{ notifiedResponsibles }` |

### Viagem
| `GET /trip/route/{routeId}/today` · `POST /trip/start/{routeId}?restart=` | M | `{ latitude, longitude, vehicleId? }` | viagem |
|---|---|---|---|
| `GET /trip/{tripId}` | M | — | viagem |
| `PUT /trip/{tripId}/student/{studentId}/board` · `/alight` · `/absent` | M | — | aluno |
| `POST /trip/{tripId}/finish` | M | — | viagem |
| `POST /trip/{tripId}/occurrences` · `GET /trip/{tripId}/occurrences` | M (registrar), M e R (ver) | `{ type, description }` | ocorrência(s) |
| `GET /trip/history?studentId=&driverId=&routeId=&date=&from=&to=&page=` | M, R | — | página |

### Notificações
| `GET /notifications?unread=&page=&size=` · `GET /notifications/unread-count` · `PUT /notifications/{id}/read` · `PUT /notifications/read-all` | M, R | — | página / total |
|---|---|---|---|

---

## 9. Ordem sugerida de construção

1. **Consertos (seção 3):** termos no cadastro e `/terms` com o 403; `my-contracts` paginado e status `SCHEDULED`; `deleteUser`; notificações novas; mensagens dos erros novos.
2. **Contrato completo:** detalhe com assinaturas, responsáveis e `pendingCancellation`; cancelar, aceitar e desistir; retirar proposta; renovar.
3. **Motorista preparado:** vans, chave Pix e menu do perfil.
4. **Parcelas** (os dois perfis).
5. **Rotas** (lista e editor).
6. **Viagem de verdade:** executar (motorista) e acompanhar (responsável), com o WebSocket da viagem e o mapa; home do responsável com viagens do dia e faltas.
7. **Filho em detalhe:** endereços, responsáveis (remover, sair, passar o admin) e rotas.
8. **Perfis e avaliação:** perfil do motorista com avaliação, perfil do responsável.
9. **Grupos e aviso**, **histórico de viagens e ocorrências**.
10. **Privacidade:** exportar dados e excluir conta.

---

## 10. Limitações conhecidas
- **Sem push com o app fechado** (#150): as notificações ficam guardadas e aparecem ao abrir.
- **GPS do motorista no navegador** precisa da aba aberta e da tela ligada (Wake Lock); em HTTPS no celular.
- **Cota da API de mapas** (OpenRouteService grátis):
  - acabou a cota → o cadastro de endereço dá **503**;
  - a viagem continua sem a linha e sem o tempo: trate `path`/`etaSeconds` `null` mostrando só a van.
- **Foto do filho** só no navegador (`localStorage`); o backend não guarda.
- **Pagamento** é Pix manual: o app não recebe dinheiro. Mercado Pago fica pra depois (#192).
