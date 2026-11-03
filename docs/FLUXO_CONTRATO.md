# Fluxo do contrato: guia pro front

Toda rota precisa do header `Authorization: Bearer <token>`.
Os IDs são UUID. O front **nunca inventa ID**: todo ID que ele manda veio de alguma resposta anterior da API.

## Resumo do fluxo

```
1. Motorista e pai entram em contato   -> o pai resgata o código do motorista e nasce o chat
2. Motorista monta a proposta          -> escolhe o filho do pai e uma escola dele (+ PDF opcional)
3. Motorista envia                     -> nasce o contrato PENDING + mensagem PROPOSAL no chat
4. Pai abre a proposta                 -> vê os detalhes (e baixa o PDF)
5. Pai aceita ou recusa                -> ACTIVE (gera as parcelas) ou CANCELED + mensagem no chat
```

O contrato **nasce dentro do chat**: criar, aceitar e recusar ficam em `/api/chats/...`.
Em `/api/contract/...` ficam só as consultas.

## Passo a passo com as rotas

### 1. O chat entre motorista e pai

| Quem | Rota | Pra quê |
|---|---|---|
| Motorista | `GET /api/users/me` | pega o `linkCode` (9 caracteres). Ele passa esse código pro pai (por fora do app) |
| Pai | `POST /api/invite-code/redeem` body `{"inviteCode":"ABC123XYZ"}` | **201**, devolve o `chatId` (texto puro, um UUID). Resgatar de novo devolve o mesmo chat |
| Os dois | `GET /api/chats` | lista os chats: `id` (o chatId), `otherUserId`, `otherUserName`, `otherUserPhotoKey` |

Do `GET /api/chats`, o motorista usa o **`otherUserId`**: ele é o `responsibleId` da proposta.

### 2. O motorista monta a proposta (telas de escolha)

| Rota | Devolve | Vira no body |
|---|---|---|
| `GET /api/chats/{chatId}/students` | filhos do pai desse chat: `[{id, name}]` | `studentId` |
| `GET /api/school/my-schools` | escolas do motorista: `[{id, name, address...}]` | `schoolId` |

Se a escola não aparece na lista, o motorista cadastra antes com `POST /api/school/add-school`.

### 3. PDF do contrato (obrigatório)

1. `GET /api/storage/presigned-url?fileName=contrato.pdf&contentType=application/pdf`, que devolve `{uploadUrl, fileKey}`.
2. `PUT` direto na `uploadUrl`, com o header `Content-Type: application/pdf` e o arquivo no corpo. Esse envio vai direto pro MinIO,
   **sem** o token.
3. O `fileKey` vai em `document.url` no passo 4.

Toda proposta precisa do PDF: sem `document.url`, ou com um `fileKey` que não existe no storage, a proposta volta **400**.

### 4. O motorista envia a proposta

`POST /api/chats/{chatId}/contract`

```json
{
  "responsibleId": "<otherUserId do chat>",
  "studentId": "<id de GET /api/chats/{chatId}/students>",
  "schoolId": "<id de GET /api/school/my-schools>",
  "contract": {
    "periodicity": "MONTHLY",
    "value": 350.00,
    "startDate": "2027-02-01",
    "endDate": "2027-06-30"
  },
  "document": {
    "url": "<fileKey do passo 3>",
    "type": "TRANSPORT_CONTRACT"
  }
}
```

- `periodicity`: `WEEKLY`, `MONTHLY`, `QUARTERLY`, `SEMI_ANNUALLY` ou `ANNUALLY`.
- Datas no formato `AAAA-MM-DD`.

**Resposta 201:**
```json
{
  "message": { "...mensagem PROPOSAL que foi pro chat..." },
  "userDriverContractId": "...",
  "contract": { "contractId": "...", "status": "PENDING", "...": "mesmo formato do GET /api/contract/{id}" }
}
```

**Erros:**

| Código | Quando |
|---|---|
| 400 | o pai não é o responsável **principal** (admin) desse filho, ou o motorista não está nesse chat |
| 403 | a escola não é desse motorista (quem não é motorista também cai aqui, porque não tem escola) |
| 409 | já existe contrato **pendente ou ativo** entre esse motorista e esse aluno |

### 5. A proposta chega no chat do pai

