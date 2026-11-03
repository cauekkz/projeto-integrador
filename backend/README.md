# VanRoute — Backend: rotas da API

Guia para quem consome a API, principalmente o front. Para cada endpoint, mostra **o que mandar**, **o que volta** e **as regras**.

Outros guias:
- **[`docs/FLUXO_FRONT.md`](../docs/FLUXO_FRONT.md)**: como montar o front (telas, a ordem das chamadas, os WebSockets e a renovação do token);
- [`docs/FLUXO_ROTA.md`](../docs/FLUXO_ROTA.md): endereços, rota e pontos, do cadastro até a rota pronta (cada entidade, DTO e verificação);
- [`docs/FLUXO_TRIP.md`](../docs/FLUXO_TRIP.md): execução da rota, faltas e notificações, com todas as entradas e saídas;
- [`docs/FLUXO_CONTRATO.md`](../docs/FLUXO_CONTRATO.md): contrato passo a passo (com a assinatura);
- [`docs/PENDENCIAS_E_ERROS.md`](../docs/PENDENCIAS_E_ERROS.md): o que ainda falta no backend;
- [`docs/SUGESTOES_FUTURAS.md`](../docs/SUGESTOES_FUTURAS.md): colunas pouco usadas e decisões para depois (pagamento, produção).

Base: `http://localhost:9090`. Todos os endpoints abaixo exigem `Authorization: Bearer <token>`, exceto os de cadastro e login.

**Logo depois do login**, o front chama **`GET /api/users/me`**:
```json
{ "id": "uuid", "name": "...", "email": "...", "phone": "...", "cpf": "...",
  "roles": ["ROLE_DRIVER"], "photoKey": "profile/... ou null", "linkCode": "ABCD2345K ou null" }
```
- O `id` é o que o front compara com o `senderId` das mensagens, para saber quais são suas. Ele também está no `sub` do token JWT, então o front pode lê-lo direto do token (`jwtDecode(token).sub`).
- O `linkCode` só vem para motorista. É o código que ele passa para os pais, pessoalmente ou de boca, e que eles usam em `POST /api/invite-code/redeem`.

**Erros** sempre vêm assim:
```json
{ "timestamp": "...", "message": "texto do erro", "details": "uri=/api/..." }
```

| Status | Quando |
|---|---|
| 400 | dado inválido ou regra violada |
| 401 | sem token ou token inválido/expirado |
| 403 | sem permissão (inclusive rota de outro perfil) |
| 404 | não existe |
| 409 | conflito (duplicado ou estado errado) |
| 422 | documento (CNH, CRLV) não passou na validação |
| 429 | muitas tentativas (código de e-mail) ou pediu outro código antes de 1 minuto |
| 500 | erro interno: a mensagem é sempre "Erro interno no servidor" (o detalhe fica só no log) |

**Quem pode chamar cada rota** (`config/SecurityConfig.java`; outro perfil recebe **403**):

| Perfil | Rotas |
|---|---|
| sem login | `POST /api/auth/**` (login, refresh, logout, verificar e-mail, reenviar código, esqueci a senha), `POST /api/responsible/auth/signup`, `POST /api/driver/auth/**` |
| só motorista | `/api/route/**`, `/api/school/**`, `/api/vehicle/**`, `/api/driver/groups/**`, `/api/driver/link-code`, `/api/driver/pix-key`, `GET /api/chats/{id}/students`, `POST /api/chats/{id}/contract`, `PUT .../contracts/{id}/withdraw`, `PUT /api/payments/{id}/paid` e `/cancel`, `POST /api/trip/start/{id}`, `/finish`, `POST .../occurrences`, `PUT /api/trip/{id}/student/**`, `GET /api/trip/route/{id}/today` |
| só responsável | `/api/student/**`, `/api/invite-code/**`, `PUT .../contracts/{id}/accept` e `/reject`, `PUT`/`DELETE /api/driver/{id}/rating` |
| os dois (logados) | o resto: chat, contrato e cancelamento, notificações, `GET /api/payments`, arquivos, histórico e ocorrências da viagem, perfil do motorista, perfil do responsável (o motorista dele ou ele mesmo), `/api/users/**`. Nessas, o service confere se a pessoa é parte daquele contrato, chat ou viagem |

**Configuração por ambiente** (o Spring junta 3 fontes; a de cima vence):

| Fonte | Onde | Pra quê |
|---|---|---|
| variáveis de ambiente | no servidor: `.env` na raiz (fora do git; modelo em `/.env.example`), lido pelo `docker compose` | produção: banco, Redis, MinIO, `JWT_KEY`, `ALLOWED_ORIGINS` (endereço do front), e-mail e mapas |
| `src/main/resources/application-local.properties` | na sua máquina (fora do git; modelo em `application-local.properties.example`) | desenvolvimento: senha do banco, `JWT_KEY`, MinIO, e-mail e a chave do OpenRouteService |
| `application.yml` / `application.properties` | no git | os padrões. Segredos **não** têm padrão: sem eles o backend não sobe |

Os testes que sobem o Spring usam o banco `vanroute_test` (`src/test/resources/application-test.properties`), nunca o `vanroute_db`.

---

## 1. Configuração da rota

