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

### 3. PDF do contrato (opcional)

1. `GET /api/storage/presigned-url?fileName=contrato.pdf&contentType=application/pdf`, que devolve `{uploadUrl, fileKey}`.
2. `PUT` direto na `uploadUrl`, com o header `Content-Type: application/pdf` e o arquivo no corpo. Esse envio vai direto pro MinIO,
   **sem** o token.
3. O `fileKey` vai em `document.url` no passo 4.

Sem PDF, manda `document.url` com qualquer texto, por enquanto. Veja "pontos de atenção".

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
| `PUT /api/chats/{chatId}/contracts/{contractId}/accept` | contrato `ACTIVE` + gera as parcelas, da `startDate` até a `endDate`, conforme a `periodicity` (fev a jun mensal = 5 parcelas) | `APPROVAL` "Contrato Aceito" |
| `PUT /api/chats/{chatId}/contracts/{contractId}/reject` | contrato `CANCELED` | `REJECTION` "Contrato Recusado" |

As duas devolvem **200 sem corpo**. Só o responsável do contrato consegue: o motorista recebe **403**.
Se o contrato já foi aceito ou recusado, dá **409**.

### 8. Depois

- `GET /api/contract/my-contracts` (`?status=ACTIVE` opcional) lista os contratos do usuário logado, motorista ou pai.
- Com o contrato `ACTIVE`, o motorista consegue colocar o aluno na rota (`PUT /api/route/stop/{id}/add-student/{studentId}`).

## Notificações do contrato

Além da mensagem no chat, cada passo vira notificação: fica gravada e chega na hora pelo WebSocket `/notifications?token=JWT`.

| Quando | Quem recebe | Tipo | `payload` |
|---|---|---|---|
| o motorista envia a proposta | o responsável | `CONTRACT_PROPOSED` "Nova proposta de contrato" | `chatId`, `contractId` |
| o pai aceita | o motorista | `CONTRACT_ACCEPTED` "Contrato aceito" | `chatId`, `contractId` |
| o pai recusa | o motorista | `CONTRACT_REJECTED` "Contrato recusado" | `chatId`, `contractId` |

Com o `contractId` do `payload`, o app abre direto a tela do contrato. As rotas de notificação estão em `docs/FLUXO_TRIP.md`, seção 6.

## Pontos de atenção (o backend ainda não trata)

- O body da proposta **não é validado**: faltando `contract` ou `document`, dá erro 500. O front precisa mandar sempre os dois.
- `startDate` depois de `endDate` passa sem erro, e o aceite gera 0 parcelas. Valide no front.
- Um `document.type` desconhecido vira `OTHER`, sem erro.
- Ainda não existe rota pra cancelar um contrato já `ACTIVE` nem pra editar uma proposta. Pra mudar uma proposta, o pai recusa e o motorista manda outra.