Pelo WebSocket do chat (`ws://.../chat/{chatId}?token=JWT`) ou pelo `GET /api/chats/{chatId}/messages`, chega uma mensagem com:
```json
{ "messageType": "PROPOSAL", "content": "Proposta de Contrato",
  "payload": "{\"userDriverContractId\": \"...\", \"contractId\": \"...\"}" }
```
**O `payload` é um texto com JSON dentro**: faça `JSON.parse(msg.payload)` pra pegar o `contractId`.
Mensagens com `messageType` diferente de `REGULAR_CHAT` são do sistema: o front mostra como card (com os
botões "ver contrato", "aceitar" e "recusar"), não como balão de texto.

### 6. O pai abre a proposta

`GET /api/contract/{contractId}`: só as duas partes conseguem ver. A resposta traz:
- `status`, `periodicity`, `value`, `startDate` e `endDate`;
- `studentId` e `studentName`;
- `schoolId`, `schoolName` e `schoolAddress`;
- `driverId` e `driverName`, `responsibleId` e `responsibleName`;
- `documentFileKey` e `documentType`.

Pra baixar o PDF: `GET /api/storage/download-url?fileKey=<documentFileKey>`, que devolve `{downloadUrl}`. Aí é só abrir essa URL
(ela vale por alguns minutos).

### 7. O pai aceita ou recusa

| Rota | Efeito | Mensagem que vai pro chat |
|---|---|---|
| `PUT /api/chats/{chatId}/contracts/{contractId}/accept` | contrato `ACTIVE` + gera as parcelas, da `startDate` até a `endDate`, conforme a `periodicity` (fev a jun mensal = 5 parcelas). Cada parcela vence na `startDate` + n períodos (começou dia 15, vence todo dia 15). O pai **assina** e o documento fica `SIGNED` | `APPROVAL` "Contrato Aceito" |
| `PUT /api/chats/{chatId}/contracts/{contractId}/reject` | contrato `CANCELED`, documento `REJECTED` | `REJECTION` "Contrato Recusado" |

**Assinatura:** quando o motorista propõe, ele assina; quando o pai aceita, o pai assina. Cada assinatura guarda quem, quando e o hash dos termos (partes, aluno, escola, valor, período e documento). O `GET /api/contract/{contractId}` mostra `documentStatus` e `signatures: [{ userId, name, signedAt, valid }]`. Se o contrato mudar depois de assinado, `valid` vira `false`.

As duas devolvem **200 sem corpo**. Só o responsável do contrato consegue: o motorista recebe **403**.
Se o contrato já foi aceito ou recusado, dá **409**. Se a `startDate` da proposta já passou, o aceite também dá **409**: o motorista precisa mandar uma proposta nova.

### 7.1 O motorista retira a proposta

| Rota | Efeito | Mensagem que vai pro chat |
|---|---|---|
| `PUT /api/chats/{chatId}/contracts/{contractId}/withdraw` | contrato `CANCELED`, documento `REJECTED` | `REJECTION` "Proposta Retirada" |

**200 sem corpo**. Só o motorista da proposta consegue (outro usuário: **403**), e só enquanto ela está `PENDING` (senão **409**). No chat, o card da proposta fica encerrado igual a uma recusa.

### 8. Depois

- `GET /api/contract/my-contracts` lista os contratos do usuário logado, motorista ou pai, **paginado** (`{ content, totalElements, ... }`, padrão 20 por página, do início mais recente). Filtros opcionais: `status`, `studentId` e `driverId`. Cada item traz `pendingCancellation` (o pedido de cancelamento esperando resposta, ou `null`) e `overduePayments` (parcelas vencidas e não pagas).
- `GET /api/contract/{contractId}` traz também `cancellations` (todos os pedidos, do mais antigo pro mais novo) e `overduePayments`.
- Com o contrato `ACTIVE`, o motorista consegue colocar o aluno na rota (`POST /api/route/{routeId}/add-student/{studentId}`).
- Parcelas e Pix: `docs/FLUXO_PAGAMENTO.md`.

### 9. Cancelar um contrato ativo

Uma parte pede, com o motivo, e **a outra aceita ou recusa**. Não vai mensagem pro chat: o pedido aparece na lista de contratos (`pendingCancellation`) e a outra parte recebe notificação.

| Rota | Quem | Efeito |
|---|---|---|
| `POST /api/contract/{contractId}/cancellation` `{ "reason": "..." }` | qualquer uma das partes | cria o pedido `PENDING` (**201**). Motivo obrigatório, até 500 caracteres |
| `PUT /api/contract/{contractId}/cancellation/accept` | a outra parte | **cancela na hora** (`ACCEPTED`) |
| `PUT /api/contract/{contractId}/cancellation/reject` | a outra parte | `REJECTED`: o contrato continua ativo |
| `DELETE /api/contract/{contractId}/cancellation` | quem pediu | `WITHDRAWN`: desistiu do pedido |

