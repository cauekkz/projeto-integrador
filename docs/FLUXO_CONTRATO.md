# Fluxo do contrato: guia pro front

Toda rota precisa do header `Authorization: Bearer <token>`.
Os IDs são UUID. O front **nunca inventa ID**: todo ID que ele manda veio de alguma resposta anterior da API.

## Resumo do fluxo

```
1. Motorista e pai entram em contato   -> o pai resgata o código do motorista e nasce o chat
2. Motorista monta a proposta          -> escolhe o filho do pai e uma escola dele (+ PDF obrigatório)
3. Motorista envia                     -> nasce o contrato PENDING + mensagem PROPOSAL no chat
4. Pai abre a proposta                 -> vê os detalhes (e baixa o PDF)
5. Pai aceita ou recusa                -> SCHEDULED/ACTIVE (gera as parcelas) ou CANCELED + mensagem no chat
6. Data de início                       -> o worker passa o SCHEDULED pra ACTIVE: só então o aluno entra nas rotas
7. Cancelamento                         -> aviso prévio de 30 dias (ou na hora, se a outra parte aceitar)
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

Toda proposta precisa do PDF. A proposta volta **400** quando:
- falta o `document.url`;
- o `fileKey` não é de um arquivo que o próprio motorista enviou;
- o arquivo não existe no storage;
- o arquivo não é um PDF de verdade (o começo dele é conferido, não só o nome);
- o arquivo passa de **10 MB**.

**O PDF é congelado na proposta:** o backend copia o arquivo para `contracts/{contractId}.pdf` e apaga o enviado. O `documentFileKey` do contrato passa a ser essa chave nova. A URL de upload vale 15 minutos e sempre grava na mesma chave; sem a cópia, o motorista conseguiria trocar o PDF depois de propor. Nenhuma URL de upload grava em `contracts/`.

Cada proposta consome o `fileKey` enviado. Para mandar outra proposta, envie o PDF de novo.

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
| `PUT /api/chats/{chatId}/contracts/{contractId}/accept` | contrato `ACTIVE` se começa hoje, ou `SCHEDULED` se começa depois (o worker ativa na data de início). Gera as parcelas da `startDate` até a `endDate`, conforme a `periodicity` (fev a jun mensal = 5 parcelas). Cada parcela vence na `startDate` + n períodos (começou dia 15, vence todo dia 15). O pai **assina** e o documento fica `SIGNED` | `APPROVAL` "Contrato Aceito" |
| `PUT /api/chats/{chatId}/contracts/{contractId}/reject` | contrato `CANCELED`, documento `REJECTED` | `REJECTION` "Contrato Recusado" |

**Assinatura:** quando o motorista propõe, ele assina; quando o pai aceita, o pai assina. Cada assinatura guarda quem, quando e o hash dos termos (partes, aluno, escola, valor, período e a chave do PDF congelado). O `GET /api/contract/{contractId}` mostra `documentStatus` e `signatures: [{ userId, name, signedAt, valid }]`. Se o contrato mudar no banco depois de assinado, `valid` vira `false`. O PDF em si não muda, porque está congelado (passo 3).

As duas devolvem **200 sem corpo**. Só o responsável do contrato consegue: o motorista recebe **403**. O contrato precisa ser **dessa conversa**, entre o motorista e o pai do chat. Aceitar o contrato do motorista A pelo `chatId` da conversa com o motorista B dá **403**.
Se o contrato já foi aceito ou recusado, dá **409**. Se a `startDate` da proposta já passou, o aceite também dá **409**: o motorista precisa mandar uma proposta nova.

**Duplo clique:** o contrato é lido com trava no banco (`SELECT ... FOR UPDATE`). O segundo aceite espera o primeiro terminar e recebe **409**. O banco também não aceita duas parcelas com o mesmo vencimento no mesmo contrato.

**Tudo junto:** o contrato, a mensagem do chat e a notificação são gravados na mesma transação. Se algo falhar, nada é gravado e o card não fica num estado pela metade. O WebSocket só recebe depois de gravado.

### 7.1 O motorista retira a proposta

| Rota | Efeito | Mensagem que vai pro chat |
|---|---|---|
| `PUT /api/chats/{chatId}/contracts/{contractId}/withdraw` | contrato `CANCELED`, documento `REJECTED` | `REJECTION` "Proposta Retirada" |

**200 sem corpo**. Só o motorista da proposta consegue (outro usuário: **403**), e só enquanto ela está `PENDING` (senão **409**). No chat, o card da proposta fica encerrado igual a uma recusa.

### 8. Depois

- `GET /api/contract/my-contracts` lista os contratos do usuário logado, motorista ou pai, **paginado** (`{ content, totalElements, ... }`, padrão 20 por página, do início mais recente).
  - Filtros opcionais: `status`, `studentId` e `driverId`.
  - Cada item traz `pendingCancellation` (o pedido de cancelamento em andamento, com `effectiveDate`, ou `null`) e `overduePayments` (parcelas vencidas e não pagas).
- `GET /api/contract/{contractId}` traz também `cancellations` (todos os pedidos, do mais antigo pro mais novo) e `overduePayments`.
- **Status do contrato aceito:** `SCHEDULED` (aceito, ainda não começou), `ACTIVE` (valendo), `INACTIVE` (passou da `endDate`) e `CANCELED`.
- Só com o contrato `ACTIVE` o motorista consegue colocar o aluno na rota (`POST /api/route/{routeId}/add-student/{studentId}`) ou num grupo. Só alunos com contrato `ACTIVE` com o motorista entram numa viagem.
- **Renovação:** com o motorista e o aluno, existe **no máximo o contrato atual e um próximo**. Nunca há dois contratos valendo ao mesmo tempo, nem uma fila de contratos futuros.
  1. Com o contrato atual `ACTIVE`, o motorista propõe o próximo, que precisa **começar depois do fim** do atual. Se sobrepor: **409**.
  2. Enquanto essa proposta está `PENDING`, ou depois de aceita (`SCHEDULED`), qualquer outra proposta para o mesmo aluno dá **409**.
  3. Quando o atual termina, o aluno **continua nas rotas e grupos**, porque a renovação está aceita. Entre os dois contratos (ex.: férias), ele fica na rota mas **não entra nas viagens**, que só incluem quem tem contrato `ACTIVE`.
  4. Na data de início, o worker passa a renovação pra `ACTIVE`, e a partir daí o motorista pode propor a seguinte.
- Parcelas e Pix: `docs/FLUXO_PAGAMENTO.md`.

### 9. Cancelar um contrato (aviso prévio)

Qualquer uma das partes pede, com o motivo. O contrato é cancelado **sozinho 30 dias depois do pedido** (a `effectiveDate`), a não ser que antes disso:
- **a outra parte aceite**, e aí cancela na hora; ou
- **quem pediu desista**.

**Ninguém recusa:** ninguém fica preso no contrato. Vale para contrato `ACTIVE` e `SCHEDULED`. Não vai mensagem pro chat: o pedido aparece na lista de contratos (`pendingCancellation`) e a outra parte recebe notificação.

| Rota | Quem | Efeito |
|---|---|---|
| `POST /api/contract/{contractId}/cancellation` `{ "reason": "..." }` | qualquer uma das partes | cria o pedido `PENDING` com `effectiveDate` = hoje + 30 dias (**201**). Motivo obrigatório, até 500 caracteres |
| `PUT /api/contract/{contractId}/cancellation/accept` | a outra parte | **cancela na hora** (`ACCEPTED`) |
| `DELETE /api/contract/{contractId}/cancellation` | quem pediu | `WITHDRAWN`: desistiu do pedido; o contrato continua |

Todas devolvem o pedido: `{ id, status, unilateral, reason, requestedById, requestedByName, requestedAt, effectiveDate, decidedById, decidedByName, decidedAt }`.

**Status do pedido:**
- `PENDING`: o aviso está correndo;
- `ACCEPTED`: a outra parte aceitou, ou o motorista cancelou na hora;
- `COMPLETED`: o aviso terminou e o worker cancelou;
- `WITHDRAWN`: quem pediu desistiu, ou o contrato acabou antes;
- `REJECTED`: só aparece no histórico antigo.

**Motorista com parcela vencida há 30 dias ou mais:** o pedido dele cancela o contrato na hora (`status: ACCEPTED`, `unilateral: true`). O pai pode pedir mesmo devendo, mas a dívida não some.

**Ao cancelar** (na hora ou no fim do aviso):
- o contrato vira `CANCELED`;
- as parcelas que vencem **depois** do cancelamento viram `CANCELED`. As vencidas até ele, inclusive as que venceram durante o aviso, **continuam devidas**: o pai segue como devedor até o motorista dar baixa ou dispensar a parcela;
- o aluno sai das rotas e dos grupos do motorista, e as faltas futuras nessas rotas são apagadas. A exceção é quando o aluno já tem a renovação aceita com o mesmo motorista: aí ele continua;
- fica no histórico: o contrato, o PDF, as assinaturas, as parcelas, o chat e as viagens.

Multa ou outra cláusula do PDF fica fora do app: o motorista cobra direto do pai.

**Erros:**
- **400**: sem motivo.
- **403**: quem não é parte, ou quem pediu tentando aceitar o próprio pedido, ou a outra parte tentando desistir.
- **404**: aceitar ou desistir sem pedido em andamento.
- **409**: contrato que não está `SCHEDULED` nem `ACTIVE`, ou já existe pedido em andamento, ou a van está rodando com o aluno agora (tente depois que a viagem terminar; no fim do aviso, o worker espera o próximo dia).

## Notificações do contrato

Além da mensagem no chat, cada passo vira notificação: fica gravada e chega na hora pelo WebSocket `/notifications?token=JWT`.

| Quando | Quem recebe | Tipo | `payload` |
|---|---|---|---|
| o motorista envia a proposta | o responsável | `CONTRACT_PROPOSED` "Nova proposta de contrato" | `chatId`, `contractId` |
| o pai aceita | o motorista | `CONTRACT_ACCEPTED` "Contrato aceito" | `chatId`, `contractId` |
| o pai recusa | o motorista | `CONTRACT_REJECTED` "Contrato recusado" | `chatId`, `contractId` |
| o motorista retira a proposta | o responsável | `CONTRACT_WITHDRAWN` "Proposta retirada" | `chatId`, `contractId` |
| uma parte pede o cancelamento | a outra | `CANCELLATION_REQUESTED` "Pedido de cancelamento" (com a data em que o contrato cai) | `contractId` |
| quem pediu desiste | a outra | `CANCELLATION_WITHDRAWN` "Pedido de cancelamento retirado" | `contractId` |
| contrato cancelado (aceite ou motorista com parcela vencida há 30+ dias) | a outra | `CONTRACT_CANCELED` "Cancelamento aceito" / "Contrato cancelado" | `contractId` |
| fim do aviso prévio (worker) | os dois | `CONTRACT_CANCELED` "Contrato cancelado" | `contractId` |
| contrato agendado chega na data de início (worker) | os dois | `CONTRACT_STARTED` "Contrato começou" | `contractId` |

Com o `contractId` do `payload`, o app abre direto a tela do contrato. As rotas de notificação estão em `docs/FLUXO_TRIP.md`, seção 6.

## Regras e pontos de atenção

- O `contract` da proposta é validado e volta **400** com a mensagem do erro: periodicidade, valor e datas obrigatórios; valor maior que zero e com até 2 casas; `startDate` não pode ter passado; `endDate` não pode ser antes da `startDate`; duração de no máximo **2 anos**.
- `document.url` é obrigatório e precisa ser o `fileKey` de um PDF de até 10 MB que o motorista enviou (passo 3); `document.type` precisa ser `TRANSPORT_CONTRACT` (ou vir vazio). Senão, **400**.
- Não existe rota pra editar uma proposta. Pra mudar, o motorista retira (ou o pai recusa) e o motorista manda outra.
- **Worker diário** (`docs/WORKER.md`):
  - proposta não respondida até a data de início vira `CANCELED` (notificação `CONTRACT_EXPIRED` pros dois);
  - pedido de cancelamento com o aviso vencido cancela o contrato;
  - contrato `SCHEDULED` vira `ACTIVE` na data de início;
  - contrato que passou da `endDate` vira `INACTIVE` e o aluno sai das rotas e grupos, igual ao cancelamento e com a mesma exceção da renovação (notificação `CONTRACT_ENDED`).

### 10. O motorista vê o perfil do responsável

`GET /api/responsible/{responsibleId}/profile` (só motorista que conversa ou tem contrato com ele; senão **403**):
```json
{ "id": "...", "name": "Maria", "photoKey": "profile/...", "phone": "11999999999",
  "financialStatus": "OVERDUE", "overduePayments": 1,
  "students": [ { "studentId": "...", "studentName": "Ana", "contractId": "...", "contractStatus": "ACTIVE" } ] }
