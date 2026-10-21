# VanRoute — Backend: rotas da API (o que mudou na branch `routeLogic`)

Guia para quem consome a API, principalmente o front. Para cada endpoint criado ou alterado, mostra **o que mandar**, **o que volta** e **as regras**. O relatório completo da auditoria (bugs, testes, pendências) está em [`docs/RELATORIO_AUDITORIA_VANROUTE.md`](../docs/RELATORIO_AUDITORIA_VANROUTE.md).

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
| 403 | sem permissão |
| 404 | não existe |
| 409 | conflito (duplicado ou estado errado) |

---

## 1. Configuração da rota

### Conceitos
- **Rota (`route`)** é a *configuração* de uma viagem, por exemplo "Ida manhã" ou "Volta tarde". Ida e volta são rotas diferentes. Só o motorista que criou a rota pode ver e alterar.
- **Ponto (`route stop`)** é onde a van para: um endereço e uma ordem (`orderIndex`). A ordem diz só quem vem antes de quem. Um ponto pode ter vários alunos e escolas.
- **School stop** liga uma escola a um ponto. A escola pode ficar no meio da rota.
- **Student stop** liga um aluno a um ponto, com:
  - `action`: `BOARDING` (embarque) ou `ALIGHTING` (desembarque);
  - `locationType`: `HOME`, `SCHOOL` ou `OTHER`;
  - `weekdays`: os dias em que o aluno usa aquele ponto. Sem dias = todos os dias.
- A **execução** (trip/boarding) ainda não existe. Quando existir, a trip de um dia segue os pontos pela ordem e, em cada um, pega os alunos daquele dia. Um ponto sem aluno no dia é pulado. Falta num dia é coisa do boarding; a configuração não muda.

### Passo a passo (motorista)
```
1. POST /api/route/create-route                            cria a rota
2. POST /api/route/{routeId}/stops                         cria os pontos (entram no fim)
3. POST /api/route/stop/{routeStopId}/add-school/{schoolId}    liga a escola a um ponto
4. POST /api/route/stop/{routeStopId}/add-student/{studentId}  liga o aluno a um ponto (embarque e desembarque)
5. GET  /api/route/{routeId}/pending                       confere se falta embarque/desembarque em algum dia
6. ajustes: swap-order, move, weekdays e os DELETEs
```

### Criar rota
`POST /api/route/create-route`
```json
{ "name": "Ida Manhã" }
```
→ `201`:
```json
{ "id": "uuid", "name": "Ida Manhã", "driverName": "Nome do motorista" }
```

### Criar pontos
`POST /api/route/{routeId}/stops` (recebe uma lista; cada ponto entra **no fim** da rota)
```json
[ { "address": { "street": "Rua A", "zipCode": "01000-000", "city": "São Paulo",
                 "neighborhood": "Centro", "number": 10, "state": "SP" } } ]
```
→ `200`: lista de pontos (formato abaixo). O endereço é geocodificado, e um endereço que não é encontrado retorna `400`.

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

### Ligar escola ao ponto
`POST /api/route/stop/{routeStopId}/add-school/{schoolId}` (sem corpo)
→ `200`: `{ "id": "uuid do school stop", "schoolId": "...", "stopId": "..." }`
- **403:** a escola não é do motorista (`/api/school/my-schools`).
- **409:** a escola já está nessa rota.

### Ligar aluno ao ponto
O `studentId` vem de `GET /api/contract/my-contracts?status=ACTIVE`, a lista de alunos com quem o motorista tem contrato ativo.

`POST /api/route/stop/{routeStopId}/add-student/{studentId}`
```json
{ "action": "BOARDING", "locationType": "HOME", "weekdays": ["MONDAY", "WEDNESDAY"] }
```
`weekdays` é opcional e, sem ele, vale todos os dias (segunda a domingo). O front manda um array de strings.

→ `200`:
```json
{ "id": "...", "studentId": "...", "studentName": "Ana", "stopId": "...", "locationType": "HOME", "action": "BOARDING", "weekdays": ["MONDAY", "WEDNESDAY"] }
```

Regras:
- **403:** não existe contrato **ATIVO** entre o motorista e o aluno.
- **400:** a rota não tem um school stop da **escola do contrato**.
- **400:** `locationType: SCHOOL` fora do ponto que tem a escola do contrato.
- **409:** o aluno já tem a mesma ação nessa rota **num dos mesmos dias**. Pode haver, por exemplo, embarque na esquina A seg/qua e na esquina B ter/qui; não pode haver dois embarques na segunda.
- **409:** o aluno já está nesse ponto.

O caso comum é **um** embarque e **um** desembarque, sem dias.

### Ajustes
| Endpoint | Corpo | O que faz |
|---|---|---|
| `PUT /api/route/stop/{routeStopId}/swap-order` | `{ "orderIndex": 1 }` | **Troca** o ponto com o que está na posição pedida. Ex.: 10 ↔ 1, o resto não muda. `400` se não houver ponto na posição |
| `PUT /api/route/student-stop/{studentStopId}/move` | `{ "routeStopId": "..." }` | Muda o aluno para outro ponto **da mesma rota** (`400` se for de outra rota). Se for `SCHOOL`, o destino precisa ser o ponto da escola |
| `PUT /api/route/student-stop/{studentStopId}/weekdays` | `{ "weekdays": ["TUESDAY"] }` | Troca os dias **desse** student stop (vazio = todos). `409` se bater com outro embarque/desembarque do aluno |
| `DELETE /api/route/student-stop/{studentStopId}` | — | Remove o aluno do ponto, sem regra. `204` |
| `DELETE /api/route/school-stop/{schoolStopId}` | — | Remove a escola do ponto. `409` se algum aluno da rota tem contrato com ela. `204` |
| `DELETE /api/route/stop/{routeStopId}` | — | Remove o ponto. `409` se ainda tem aluno ou escola. Os pontos de baixo sobem uma posição. `204` |