Todas devolvem o pedido: `{ id, status, unilateral, reason, requestedById, requestedByName, requestedAt, decidedById, decidedByName, decidedAt }`.

**Motorista com 2 ou mais parcelas atrasadas:** o pedido dele já cancela o contrato, sem esperar o pai (`status: ACCEPTED`, `unilateral: true`). O pai nunca cancela sozinho. Atrasada = parcela `PENDING` com vencimento antes de hoje.

**Ao cancelar:**
- o contrato vira `CANCELED`;
- as parcelas que **ainda iam vencer** viram `CANCELED`; as **vencidas continuam devidas** (o motorista ainda pode dar baixa nelas);
- o aluno sai das rotas e dos grupos do motorista, e as faltas futuras nessas rotas são apagadas;
- fica no histórico: o contrato, o PDF, as assinaturas, as parcelas, o chat e as viagens.

**Erros:** **400** sem motivo; **403** quem não é parte, quem pediu tentando aceitar ou recusar, ou a outra parte tentando desistir; **404** responder sem pedido pendente; **409** contrato que não está `ACTIVE`, já existe pedido pendente, ou a van está rodando com o aluno agora (tente depois que a viagem terminar).

## Notificações do contrato

Além da mensagem no chat, cada passo vira notificação: fica gravada e chega na hora pelo WebSocket `/notifications?token=JWT`.

| Quando | Quem recebe | Tipo | `payload` |
|---|---|---|---|
| o motorista envia a proposta | o responsável | `CONTRACT_PROPOSED` "Nova proposta de contrato" | `chatId`, `contractId` |
| o pai aceita | o motorista | `CONTRACT_ACCEPTED` "Contrato aceito" | `chatId`, `contractId` |
| o pai recusa | o motorista | `CONTRACT_REJECTED` "Contrato recusado" | `chatId`, `contractId` |
| o motorista retira a proposta | o responsável | `CONTRACT_WITHDRAWN` "Proposta retirada" | `chatId`, `contractId` |
| uma parte pede o cancelamento | a outra | `CANCELLATION_REQUESTED` "Pedido de cancelamento" | `contractId` |
| a outra parte recusa | quem pediu | `CANCELLATION_REJECTED` "Cancelamento recusado" | `contractId` |
| quem pediu desiste | a outra | `CANCELLATION_WITHDRAWN` "Pedido de cancelamento retirado" | `contractId` |
| contrato cancelado (aceite ou motorista com atraso) | a outra | `CONTRACT_CANCELED` "Cancelamento aceito" / "Contrato cancelado" | `contractId` |

Com o `contractId` do `payload`, o app abre direto a tela do contrato. As rotas de notificação estão em `docs/FLUXO_TRIP.md`, seção 6.

## Regras e pontos de atenção

- O `contract` da proposta é validado e volta **400** com a mensagem do erro: periodicidade, valor e datas obrigatórios; valor maior que zero e com até 2 casas; `startDate` não pode ter passado; `endDate` não pode ser antes da `startDate`; duração de no máximo **2 anos**.
- `document.url` é obrigatório e precisa ser o `fileKey` de um PDF já enviado (passo 3); `document.type` precisa ser `TRANSPORT_CONTRACT` (ou vir vazio). Senão, **400**.
- Não existe rota pra editar uma proposta. Pra mudar, o motorista retira (ou o pai recusa) e o motorista manda outra.
- **Worker diário** (`docs/WORKER.md`): proposta não respondida até a data de início vira `CANCELED` (notificação `CONTRACT_EXPIRED` pros dois); contrato que passou da `endDate` vira `INACTIVE` e o aluno sai das rotas e grupos, igual ao cancelamento (notificação `CONTRACT_ENDED`).

### 10. O motorista vê o perfil do responsável

`GET /api/responsible/{responsibleId}/profile` (só motorista que conversa ou tem contrato com ele; senão **403**):
```json
{ "id": "...", "name": "Maria", "photoKey": "profile/...", "phone": "11999999999",
  "financialStatus": "OVERDUE", "overduePayments": 1,
  "students": [ { "studentId": "...", "studentName": "Ana", "contractId": "...", "contractStatus": "ACTIVE" } ] }
```
- `financialStatus` é a situação **geral** do responsável (`OVERDUE` mesmo que o atraso seja com outro motorista); `overduePayments` conta **só as parcelas com esse motorista** (as dos outros não aparecem).
- `students`: só os filhos que têm (ou tiveram) contrato com ele, cada um com `relationType` (o rótulo: `FINANCIAL`, `LEGAL`, `WITHDRAWAL`) e `admin`.
- No detalhe do contrato (`GET /api/contract/{id}`), `studentResponsibles` lista **todos** os responsáveis do aluno (o admin primeiro): `{ responsibleId, name, relationType, admin }`, sem telefone.
- Sem CPF nem e-mail. A foto vem como `photoKey` (igual ao perfil do motorista).