### Conceitos
- **Rota (`route`)** é a *configuração* de uma viagem, por exemplo "Ida manhã" ou "Volta tarde". Ida e volta são rotas diferentes. Só o motorista que criou a rota pode ver e alterar.
- **Ponto (`route stop`)** é uma parada da van: um endereço e uma ordem (`orderIndex`). A ordem diz só quem vem antes de quem. Um ponto pode ter vários alunos e escolas, e o mesmo endereço pode ter mais de um ponto na rota.
- **School stop** liga uma escola a um ponto. A escola pode ficar no meio da rota.
- **Student stop** liga um aluno a um ponto, com:
  - `action`: `BOARDING` (embarque) ou `ALIGHTING` (desembarque);
  - `locationType`: `HOME`, `SCHOOL` ou `OTHER`;
  - `weekdays`: os dias em que o aluno usa aquele ponto. Sem dias = todos os dias.
- A **execução** (trip/boarding) segue os pontos pela ordem e, em cada um, pega os alunos daquele dia. Um ponto sem aluno no dia é pulado. Falta num dia é coisa do boarding; a configuração não muda. Veja a seção 1c e `docs/FLUXO_TRIP.md`.

### Passo a passo (motorista)
```
1. POST /api/route/create-route                        cria a rota ("Ida manha"; a volta e outra rota)
2. GET  /api/route/stop-options                        alunos dele (com os enderecos)
3. POST /api/route/{routeId}/add-student/{studentId}   "adicionar aluno na rota" (1 passo, de novo pro desembarque)
4. GET  /api/route/{routeId}/pending                   lista vazia = rota pronta pra viagem
5. ajustes: swap-order, move, weekdays e os DELETEs
```
Esse é o **único** jeito de criar ponto: não existe ponto sem aluno.

### Adicionar aluno na rota (1 passo)
`POST /api/route/{routeId}/add-student/{studentId}`: põe **uma** ação do aluno (embarque e desembarque são duas chamadas) e diz **em qual ponto**. Um dos dois é obrigatório:
- **`"newStop": true`:** cria um ponto novo, mesmo que já exista outro no mesmo endereço;
- **`"routeStopId"`:** usa um ponto que já existe nessa rota (o front acha no `GET /{routeId}/stops`, pelo endereço).

```json
{ "action": "BOARDING",  "locationType": "HOME",   "newStop": true, "studentAddressId": "<Casa da Ana>", "weekdays": ["MONDAY", "TUESDAY"] }
{ "action": "ALIGHTING", "locationType": "SCHOOL", "newStop": true }
{ "action": "ALIGHTING", "locationType": "SCHOOL", "routeStopId": "<ponto da escola que já existe>" }
{ "action": "BOARDING",  "locationType": "OTHER",  "newStop": true, "studentAddressId": "<Padaria>", "orderIndex": 3 }
```
- **`HOME`/`OTHER`:**
  - **ponto novo:** manda o `studentAddressId` (um endereço desse aluno, de `stop-options`);
  - **ponto que já existe:** o ponto tem que ser num endereço do aluno.
- **`SCHOOL`:** não manda endereço; é sempre a **escola do contrato**.
  - **ponto novo:** cria o ponto no endereço da escola, já ligado a ela (mesmo que a escola já esteja em outro ponto);
  - **ponto que já existe:** o ponto tem que ser no endereço da escola; ele liga a escola se ainda não estiver ligada.
- **`HOME`/`OTHER` nunca cria escola.**
- **Sem `weekdays`** = todos os dias.
- **`orderIndex`** (só no ponto novo, a partir de 1): a posição. Sem ele, a posição é automática:
  - **embarque:** entra **antes** de onde o aluno desce (antes dos desembarques dele e, se não é na escola, antes do primeiro ponto da escola do contrato);
  - **desembarque:** entra no fim.

  Os de baixo descem uma posição. O backend **não sabe se a rota é ida ou volta**: olha só a ação desse aluno.

**A regra rígida é a ordem:** em cada dia, o embarque tem que vir **antes** do desembarque. Isso vale para o ponto novo, para o ponto que já existe, para a posição escolhida e para todo ajuste depois. Se algo falhar, **nada fica gravado**.

→ `200`: `{ "stops": [...], "pending": [...] }` (veja "Resposta das alterações").
- **400:**
  - nem `routeStopId` nem `newStop`, ou os dois;
  - `orderIndex` num ponto que já existe;
  - `SCHOOL` com `studentAddressId`;
  - ponto novo de `HOME`/`OTHER` sem `studentAddressId`;
  - endereço de outro aluno;
  - ponto que não é no endereço certo;
  - quebra da ordem.
- **403:** sem contrato ativo com o aluno, ou a rota é de outro motorista.
- **404:** o `routeStopId` não existe nessa rota.
- **409:** a mesma ação no mesmo dia, ou o aluno já está nesse ponto.

**Exemplo, a van volta na escola** (deixa a Ana, busca o Paulo e volta para deixar ele):
1. Ana embarca em casa (`newStop`): casa (1).
2. Ana desce na escola (`newStop`): escola (2).
3. Paulo embarca na padaria (`newStop`, `orderIndex: 3`): padaria (3).
4. Paulo desce na escola:
   - **no ponto 2** (`routeStopId`): **400**, porque ele desceria antes de embarcar;
   - **num ponto novo** (`newStop`): escola (4). A rota fica casa, escola, padaria, escola.

### Criar rota
`POST /api/route/create-route`
```json
{ "name": "Ida Manhã" }
```
→ `201`:
```json
{ "id": "uuid", "name": "Ida Manhã", "driverName": "Nome do motorista" }
```

### Endereços: quem cadastra o quê
| Endereço | Quem cadastra | Pra quê |
|---|---|---|
| do **responsável** (`responsibles.address_id`) | o pai, no cadastro | **cobrança** (pagamento, contrato). Não entra na rota |
| do **aluno** (`student_addresses`, vários, com apelido) | o pai | **onde o aluno é buscado ou deixado**: "Casa", "Casa da avó", "Inglês de terça" |
| da **escola** | o motorista, ao cadastrar a escola | o ponto da escola |