```
- `financialStatus` e `overduePayments` são **só das parcelas com esse motorista**: atraso com outro motorista não aparece, porque esse motorista não é credor dessas parcelas. No próprio perfil (`responsibleId` = o logado), valem para todos os contratos.
- `students`: só os filhos que têm (ou tiveram) contrato com ele, cada um com `relationType` (o rótulo: `FINANCIAL`, `LEGAL`, `WITHDRAWAL`) e `admin`.
- No detalhe do contrato (`GET /api/contract/{id}`), `studentResponsibles` lista **todos** os responsáveis do aluno (o admin primeiro): `{ responsibleId, name, relationType, admin }`, sem telefone.
- Sem CPF nem e-mail. A foto vem como `photoKey` (igual ao perfil do motorista).

## Onde está no código

**Proposta e decisão** (as rotas ficam no chat porque a proposta nasce como mensagem; [ContractChatService](../backend/src/main/java/br/com/vanroute/backend/services/ContractChatService.java#L1) grava contrato, mensagem e notificação na mesma transação e confere que o contrato é da conversa em [checkContractOfChat](../backend/src/main/java/br/com/vanroute/backend/services/ContractChatService.java#L96)):

| Rota | Controller | Service | O que o service faz |
|---|---|---|---|
| `POST /api/chats/{chatId}/contract` | [ChatController.createContract](../backend/src/main/java/br/com/vanroute/backend/controllers/ChatController.java#L81) | [ContractService.createContract](../backend/src/main/java/br/com/vanroute/backend/services/ContractService.java#L106) | valida os termos ([validateTerms](../backend/src/main/java/br/com/vanroute/backend/services/ContractService.java#L238)), confere vínculo e escola, trava o aluno e recusa proposta pendente ou período sobreposto ([existsPendingOrOverlapping](../backend/src/main/java/br/com/vanroute/backend/repositories/UserDriverContractRepository.java#L33)), congela o PDF ([StorageService.freezeContractPdf](../backend/src/main/java/br/com/vanroute/backend/services/StorageService.java#L145)) e grava contrato, documento e a assinatura do motorista |
| `PUT /api/chats/{chatId}/contracts/{id}/accept` | [ChatController.acceptContract](../backend/src/main/java/br/com/vanroute/backend/controllers/ChatController.java#L93) | [ContractService.acceptContract](../backend/src/main/java/br/com/vanroute/backend/services/ContractService.java#L187) | trava o contrato ([lockContract](../backend/src/main/java/br/com/vanroute/backend/services/ContractService.java#L229)), recusa se a data de início já passou, deixa `ACTIVE` ou `SCHEDULED`, assina pelo pai e gera as parcelas ([installmentDates](../backend/src/main/java/br/com/vanroute/backend/services/ContractService.java#L262)) |
| `PUT /api/chats/{chatId}/contracts/{id}/reject` | [ChatController.rejectContract](../backend/src/main/java/br/com/vanroute/backend/controllers/ChatController.java#L99) | [ContractService.rejectContract](../backend/src/main/java/br/com/vanroute/backend/services/ContractService.java#L280) | `CANCELED` + documento `REJECTED` |
| `PUT /api/chats/{chatId}/contracts/{id}/withdraw` | [ChatController.withdrawContract](../backend/src/main/java/br/com/vanroute/backend/controllers/ChatController.java#L106) | [ContractService.withdrawContract](../backend/src/main/java/br/com/vanroute/backend/services/ContractService.java#L302) | só o motorista e só `PENDING`: `CANCELED` + documento `REJECTED` |

**Consulta:**

| Rota | Controller | Service | O que o service faz |
|---|---|---|---|
| `GET /api/contract/my-contracts` | [ContractController.myContracts](../backend/src/main/java/br/com/vanroute/backend/controllers/ContractController.java#L49) | [ContractService.findMyContracts](../backend/src/main/java/br/com/vanroute/backend/services/ContractService.java#L414) | filtra por status, aluno e motorista; junta o pedido em andamento ([findPendingOf](../backend/src/main/java/br/com/vanroute/backend/repositories/ContractCancellationRepository.java#L28)) e as atrasadas ([countOverdueOf](../backend/src/main/java/br/com/vanroute/backend/repositories/PaymentRepository.java#L59)) numa consulta só |
| `GET /api/contract/{id}` | [ContractController.getContract](../backend/src/main/java/br/com/vanroute/backend/controllers/ContractController.java#L78) | [ContractService.getContractDetails](../backend/src/main/java/br/com/vanroute/backend/services/ContractService.java#L354) | só as partes; traz documento, assinaturas (com [termsHash](../backend/src/main/java/br/com/vanroute/backend/services/ContractService.java#L338)), histórico de cancelamento e atrasadas |

**Cancelamento** (passo 9):

| Rota | Controller | Service | O que o service faz |
|---|---|---|---|
| `POST /api/contract/{id}/cancellation` | [ContractController.requestCancellation](../backend/src/main/java/br/com/vanroute/backend/controllers/ContractController.java#L58) | [ContractCancellationService.request](../backend/src/main/java/br/com/vanroute/backend/services/ContractCancellationService.java#L88) | valida o motivo, trava o contrato e exige `SCHEDULED`/`ACTIVE` ([acceptedContractOf](../backend/src/main/java/br/com/vanroute/backend/services/ContractCancellationService.java#L164)); cria o pedido com o aviso de [NOTICE_DAYS](../backend/src/main/java/br/com/vanroute/backend/services/ContractCancellationService.java#L51) dias, ou cancela na hora se o motorista tem parcela vencida há [OVERDUE_DAYS_TO_CANCEL_NOW](../backend/src/main/java/br/com/vanroute/backend/services/ContractCancellationService.java#L52) dias ([existsUnpaidDueUntil](../backend/src/main/java/br/com/vanroute/backend/repositories/PaymentRepository.java#L33)) |
| `PUT /api/contract/{id}/cancellation/accept` | [ContractController.acceptCancellation](../backend/src/main/java/br/com/vanroute/backend/controllers/ContractController.java#L66) | [ContractCancellationService.accept](../backend/src/main/java/br/com/vanroute/backend/services/ContractCancellationService.java#L133) | só a outra parte; cancela na hora |
| `DELETE /api/contract/{id}/cancellation` | [ContractController.withdrawCancellation](../backend/src/main/java/br/com/vanroute/backend/controllers/ContractController.java#L72) | [ContractCancellationService.withdraw](../backend/src/main/java/br/com/vanroute/backend/services/ContractCancellationService.java#L149) | só quem pediu |

O cancelamento de fato fica em [ContractCancellationService.cancel](../backend/src/main/java/br/com/vanroute/backend/services/ContractCancellationService.java#L185): recusa se a van está rodando com o aluno, marca o contrato `CANCELED`, cancela as parcelas futuras e, se não houver renovação ([existsOtherAccepted](../backend/src/main/java/br/com/vanroute/backend/repositories/UserDriverContractRepository.java#L42)), tira o aluno das rotas, das faltas futuras e dos grupos. No fim do aviso, o worker faz o mesmo em [contracts.go](../worker/internal/jobs/contracts.go) (`CancelAfterNotice` e `releaseStudent`).

**Perfil do responsável** (passo 10): [ResponsibleProfileController.profile](../backend/src/main/java/br/com/vanroute/backend/controllers/ResponsibleProfileController.java#L26) → [ResponsibleProfileService.profile](../backend/src/main/java/br/com/vanroute/backend/services/ResponsibleProfileService.java#L46), que confere o acesso (chat ou contrato) e conta as atrasadas só entre os dois ([PaymentRepository.countOverdueBetween](../backend/src/main/java/br/com/vanroute/backend/repositories/PaymentRepository.java#L41)).

**Banco e modelos:**
- [V31__contract_cancellations_and_pix_key.sql](../backend/src/main/resources/db/migration/V31__contract_cancellations_and_pix_key.sql): tabela `contract_cancellations` (um pedido pendente por contrato, garantido por índice único).
- [V35__contract_schedule_and_notice.sql](../backend/src/main/resources/db/migration/V35__contract_schedule_and_notice.sql): `UNIQUE (contract_id, due_date)` nas parcelas, `effective_date` do aviso prévio, pedido `COMPLETED` e contratos aceitos que ainda não começaram como `SCHEDULED`.
- Trava das mudanças de status: [ContractRepository.findByIdForUpdate](../backend/src/main/java/br/com/vanroute/backend/repositories/ContractRepository.java#L21).
- Modelos: [ContractCancellation.java](../backend/src/main/java/br/com/vanroute/backend/models/contract/ContractCancellation.java#L1), [CancellationStatus.java](../backend/src/main/java/br/com/vanroute/backend/models/contract/enums/CancellationStatus.java#L1), [ContractStatus.java](../backend/src/main/java/br/com/vanroute/backend/models/contract/enums/ContractStatus.java#L1), [Contract.java](../backend/src/main/java/br/com/vanroute/backend/models/contract/Contract.java#L1), [UserDriverContract.java](../backend/src/main/java/br/com/vanroute/backend/models/contract/UserDriverContract.java#L1).
- DTOs: [ContractProposalRequestDTO.java](../backend/src/main/java/br/com/vanroute/backend/dtos/contract/ContractProposalRequestDTO.java#L1), [ContractSummaryResponseDTO.java](../backend/src/main/java/br/com/vanroute/backend/dtos/contract/ContractSummaryResponseDTO.java#L1), [ContractDetailsResponseDTO.java](../backend/src/main/java/br/com/vanroute/backend/dtos/contract/ContractDetailsResponseDTO.java#L1), [CancellationRequestDTO.java](../backend/src/main/java/br/com/vanroute/backend/dtos/contract/CancellationRequestDTO.java#L1), [CancellationResponseDTO.java](../backend/src/main/java/br/com/vanroute/backend/dtos/contract/CancellationResponseDTO.java#L1).

**Testes:** [ContractServiceTest](../backend/src/test/java/br/com/vanroute/backend/services/ContractServiceTest.java), [ContractCancellationServiceTest](../backend/src/test/java/br/com/vanroute/backend/services/ContractCancellationServiceTest.java), [ContractChatServiceTest](../backend/src/test/java/br/com/vanroute/backend/services/ContractChatServiceTest.java), os testes do worker em [jobs_test.go](../worker/internal/jobs/jobs_test.go) e as seções 8 e 13i de [run-api-tests.sh](../backend/api-tests/run-api-tests.sh).