## Onde está no código

**Proposta e decisão** (as rotas ficam no chat porque a proposta nasce como mensagem):

| Rota | Controller | Service | O que o service faz |
|---|---|---|---|
| `POST /api/chats/{chatId}/contract` | [ChatController.createContract](../backend/src/main/java/br/com/vanroute/backend/controllers/ChatController.java#L108) | [ContractService.createContract](../backend/src/main/java/br/com/vanroute/backend/services/ContractService.java#L105) | valida os termos ([validateTerms](../backend/src/main/java/br/com/vanroute/backend/services/ContractService.java#L226): valor, datas, início no passado, 2 anos), exige o PDF e confere no storage ([StorageService.exists](../backend/src/main/java/br/com/vanroute/backend/services/StorageService.java#L92)), confere vínculo e escola, grava contrato, documento `TRANSPORT_CONTRACT` e a assinatura do motorista |
| `PUT /api/chats/{chatId}/contracts/{id}/accept` | [ChatController.acceptContract](../backend/src/main/java/br/com/vanroute/backend/controllers/ChatController.java#L139) | [ContractService.acceptContract](../backend/src/main/java/br/com/vanroute/backend/services/ContractService.java#L184) | recusa se a data de início já passou, ativa, assina pelo pai e gera as parcelas ([installmentDates](../backend/src/main/java/br/com/vanroute/backend/services/ContractService.java#L250): início + n períodos) |
| `PUT /api/chats/{chatId}/contracts/{id}/reject` | [ChatController.rejectContract](../backend/src/main/java/br/com/vanroute/backend/controllers/ChatController.java#L161) | [ContractService.rejectContract](../backend/src/main/java/br/com/vanroute/backend/services/ContractService.java#L268) | `CANCELED` + documento `REJECTED` |
| `PUT /api/chats/{chatId}/contracts/{id}/withdraw` | [ChatController.withdrawContract](../backend/src/main/java/br/com/vanroute/backend/controllers/ChatController.java#L184) | [ContractService.withdrawContract](../backend/src/main/java/br/com/vanroute/backend/services/ContractService.java#L289) | só o motorista e só `PENDING`: `CANCELED` + documento `REJECTED` |

O controller manda a mensagem do chat (`PROPOSAL`, `APPROVAL`, `REJECTION`) pelo Redis e a notificação com [NotificationService.send](../backend/src/main/java/br/com/vanroute/backend/services/NotificationService.java#L50).

**Consulta:**

| Rota | Controller | Service | O que o service faz |
|---|---|---|---|
| `GET /api/contract/my-contracts` | [ContractController.myContracts](../backend/src/main/java/br/com/vanroute/backend/controllers/ContractController.java#L49) | [ContractService.findMyContracts](../backend/src/main/java/br/com/vanroute/backend/services/ContractService.java#L400) | filtra por status, aluno e motorista; junta o pedido pendente ([findPendingOf](../backend/src/main/java/br/com/vanroute/backend/repositories/ContractCancellationRepository.java#L28)) e as atrasadas ([countOverdueOf](../backend/src/main/java/br/com/vanroute/backend/repositories/PaymentRepository.java#L45)) numa consulta só |
| `GET /api/contract/{id}` | [ContractController.getContract](../backend/src/main/java/br/com/vanroute/backend/controllers/ContractController.java#L83) | [ContractService.getContractDetails](../backend/src/main/java/br/com/vanroute/backend/services/ContractService.java#L340) | só as partes; traz documento, assinaturas (com [termsHash](../backend/src/main/java/br/com/vanroute/backend/services/ContractService.java#L317)), histórico de cancelamento ([findHistory](../backend/src/main/java/br/com/vanroute/backend/repositories/ContractCancellationRepository.java#L23)) e atrasadas |

**Cancelamento** (passo 9):

| Rota | Controller | Service | O que o service faz |
|---|---|---|---|
| `POST /api/contract/{id}/cancellation` | [ContractController.requestCancellation](../backend/src/main/java/br/com/vanroute/backend/controllers/ContractController.java#L57) | [ContractCancellationService.request](../backend/src/main/java/br/com/vanroute/backend/services/ContractCancellationService.java#L78) | valida o motivo, exige contrato `ACTIVE` e nenhum pedido pendente; motorista com [OVERDUE_TO_CANCEL_ALONE](../backend/src/main/java/br/com/vanroute/backend/services/ContractCancellationService.java#L47) (2) atrasadas cancela na hora |
| `PUT /api/contract/{id}/cancellation/accept` | [ContractController.acceptCancellation](../backend/src/main/java/br/com/vanroute/backend/controllers/ContractController.java#L65) | [ContractCancellationService.accept](../backend/src/main/java/br/com/vanroute/backend/services/ContractCancellationService.java#L119) | só a outra parte; cancela na hora |
| `PUT /api/contract/{id}/cancellation/reject` | [ContractController.rejectCancellation](../backend/src/main/java/br/com/vanroute/backend/controllers/ContractController.java#L71) | [ContractCancellationService.reject](../backend/src/main/java/br/com/vanroute/backend/services/ContractCancellationService.java#L131) | só a outra parte; o contrato continua |
| `DELETE /api/contract/{id}/cancellation` | [ContractController.withdrawCancellation](../backend/src/main/java/br/com/vanroute/backend/controllers/ContractController.java#L77) | [ContractCancellationService.withdraw](../backend/src/main/java/br/com/vanroute/backend/services/ContractCancellationService.java#L143) | só quem pediu |

O cancelamento de fato fica em [ContractCancellationService.cancel](../backend/src/main/java/br/com/vanroute/backend/services/ContractCancellationService.java#L186): recusa se a van está rodando com o aluno, marca o contrato `CANCELED`, cancela as parcelas futuras, tira o aluno das rotas, das faltas futuras e dos grupos do motorista. Os avisos saem por [notifyOther](../backend/src/main/java/br/com/vanroute/backend/services/ContractCancellationService.java#L219).

**Perfil do responsável** (passo 10): [ResponsibleProfileController.profile](../backend/src/main/java/br/com/vanroute/backend/controllers/ResponsibleProfileController.java#L26) → [ResponsibleProfileService.profile](../backend/src/main/java/br/com/vanroute/backend/services/ResponsibleProfileService.java#L46), que confere o acesso (chat ou contrato) e conta as atrasadas só entre os dois ([PaymentRepository.countOverdueBetween](../backend/src/main/java/br/com/vanroute/backend/repositories/PaymentRepository.java#L27)).

**Banco e modelos:**
- [V31__contract_cancellations_and_pix_key.sql](../backend/src/main/resources/db/migration/V31__contract_cancellations_and_pix_key.sql): tabela `contract_cancellations` (um pedido pendente por contrato, garantido por índice único).
- Modelos: [ContractCancellation.java](../backend/src/main/java/br/com/vanroute/backend/models/contract/ContractCancellation.java#L1), [CancellationStatus.java](../backend/src/main/java/br/com/vanroute/backend/models/contract/enums/CancellationStatus.java#L1), [Contract.java](../backend/src/main/java/br/com/vanroute/backend/models/contract/Contract.java#L1), [UserDriverContract.java](../backend/src/main/java/br/com/vanroute/backend/models/contract/UserDriverContract.java#L1).
- DTOs: [ContractProposalRequestDTO.java](../backend/src/main/java/br/com/vanroute/backend/dtos/contract/ContractProposalRequestDTO.java#L1), [ContractSummaryResponseDTO.java](../backend/src/main/java/br/com/vanroute/backend/dtos/contract/ContractSummaryResponseDTO.java#L1), [ContractDetailsResponseDTO.java](../backend/src/main/java/br/com/vanroute/backend/dtos/contract/ContractDetailsResponseDTO.java#L1), [CancellationRequestDTO.java](../backend/src/main/java/br/com/vanroute/backend/dtos/contract/CancellationRequestDTO.java#L1), [CancellationResponseDTO.java](../backend/src/main/java/br/com/vanroute/backend/dtos/contract/CancellationResponseDTO.java#L1).

**Testes:** [ContractServiceTest](../backend/src/test/java/br/com/vanroute/backend/services/ContractServiceTest.java), [ContractCancellationServiceTest](../backend/src/test/java/br/com/vanroute/backend/services/ContractCancellationServiceTest.java) e as seções 8 e 13i de [run-api-tests.sh](../backend/api-tests/run-api-tests.sh).