O ponto da rota **não tem endereço digitado**: ele usa o mesmo endereço do aluno ou da escola, sem buscar a coordenada de novo na API de mapas. Os **dias da semana** continuam no student stop.

**Endereço sem repetição:** todo endereço novo passa pelo mesmo lugar (`AddressService.findOrCreate`). O endereço é o mesmo quando tem **o mesmo CEP e o mesmo número**; o jeito de escrever a rua não importa. Se o CEP termina em `000` (pode ser o CEP único de uma cidade pequena), ele compara também a rua e a cidade. Então:
- se **já existe** um endereço com essa chave, **só liga nele** (não cria outro e não chama a API de mapas);
- se não existe, cria e busca a coordenada uma vez.

"Avenida Paulista, 1000, 01310-100" e "AV. PAULISTA, 1000, 01310100" são o mesmo endereço. A coordenada que vier do app é ignorada: quem define é a busca. Como o endereço é compartilhado, ele **nunca é editado**; mudou o endereço, liga num novo.

**Endereços do aluno (responsável):**

| Endpoint | O que faz |
|---|---|
| `GET /api/student/{id}/addresses` | `[{ id, label, address }]`, os ativos. O `id` é o `studentAddressId` |
| `POST /api/student/{id}/addresses` `{label, address}` | endereço novo (busca a coordenada uma vez). `201` |
| `POST /api/student/{id}/addresses` `{label, addressId}` | **reaproveita** um endereço que já existe: de outro filho ou o de cobrança do responsável (só evita digitar de novo; cada filho tem a própria lista). `403` se o endereço não é dele nem de um filho dele; `409` se o aluno já tem esse endereço |
| `DELETE /api/student/{id}/addresses/{studentAddressId}` | tira o endereço do aluno: apaga **só a ligação** (o endereço continua pra quem mais usa). O aluno **sai dos pontos de casa** com esse endereço (a rota fica com pendência), e o motorista recebe a notificação `ROUTE` "Endereço removido". `409` se for o último endereço do aluno, ou se tiver uma viagem rodando com ele agora |

Ver os endereços: qualquer responsável do aluno. **Adicionar e tirar: só o responsável admin** (quem cadastrou o aluno); os outros recebem `403`, porque mexer nos endereços muda a rota.

No cadastro do aluno (`POST /api/student/create-student`):
- **um endereço:** `address` (novo) **ou** `addressId` (reaproveita um da família: de outro filho ou o seu), e `addressLabel` (opcional). Vira o "Casa";
- **vários de uma vez:** `"addresses": [{ "label": "Casa", "addressId": "..." }, { "label": "Natação", "address": { ... } }]`. O primeiro sem apelido vira "Casa". O mesmo endereço duas vezes dá `400`.

### Quem pode entrar na rota
`GET /api/route/stop-options` (motorista):
```json
{ "students": [ { "studentId": "...", "studentName": "Ana",
                  "addresses": [ { "id": "<studentAddressId>", "label": "Casa", "address": { ... } } ] } ] }
```
Só aparecem os alunos com contrato **ativo** com ele. O `id` de cada endereço é o `studentAddressId` do `add-student`. A escola não aparece: é sempre a do contrato.

### Resposta das alterações
Tudo que altera a rota (`add-student`, `swap-order`, `move`, `weekdays` e os três `DELETE`) devolve `200` com a rota inteira **já alterada** e as pendências:
```json
{ "stops":   [ { "id": "...", "orderIndex": 1, "address": { ... }, "students": [ ... ], "schools": [ ... ] } ],
  "pending": [ { "studentId": "...", "studentName": "Ana", "missingBoarding": [], "missingAlighting": ["FRIDAY"] } ] }
```
`pending` vazio = rota pronta. Com pendência a **viagem não inicia** (veja "Iniciar"), então o front pode mostrar o aviso na hora ("Ana: falta desembarque na sexta").

**Formato de um ponto** (também é o formato do GET):
```json
{
  "id": "uuid do route stop",
  "orderIndex": 1,
  "address": { "id": "...", "street": "...", "zipCode": "...", "city": "...", "neighborhood": "...",
               "number": 10, "state": "SP", "latitude": -23.5, "longitude": -46.6 },
  "students": [ { "id": "uuid do student stop", "studentId": "...", "studentName": "Ana", "stopId": "...",
                  "locationType": "HOME", "action": "BOARDING", "weekdays": ["MONDAY", "WEDNESDAY"] } ],
  "schools":  [ { "id": "uuid do school stop", "schoolId": "...", "stopId": "..." } ]
}
```
Os `id` de `students` e `schools` são os usados para mover e excluir.

### Regras do aluno na rota
O `studentId` vem de `GET /api/contract/my-contracts?status=ACTIVE`, a lista de alunos com quem o motorista tem contrato ativo. `weekdays` vale em qualquer `locationType` (inclusive `SCHOOL`: "desce na escola só seg/qua") e, sem ele, vale todos os dias (segunda a domingo).

Regras (no `add-student` e também no `move`, que muda o aluno de ponto):
- **400:** `HOME`/`OTHER` num endereço que **não é um endereço cadastrado do aluno**, ou num ponto de escola.
- **403:** não existe contrato **ATIVO** entre o motorista e o aluno.
- **400:** `SCHOOL` fora do ponto da **escola do contrato** (no `add-student` ele já escolhe o ponto certo).
- **400:** o embarque ficaria **depois** do desembarque na ordem da rota.
- **409:** o aluno já tem a mesma ação nessa rota **num dos mesmos dias**. Pode haver, por exemplo, embarque na esquina A seg/qua e na esquina B ter/qui; não pode haver dois embarques na segunda.
- **409:** o aluno já está nesse ponto.