### Consultas
| Endpoint | Quem | Retorno |
|---|---|---|
| `GET /api/route/{routeId}/stops` | dono da rota | pontos em ordem, com alunos e escolas |
| `GET /api/route/get-all-routes-and-stops` | motorista | `[{ id, name, driverName, stops: [...] }]`, só as rotas dele |
| `GET /api/route/{id}` | dono da rota | `{ id, name, driverName }` |
| `GET /api/route/{routeId}/pending` | dono da rota | `[{ studentId, studentName, missingBoarding: [...], missingAlighting: [...] }]`: dias em que o aluno aparece mas falta embarque ou desembarque. Lista vazia = rota completa |
| `GET /api/student/{studentId}/routes` | responsável do aluno | `[{ id, name }]`: só o nome das rotas em que o filho está |
| `GET /api/school/my-schools` | motorista | `[{ id, name, phone, email, address }]`: o endereço ajuda a criar o ponto da escola |

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

## 2. Contrato

A proposta nasce no chat. Criar, aceitar e recusar ficam no `ChatController`; a consulta fica no `ContractController`.

| Endpoint | Quem | Retorno |
|---|---|---|
| `GET /api/chats/{chatId}/students` | só o **motorista** que participa do chat | `[{ id, name }]`: filhos do pai da conversa de que ele é admin (o botão "ver filhos" usado para escolher o aluno da proposta) |
| `POST /api/chats/{chatId}/contract` | motorista | `201`: `{ message, userDriverContractId, contract }`, onde `contract` já vem completo (formato abaixo) |
| `PUT /api/chats/{chatId}/contracts/{contractId}/accept` | responsável do contrato | `200`; o contrato fica `ACTIVE` e as parcelas são geradas |
| `PUT /api/chats/{chatId}/contracts/{contractId}/reject` | responsável do contrato | `200`; o contrato fica `CANCELED` |
| `GET /api/contract/my-contracts?status=ACTIVE` | motorista ou responsável | `[{ contractId, userDriverContractId, status, periodicity, value, startDate, endDate, studentId, studentName, schoolId, schoolName, driverId, driverName, responsibleId, responsibleName }]`: os contratos em que o usuário é parte; `status` é opcional |
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
  "documentFileKey": "...", "documentType": "TRANSPORT_CONTRACT" }
```
A mensagem PROPOSAL do chat traz só os IDs no `payload`. Para mostrar a proposta, o front chama `GET /api/contract/{contractId}` com o `contractId` do payload. Os dados vêm sempre atualizados do banco.

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
- `GET /api/users/{id}` traz `photoKey`; para exibir, use o `download-url`.

---

## 5. Outras mudanças de contrato da API

- **Cadastro de responsável** (`POST /api/responsible/auth/signup`):
  - agora valida CPF, senha forte, confirmação de senha e **endereço obrigatório**;
  - aceita o endereço em `address` ou `addressRequestDTO`;
  - envia o código de verificação por e-mail; o login só funciona depois do `POST /api/auth/verify-email`.
- **Cadastro de aluno** (`POST /api/student/create-student`): não tem mais `weekdays`; os dias ficam no student stop.
- **Escola** (`POST /api/school/add-school`): se a escola (nome + rua) já existe, o motorista é **vinculado** a ela, e não dá mais erro. `409` se já estiver vinculado.
- **Caminho removido:** `POST /api/student-stop/{studentId}/{routeStopId}` virou `POST /api/route/stop/{routeStopId}/add-student/{studentId}`.
- **JWT:** agora expira (`JWT_EXPIRATION`, padrão 15 min), e token vencido dá `401`. O `sub` passou a ser o **id do usuário** (antes era o CPF). Tokens antigos dão `401`; basta logar de novo.
- **No backend**, o `authentication.getName()` devolve o **id** do usuário. Use `AuthenticatedUser.id(authentication)` (um `UUID`, sem ir ao banco). Os services recebem `UUID userId`, e o CPF só é usado no login e na checagem de cadastro duplicado.
- **Código de atualização do usuário** (`send-code-updateUser` / `update-user`): agora é um por usuário (chave `User:SendCodeUser:{id}` no Redis). Antes a chave era única e um usuário podia usar o código do outro.

## 6. Banco (migrations novas)

| Migration | O que faz |
|---|---|
| V18 | antigo `V15__add_status...`, renomeado porque havia dois V15. Quem já tinha aplicado esse V15: `flyway repair` ou recriar o banco local |
| V19 | `student_stops.weekdays`; remove `student_stops.status`, `student_stops.route_id` e `student_addresses.weekdays` (sem uso) |
| V20 | `users.photo_key` |
| V21 | `vehicles`: `renavam` e `chassis` (únicos), `manufacture_year`, `color`, `fuel`, `category`, `licensing_year`. Em `driver_vehicles`, a UNIQUE (motorista, van) virou índice parcial `WHERE end_date IS NULL`: só um vínculo **ativo** por par, com histórico liberado |

## 7. Rodar e testar

```bash
docker compose up -d db redis minio
cd backend && ./mvnw clean package     # compila e roda os testes unitários
java -jar target/backend-0.0.1-SNAPSHOT.jar
```
Os testes de API ponta a ponta (servidor real, dados fictícios) estão em `backend/api-tests/`. Como rodar está no cabeçalho do `run-api-tests.sh` e na seção E do relatório.