O caso comum é **um** embarque e **um** desembarque, sem dias.

O que o front precisa mudar está em [`docs/FLUXO_FRONT.md`, seção 11](../docs/FLUXO_FRONT.md#11-o-que-o-front-precisa-mudar-endereços-e-rota).

### Ajustes
| Endpoint | Corpo | O que faz |
|---|---|---|
| `PUT /api/route/stop/{routeStopId}/swap-order` | `{ "orderIndex": 1 }` | **Troca** o ponto com o que está na posição pedida. Ex.: 10 ↔ 1, o resto não muda. `400` se não houver ponto na posição |
| `PUT /api/route/student-stop/{studentStopId}/move` | `{ "routeStopId": "..." }` | Muda o aluno para outro ponto **da mesma rota** (`400` se for de outra rota). Se for `SCHOOL`, o destino precisa ser o ponto da escola |
| `PUT /api/route/student-stop/{studentStopId}/weekdays` | `{ "weekdays": ["TUESDAY"] }` | Troca os dias **desse** student stop (vazio = todos). `409` se bater com outro embarque/desembarque do aluno |
| `DELETE /api/route/student-stop/{studentStopId}` | — | Remove o aluno do ponto, sem regra |
| `DELETE /api/route/school-stop/{schoolStopId}` | — | Remove a escola do ponto. `409` se algum aluno dela ainda embarca ou desce ali (`SCHOOL`) |
| `DELETE /api/route/stop/{routeStopId}` | — | Remove o ponto. `409` se ainda tem aluno ou escola. Os pontos de baixo sobem uma posição |

Todos devolvem `{ stops, pending }` (seção "Resposta das alterações").

### Consultas
| Endpoint | Quem | Retorno |
|---|---|---|
| `GET /api/route/{routeId}/stops` | dono da rota | pontos em ordem, com alunos e escolas |
| `GET /api/route/get-all-routes-and-stops` | motorista | `[{ id, name, driverName, stops: [...] }]`, só as rotas dele |
| `GET /api/route/{id}` | dono da rota | `{ id, name, driverName }` |
| `GET /api/route/{routeId}/pending` | dono da rota | `[{ studentId, studentName, missingBoarding: [...], missingAlighting: [...] }]`: dias em que o aluno aparece mas falta embarque ou desembarque. Lista vazia = rota completa |
| `GET /api/student/{studentId}/routes` | responsável do aluno | `[{ id, name }]`: só o nome das rotas em que o filho está |
| `GET /api/school/my-schools` | motorista | `[{ id, name, phone, email, address }]` |

---

## 1b. Van (veículo)

A van é cadastrada pelo **CRLV digital (PDF)**. O backend:
1. valida a assinatura ICP-Brasil;
2. lê os dados do veículo do próprio PDF (sem OCR);
3. vincula a van ao motorista.

O CRLV **não precisa** estar no nome do motorista, e o PDF **não é guardado**.

| Endpoint | Quem | O que faz |
|---|---|---|
| `POST /api/vehicle/add-vehicle` (multipart, campo `documentPdf`) | motorista | `201` com a van. Se a van já existe (procura pelo **RENAVAM**, que nunca muda, e depois pela **placa**), só **vincula** o motorista à van existente, sem alterar os dados dela. A assinatura é validada sempre, para ninguém se vincular a uma van com um PDF falso. `422` se o PDF não for um CRLV digital válido. `409` se o motorista já está vinculado a essa van |
| `GET /api/vehicle/my-vehicles` | motorista | vans **ativas** do motorista (ele pode ter várias) |
| `PUT /api/vehicle/{vehicleId}/unlink` | motorista | o motorista para de usar a van (`end_date` = hoje). O histórico fica, e depois ele pode vincular de novo. `204` |

Resposta:
```json
{ "id": "...", "plate": "ABC1D23", "renavam": "12345678901", "chassis": "9BM...", "model": "M.BENZ/SPRINTER 415",
  "year": 2020, "manufactureYear": 2019, "color": "BRANCA", "fuel": "DIESEL", "category": "ALUGUEL",
  "capacity": 16, "licensingYear": 2026, "status": "ACTIVE", "linkedSince": "2026-10-21" }
```
`capacity` é o **número de passageiros** (campo LOTAÇÃO do CRLV). `year` é o ano do modelo, e `licensingYear` é o exercício (ano do licenciamento).

## 1c. Execução da rota (trip)

A **trip** é a viagem de uma rota num dia. Cada aluno do dia vira um **boarding** (`PENDING` → `BOARDED` → `DELIVERED`, ou `ABSENT`). O "hoje" é sempre no horário de São Paulo.

**Iniciar:** `POST /api/trip/start/{routeId}` com `{ "latitude", "longitude", "vehicleId"? }`, a posição atual do GPS do motorista.
- **Regras:**
  - só o motorista da rota;
  - a rota não pode ter pendências: `400` dizendo quem e o que falta ("Ana sem desembarque [FRIDAY]");
  - a van é a enviada ou a **van padrão da rota** (`PUT /api/route/{routeId}/set-vehicle/{vehicleId}`), e tem que ser uma van ativa do motorista.
- **Alunos do dia:** quem tem student stop nessa rota com o dia de hoje. Quem tem **falta avisada** já entra `ABSENT`.
- **Pontos:** só os que têm aluno hoje, na ordem. Escola sem aluno no dia é pulada.
- **Trajeto:** calculado pela API de rotas (OpenRouteService), da posição do motorista até os pontos que faltam. Fica no Redis.
- **Trip já existente:**
  - já existe trip hoje → `409`. O app chama `GET /api/trip/route/{routeId}/today` para **continuar**, ou inicia de novo com `?restart=true` para **recomeçar do zero** (a antiga vira `CANCELED`);
  - trip já finalizada hoje → `409`.

| Endpoint | Quem | O que faz |
|---|---|---|
| `GET /api/trip/{tripId}` | motorista | trip com os pontos do dia (alunos, status, `done`) e o trajeto |
| `PUT /api/trip/{tripId}/student/{studentId}/board` | motorista | `PENDING` → `BOARDED` (hora do embarque); a trip vira `IN_PROGRESS` |
| `PUT /api/trip/{tripId}/student/{studentId}/alight` | motorista | `BOARDED` → `DELIVERED` (hora do desembarque) |
| `PUT /api/trip/{tripId}/student/{studentId}/absent` | motorista | **pula o aluno** só nessa trip (não apareceu, ninguém respondeu). Só se ainda não embarcou. Recalcula o trajeto |
| `POST /api/trip/{tripId}/finish` | motorista | `FINISHED` com hora de fim |
| `GET /api/student/{studentId}/today-trips` | responsável | trips de hoje do filho, para pegar o `tripId` e abrir o WebSocket |

### Tempo real: WebSocket `ws://<host>/trip/{tripId}?token=<jwt>`
Sem polling: ao conectar, cada um já recebe o estado atual, e depois recebe uma mensagem a cada mudança (GPS, embarque, falta, fim).

- **Motorista** manda o GPS pelo próprio socket, a cada poucos segundos:
  ```json
  { "type": "position", "latitude": -23.55, "longitude": -46.63 }
  ```
  Ele recebe a trip inteira: todos os pontos com os tempos, todos os alunos e o trajeto.
- **Responsável** só recebe (se mandar posição, volta `{"type":"error"}`). Ele vê **só os filhos dele** e o status deles. **A van, o trajeto e o tempo restante só aparecem quando o próximo ponto da van é um ponto do filho dele**:
  - indo buscar na casa: vê a van chegando e o tempo;
  - o filho embarcou, e a van vai buscar os outros: só o status;
  - indo para a escola do filho: vê de novo;
  - entregue: só o status.

  Quem não é motorista nem responsável de um aluno da trip é desconectado (código `1003`).

Mensagem (`type: "trip-update"`):
```json
{ "type": "trip-update", "tripId": "...", "routeName": "Ida", "status": "IN_PROGRESS",
  "following": true, "latitude": -23.55, "longitude": -46.63, "positionAt": 1792585537168,
  "targetStopId": "...", "etaSeconds": 420.0,
  "path": [[-46.63, -23.55], [-46.631, -23.551], "..."],
  "students": [ { "studentId": "...", "studentName": "Ana", "status": "PENDING", "action": "BOARDING", "locationType": "HOME" } ],
  "stops": null }
```
- **`following`:** se o pai está acompanhando agora. Quando é `false`, a posição, o trajeto e o tempo vêm `null`.
- **`targetStopId` e `etaSeconds`:** o ponto para onde a van está indo e os **segundos que faltam** até ele.
- **`path`:** a linha do mapa (`[longitude, latitude]`). Para o pai, ela vai só até o ponto do filho.
- **`stops`:** só para o motorista, com todos os pontos e o `etaSeconds` de cada um.

**Trajeto e tempo:** a linha vem da API de rotas como uma lista de pontos (centenas, em ruas com curvas). O front desenha com Leaflet + OpenStreetMap e move o ícone da van. A API de rotas é chamada:
- ao iniciar;
- quando um aluno sai da trip;
- com o GPS chegando, a cada **5 min** (`TRIP_ETA_REFRESH_SECONDS`), ou na hora se a van sair da linha (mais de 150 m, `TRIP_OFF_ROUTE_METERS`). A cota da chave é de 200 rotas por dia.

Entre uma chamada e outra, o tempo restante só desconta. A chave da API fica só no backend.

**Navegação do motorista:** o front pode abrir o Google Maps ou o Waze com os pontos da trip em ordem (latitude e longitude vêm no `GET /api/trip/{tripId}`), sem chave nenhuma.

**Falta avisada pelo responsável** (vale para uma rota num dia; pode ser com antecedência):

| Endpoint | O que faz |
|---|---|
| `POST /api/student/{studentId}/absences` `{routeId, date}` | o filho não vai nessa rota nesse dia. Se a trip já está rolando, ele sai **na hora** e o trajeto é recalculado. O motorista recebe mensagem automática no chat (`messageType: ABSENCE`) |
| `GET /api/student/{studentId}/absences` | faltas avisadas de hoje em diante |
| `DELETE /api/student/{studentId}/absences/{absenceId}` | "ele vai sim": volta para a trip (se ainda não passou) e o motorista é avisado |

## 2. Contrato

A proposta nasce no chat. Criar, aceitar e recusar ficam no `ChatController`; a consulta fica no `ContractController`.

| Endpoint | Quem | Retorno |
|---|---|---|
| `GET /api/chats/{chatId}/students` | só o **motorista** que participa do chat | `[{ id, name }]`: filhos do pai da conversa de que ele é admin (o botão "ver filhos" usado para escolher o aluno da proposta) |
| `POST /api/chats/{chatId}/contract` | motorista | `201`: `{ message, userDriverContractId, contract }`, onde `contract` já vem completo (formato abaixo) |
| `PUT /api/chats/{chatId}/contracts/{contractId}/accept` | responsável do contrato | `200`; o contrato fica `ACTIVE`, as parcelas são geradas e o responsável **assina** (o documento fica `SIGNED`) |
| `PUT /api/chats/{chatId}/contracts/{contractId}/reject` | responsável do contrato | `200`; o contrato fica `CANCELED` e o documento `REJECTED` |
| `GET /api/contract/my-contracts?status=ACTIVE` | motorista ou responsável | página `{ content: [{ contractId, userDriverContractId, status, periodicity, value, startDate, endDate, studentId, studentName, schoolId, schoolName, driverId, driverName, responsibleId, responsibleName, pendingCancellation, overduePayments }], totalElements, ... }`: os contratos em que o usuário é parte. Filtros opcionais: `status`, `studentId`, `driverId`; `page`/`size` (padrão 20) |
| `GET /api/contract/{contractId}` | só as partes do contrato | detalhes (abaixo); os outros recebem `403` |

Corpo da proposta. `responsibleId` é o `otherUserId` do chat (`GET /api/chats`), `studentId` vem de `GET /api/chats/{chatId}/students` e `schoolId` vem de `GET /api/school/my-schools`:
```json
{ "responsibleId": "...", "studentId": "...", "schoolId": "...",
  "contract": { "periodicity": "MONTHLY", "value": 350.00, "startDate": "2027-02-01", "endDate": "2027-06-30" },
  "document": { "url": "<fileKey do PDF no MinIO>", "type": "TRANSPORT_CONTRACT" } }
```

Detalhes do contrato:
```json
{ "contractId": "...", "userDriverContractId": "...", "status": "PENDING", "periodicity": "MONTHLY",
  "value": 350.00, "startDate": "...", "endDate": "...",
  "studentId": "...", "studentName": "...", "schoolId": "...", "schoolName": "...", "schoolAddress": { ... },
  "driverId": "...", "driverName": "...", "responsibleId": "...", "responsibleName": "...",
  "documentFileKey": "...", "documentType": "TRANSPORT_CONTRACT",
  "documentStatus": "SIGNED",
  "signatures": [ { "userId": "...", "name": "Carlos", "signedAt": "2027-01-20T10:00:00", "valid": true },
                  { "userId": "...", "name": "Maria",  "signedAt": "2027-01-21T08:30:00", "valid": true } ] }
```
**Assinatura:** propor é o motorista assinar, e aceitar é o responsável assinar. Cada assinatura guarda **quem, quando e o hash (SHA-256) dos termos**: as partes, o aluno, a escola, o valor, o período e o documento. É uma assinatura eletrônica simples, com registro de autoria e integridade. `valid` diz se os termos de hoje ainda são os que foram assinados; se alguém mudar o contrato no banco, vira `false`. `documentStatus`: `PENDING` até o aceite, `SIGNED` ou `REJECTED`.
A mensagem PROPOSAL do chat traz só os IDs no `payload`. Para mostrar a proposta, o front chama `GET /api/contract/{contractId}` com o `contractId` do payload. Os dados vêm sempre atualizados do banco.

---

## 2b. Grupos, ocorrências, perfil do motorista e avaliação

### Grupos do motorista
O motorista agrupa os alunos dele como quiser ("Escola X manhã", "Irmãos Silva"). Um aluno pode estar em vários grupos. Só o próprio motorista vê e mexe; outro motorista recebe `404`, e responsável recebe `403`.

| Endpoint | Corpo | O que faz |
|---|---|---|
| `GET /api/driver/groups` | — | `[{ id, name, students: [{ studentId, studentName }] }]` |
| `POST /api/driver/groups` | `{ name }` | `201`. `409` se ele já tem um grupo com esse nome (sem diferenciar maiúscula) |
| `PUT /api/driver/groups/{id}` | `{ name }` | renomeia |
| `DELETE /api/driver/groups/{id}` | — | `204` (os alunos não são afetados) |
| `POST /api/driver/groups/{id}/students/{studentId}` | — | põe o aluno. `403` sem contrato **ativo** com ele; `409` se já está |
| `DELETE /api/driver/groups/{id}/students/{studentId}` | — | tira. `404` se não está |
| `POST /api/driver/groups/{id}/notice` | `{ reason? }` (opcional, até 300) | **avisa o grupo**: cada responsável recebe a notificação `DRIVER_NOTICE` "Aviso do motorista": *"Carlos avisou que não vai buscar Ana e Pedro hoje. Motivo: van na oficina."* (só os filhos dele que estão no grupo; sem motivo, só a mensagem padrão). Devolve `{ notifiedResponsibles }`. `409` se o grupo está vazio |

O aviso **não gera falta** nem mexe na rota: é só uma mensagem. Chega na hora pelo WebSocket `/notifications`.

### Ocorrências na viagem
| Endpoint | Quem | O que faz |
|---|---|---|
| `POST /api/trip/{tripId}/occurrences` `{ type, description }` | o motorista da viagem | registra e manda a notificação `OCCURRENCE` para os responsáveis de **todos** os alunos da viagem: "Ocorrência na viagem: trânsito" / "Ida Manhã: acidente na marginal". `201` com `{ id, tripId, type, description, dateTime, driverName }`. `409` se a viagem já terminou |
| `GET /api/trip/{tripId}/occurrences` | o motorista ou um responsável de aluno da viagem | a lista, a mais nova primeiro |

`type`: `VEHICLE`, `STUDENT`, `TRAFFIC`, `DISCIPLINARY`, `FINANCIAL`, `MEDICAL` ou `OTHER`. A notificação é em tempo real (WebSocket `/notifications`); o `GET` é o histórico.

### Perfil do motorista e avaliação
| Endpoint | Quem | O que faz |
|---|---|---|
| `GET /api/driver/{driverId}/profile` | o próprio motorista, ou um responsável que **já conversa** (chat) ou **tem contrato** com ele | perfil (abaixo); os outros recebem `403` |
| `PUT /api/driver/{driverId}/rating` `{ score, comment? }` | responsável que **tem ou já teve contrato** com ele (`ACTIVE`, `INACTIVE`, `SUSPENDED` ou `OVERDUE`; proposta pendente ou recusada não conta) | cria ou **troca** a nota dele (uma por responsável por motorista). `score` de 1 a 5; `comment` até 500 |
| `DELETE /api/driver/{driverId}/rating` | o responsável que avaliou | apaga a avaliação dele |

```json
{ "driverId": "...", "name": "Carlos", "photoKey": "profile/...", "memberSince": "2026-10-01",
  "schools":  [ { "schoolId": "...", "name": "Escola X" } ],
  "vehicles": [ { "model": "Sprinter", "color": "Branca", "plate": "ABC1D23", "capacity": 15 } ],
  "ratingAverage": 4.7, "ratingCount": 3,
  "myRating": { "score": 5, "comment": "Pontual", "date": "...", "responsibleName": "Maria" },
  "recentRatings": [ { "score": 5, "comment": "Pontual", "date": "...", "responsibleName": "Maria" } ] }
```
O perfil **não tem** CPF, e-mail nem telefone (a conversa é pelo chat). De quem avaliou, aparece só o primeiro nome. `ratingAverage` é `null` sem avaliações.

---

## 3. Chat

`POST /api/chats/{chatId}/messages`
```json
{ "content": "texto", "attachmentType": "IMAGE", "attachmentUrl": "<fileKey>" }
```
- Precisa de texto e/ou anexo.
- **Não** aceita `messageType` nem `payload`: toda mensagem de usuário é `REGULAR_CHAT`. As mensagens `PROPOSAL`, `APPROVAL` e `REJECTION` só o sistema gera, no fluxo de contrato.

Resposta (também é o que chega pelo WebSocket):
```json
{ "id": "...", "senderId": "...", "content": "...", "attachmentUrl": "...", "attachmentType": "IMAGE",
  "sentAt": "2026-10-21T08:30:00", "messageType": "REGULAR_CHAT", "payload": null }
```
Use **`messageType`** para saber se é proposta, aceite ou recusa.

**Tempo real:** conecte em `ws://localhost:9090/chat/{chatId}?token=<jwt>`. Cada mensagem enviada no chat chega como JSON para os dois participantes. Quem não participa do chat é desconectado (código `1003`).

`GET /api/chats` traz, por chat, `otherUserId`, `otherUserName` e `otherUserPhotoKey`.

---

## 4. Arquivos (MinIO)

O arquivo **não passa pelo backend**: o front envia e baixa direto do MinIO, usando links temporários (15 min) que o backend assina. O banco guarda só o **`fileKey`**, o nome do arquivo no bucket. O bucket é privado.

**Enviar (imagem/PDF do chat ou PDF do contrato):**
```
1. GET /api/storage/presigned-url?fileName=foto.jpeg&contentType=image/jpeg
   → { "uploadUrl": "...", "fileKey": "3f2a....jpeg" }
2. PUT <uploadUrl> com o arquivo no corpo e o header Content-Type igual ao do passo 1   (direto no MinIO)
3. usar o fileKey: attachmentUrl da mensagem ou document.url da proposta
```

**Baixar/exibir:**
```
1. GET /api/storage/download-url?fileKey=3f2a....jpeg
   → { "downloadUrl": "...", "fileKey": "..." }        ou 403
2. usar downloadUrl no <img src> ou no link do PDF
```

Quem pode baixar:

| Arquivo | Quem |
|---|---|
| anexo de chat | quem participa do chat |
| PDF de contrato | as partes do contrato |
| foto de perfil | qualquer usuário logado |

**Foto de perfil:**
```
1. GET /api/users/photo-upload-url?fileName=eu.jpeg&contentType=image/jpeg   (só image/*)
   → { "uploadUrl": "...", "fileKey": "profile/{seuId}/....jpeg" }
2. PUT <uploadUrl> com a imagem
3. PUT /api/users/update-photo   { "fileKey": "profile/{seuId}/....jpeg" }   → { "photoKey": "..." }
```
- Só aceita chave da sua própria pasta e que já exista no bucket; a foto antiga é apagada.
- O `photoKey` dos outros vem no `GET /api/chats` (`otherUserPhotoKey`); a sua vem no `/me`. Para exibir, use o `download-url`.

---

## 5. Cadastro, login e token

- **Cadastro de responsável** (`POST /api/responsible/auth/signup`):
  - valida CPF, senha forte, confirmação de senha e **endereço obrigatório** (o de cobrança);
  - aceita o endereço em `address` ou `addressRequestDTO`;
  - envia o código de verificação por e-mail; o login só funciona depois do `POST /api/auth/verify-email`;
  - a resposta traz o papel em `roles: ["ROLE_RESPONSIBLE"]`.
- **Papel do usuário:** cada usuário tem **um** papel (`ROLE_DRIVER`, `ROLE_RESPONSIBLE` ou `ROLE_ADMIN`), na coluna `users.role`. Ele vai no JWT, na claim `roles` (ex.: `["ROLE_DRIVER"]`), e no `GET /api/users/me`.
- **Escola** (`POST /api/school/add-school`): se a escola (nome + rua) já existe, o motorista é **vinculado** a ela. `409` se já estiver vinculado.
- **JWT:** expira (`JWT_EXPIRATION`, padrão 15 min), e token vencido dá `401`. O `sub` é o **id do usuário**. A chave vem de `JWT_KEY`; o valor padrão do `application.yml` é só para desenvolvimento.
- **Renovar o token sem senha:** o login também devolve `refreshToken` (30 dias, `JWT_REFRESH_EXPIRATION`). Com token vencido (`401`), o front chama `POST /api/auth/refresh` `{"refreshToken"}`, recebe um par novo (o usado deixa de valer) e repete a chamada. `POST /api/auth/logout` `{"refreshToken"}` apaga. No Redis fica só o hash do refreshToken. Passo a passo pro front em `docs/FLUXO_TRIP.md`, seção 4.10.
- **Notificações:** `GET /api/notifications`, `GET /api/notifications/unread-count`, `PUT /api/notifications/{id}/read`, `PUT /api/notifications/read-all` e o WebSocket `ws://<host>/notifications?token=<jwt>` (veja `docs/FLUXO_TRIP.md`, seção 6).
- **No backend**, o `authentication.getName()` devolve o **id** do usuário. Use `AuthenticatedUser.id(authentication)` (um `UUID`, sem ir ao banco). Os services recebem `UUID userId`, e o CPF só é usado no login e na checagem de cadastro duplicado.
- **Código de atualização do usuário** (`send-code-updateUser` / `update-user`): um por usuário (chave `User:SendCodeUser:{id}` no Redis).

## 6. Banco

As migrations ficam em `src/main/resources/db/migration` e o Flyway aplica sozinho quando o backend sobe (a última é a **V29**). Migration que já rodou não se edita: mudança de banco é sempre numa migration nova, com o próximo número (**V30**). Para zerar o banco local: `docker compose down -v`.

| Assunto | Tabelas |
|---|---|
| usuários | `users` (com `role`), `drivers`, `responsibles` (com o endereço de **cobrança**) |
| alunos | `students`, `student_responsibles` (vínculo e tipo), `student_addresses` (endereços de busca, com apelido) |
| endereços | `addresses` (cada lugar uma vez: mesmo CEP + número = mesmo endereço) |
| escola e van | `schools`, `driver_schools`, `vehicles`, `driver_vehicles` |
| contrato | `contracts`, `user_driver_contracts` (motorista + responsável + aluno + escola), `payments` (parcelas), `documents`, `signatures` |
| rota | `routes`, `route_drivers`, `route_stops`, `student_stops`, `school_stops` |
| viagem | `trips`, `boardings`, `student_absences`, `occurrences` |
| grupos | `driver_groups`, `driver_group_students` |
| outros | `chats`, `chat_messages`, `notifications`, `ratings` |

## 7. Rodar e testar

```bash
docker compose up -d db redis minio    # o banco é o vanroute_db; para zerar: docker compose down -v
cd backend && ./mvnw clean package     # compila e roda os testes unitários (no vanroute_db)
java -jar target/backend-0.0.1-SNAPSHOT.jar
```
Para a trip, o servidor também precisa de `ORS_DIRECTIONS_URL` (nos testes aponta para o simulador do `stubs.py`) e, nos testes, `TRIP_ETA_REFRESH_SECONDS=3`. O teste do WebSocket da trip é o `backend/api-tests/trip-ws-test.mjs`. Para ver a execução da rota e as notificações funcionando sem front: `bash backend/api-tests/demo/run-demo.sh` e abra http://localhost:8090 (motorista e 2 pais na mesma tela). Fluxo completo, entradas e saídas de todas as rotas e dos WebSockets (`/trip/{tripId}` e `/notifications`): `docs/FLUXO_TRIP.md`. Para testar contra a API real do OpenRouteService, suba o backend sem `ORS_GEOCODE_URL`/`ORS_DIRECTIONS_URL` (com a chave em `openrouteservice.api-key` no `application-local.properties`, que fica fora do git) e rode com `REAL_MAPS=1`: as contagens de chamadas, que só existem no simulador, aparecem como SKIP. 

**Testes de API ponta a ponta** (servidor real, dados fictícios, sem serviços externos), em `backend/api-tests/`:
```bash
python3 -I backend/api-tests/stubs.py &    # e-mail falso :2525 e simulador de mapas :8089
cd backend && \
  SPRING_MAIL_HOST=127.0.0.1 SPRING_MAIL_PORT=2525 SPRING_MAIL_USERNAME=stub SPRING_MAIL_PASSWORD=stub \
  SPRING_MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_ENABLE=false \
  ORS_GEOCODE_URL=http://127.0.0.1:8089/pelias/v1/search ORS_DIRECTIONS_URL=http://127.0.0.1:8089/v2/directions/driving-car \
  TRIP_ETA_REFRESH_SECONDS=3 TRIP_OFF_ROUTE_MIN_SECONDS=0 java -jar target/backend-0.0.1-SNAPSHOT.jar &
./api-tests/run-api-tests.sh   # usa node >= 22 nos testes de WebSocket
```
- Cada execução cria dados fictícios novos (CPFs válidos aleatórios), então pode ser repetida. Os dados ficam no `vanroute_db`.
- Os motoristas são inseridos via SQL, porque o cadastro real exige uma CNH digital assinada ICP-Brasil, que não dá para gerar com dados fictícios.
- Os testes unitários (`./mvnw test`) usam Mockito; o `BackendApplicationTests` sobe o contexto completo e roda o Flyway no `vanroute_db`.
