# Fluxo de pagamento: guia pro front

O pai paga a parcela no **Pix do motorista**, pelo app do banco. O app não recebe o dinheiro: quem confirma que recebeu é o **motorista**, dando baixa na parcela. Se o pai quiser avisar que pagou, ele avisa pelo chat.

## Resumo

```
1. Motorista cadastra a chave Pix         PUT /api/driver/pix-key
2. Pai aceita o contrato                  -> parcelas PENDING (docs/FLUXO_CONTRATO.md, passo 7)
3. Pai vê as parcelas e copia a chave     GET /api/payments
4. Pai paga no app do banco (fora do app)
5. Motorista confere no banco e dá baixa  PUT /api/payments/{id}/paid   -> PAID + notificação pro pai
   ou dispensa a parcela                  PUT /api/payments/{id}/cancel -> CANCELED + notificação pro pai
```

## 1. Chave Pix do motorista

| Rota | Efeito |
|---|---|
| `GET /api/driver/pix-key` | `{ "type": "EMAIL", "key": "joao@van.com" }`, ou `type` e `key` nulos se não tem |
| `PUT /api/driver/pix-key` `{ "type": "PHONE", "key": "(11) 98765-4321" }` | salva e devolve a chave já no formato do banco |
| `DELETE /api/driver/pix-key` | remove (**204**) |

Só motorista: o responsável recebe **403** (regra de perfil no `SecurityConfig`).

| `type` | Aceita | Fica salva como |
|---|---|---|
| `CPF` | com ou sem pontuação, dígitos verificadores certos | `52998224725` |
| `CNPJ` | com ou sem pontuação, dígitos verificadores certos | `11222333000181` |
| `EMAIL` | até 77 caracteres | minúsculo, sem espaços |
| `PHONE` | DDD + número, com ou sem `+55` | `+5511987654321` |
| `RANDOM` | a chave aleatória (UUID) | minúscula |

Chave que não bate com o tipo: **400** "Chave Pix inválida para o tipo CPF".

## 2. Parcelas

As parcelas nascem no aceite do contrato: uma por período, com o valor do contrato. Cada uma vence na data de início + n períodos (começou dia 15, vence todo dia 15). Detalhes em `docs/FLUXO_CONTRATO.md`.

### `GET /api/payments`

O pai vê as parcelas dos contratos dele; o motorista, as dos contratos dele. Paginado (`page`, `size`, padrão 20), do vencimento mais antigo pro mais novo.

Filtros opcionais: `studentId`, `driverId`, `responsibleId`, `contractId`, `status` e `from`/`to` (período do vencimento, `AAAA-MM-DD`).

```json
{ "content": [ {
    "id": "...", "contractId": "...",
    "studentId": "...", "studentName": "Ana",
    "driverId": "...", "driverName": "Carlos",
    "responsibleId": "...", "responsibleName": "Maria",
    "value": 350.00, "dueDate": "2026-12-01",
    "status": "OVERDUE", "paymentMethod": null, "paidAt": null,
    "driverPixKeyType": "EMAIL", "driverPixKey": "carlos@van.com"
  } ],
  "totalElements": 5, "totalPages": 1 }
```

| `status` | Quer dizer |
|---|---|
| `PENDING` | em aberto, vencimento hoje ou depois |
| `OVERDUE` | em aberto, **vencida** (vencimento antes de hoje). O worker diário grava `OVERDUE` no banco e avisa o pai; até ele rodar, a API já mostra `OVERDUE` pela data. Só pra mostrar: o app não cobra multa nem juros |
| `PAID` | o motorista confirmou; tem `paidAt` e `paymentMethod` |
| `CANCELED` | o motorista dispensou, ou o contrato foi cancelado antes dela vencer |

**No front:** com `PENDING` ou `OVERDUE` e o usuário sendo o pai, mostre a `driverPixKey` com um **botão de copiar**. Se ela vier `null`, o motorista ainda não cadastrou a chave.

**400:** `to` antes de `from`, ou `status` que não existe.

### `PUT /api/payments/{paymentId}/paid` (motorista)

```json
{ "method": "PIX" }
```
`method`: `PIX`, `CASH`, `BOLETO` ou `CREDIT_CARD`. Vale pra parcela `PENDING` ou `OVERDUE`. Devolve a parcela com `status: PAID`, `paymentMethod` e `paidAt`. O pai recebe a notificação `PAYMENT` "Pagamento confirmado" e, se não tiver outra atrasada, a situação financeira dele volta pra `UP_TO_DATE` na hora.

### `PUT /api/payments/{paymentId}/cancel` (motorista)

Dispensa a parcela (ex.: o contrato começou no meio do mês e o motorista não vai cobrar a primeira). Devolve com `status: CANCELED`. O pai recebe a notificação `PAYMENT` "Parcela cancelada".

**As duas não têm volta.** Erros: **400** baixa sem `method`; **403** quem não é o motorista do contrato; **404** parcela que não existe; **409** parcela já paga ou cancelada.

Parcela atrasada de um contrato já cancelado continua aberta: o motorista ainda pode dar baixa nela.

## 3. Notificações

| Tipo | Quando | Quem recebe | `payload` |
|---|---|---|---|
| `PAYMENT` | o motorista dá baixa ou cancela uma parcela | o responsável do contrato | `paymentId`, `contractId` |
| `PAYMENT` "Parcela atrasada" | o worker diário marca a parcela como `OVERDUE` (uma vez só) | o responsável do contrato | `paymentId`, `contractId` |

Chegam na hora pelo WebSocket `/notifications?token=JWT` e ficam gravadas (`docs/FLUXO_TRIP.md`, seção 6).

## 4. O que fica pra depois

- Situação financeira do responsável (`responsibles.financial_status`): nasce `UP_TO_DATE`; o worker marca `OVERDUE` e o backend volta pra `UP_TO_DATE` na baixa. O motorista vê a situação **com ele** no perfil do responsável (`docs/FLUXO_CONTRATO.md`, passo 10).
- Cobrança pelo Mercado Pago (o pai paga pelo app e a baixa é automática): só depois de validar o fluxo do Pix.

## 5. Onde está no código

| Rota | Controller | Service | O que o service faz |
|---|---|---|---|
| `GET /api/driver/pix-key` | [DriverController.getPixKey](../backend/src/main/java/br/com/vanroute/backend/controllers/DriverController.java#L94) | [DriverService.getPixKey](../backend/src/main/java/br/com/vanroute/backend/services/DriverService.java#L74) | busca o motorista logado e devolve tipo e chave |
| `PUT /api/driver/pix-key` | [DriverController.setPixKey](../backend/src/main/java/br/com/vanroute/backend/controllers/DriverController.java#L99) | [DriverService.setPixKey](../backend/src/main/java/br/com/vanroute/backend/services/DriverService.java#L80) | valida e normaliza com [PixKeys.normalize](../backend/src/main/java/br/com/vanroute/backend/utils/PixKeys.java#L20) e grava nas colunas `pix_key_type` e `pix_key` |
| `DELETE /api/driver/pix-key` | [DriverController.removePixKey](../backend/src/main/java/br/com/vanroute/backend/controllers/DriverController.java#L104) | [DriverService.removePixKey](../backend/src/main/java/br/com/vanroute/backend/services/DriverService.java#L89) | limpa as duas colunas |
| `GET /api/payments` | [PaymentController.list](../backend/src/main/java/br/com/vanroute/backend/controllers/PaymentController.java#L39) | [PaymentService.list](../backend/src/main/java/br/com/vanroute/backend/services/PaymentService.java#L62) | monta a consulta só com os filtros que vieram, pagina e troca `PENDING` vencida por `OVERDUE` ([withDisplayStatus](../backend/src/main/java/br/com/vanroute/backend/services/PaymentService.java#L101)) |
| `PUT /api/payments/{id}/paid` | [PaymentController.markPaid](../backend/src/main/java/br/com/vanroute/backend/controllers/PaymentController.java#L54) | [PaymentService.markPaid](../backend/src/main/java/br/com/vanroute/backend/services/PaymentService.java#L107) | confere dono e status ([pendingOfDriver](../backend/src/main/java/br/com/vanroute/backend/services/PaymentService.java#L138)), grava `PAID`, método e `paid_at`, e notifica o pai |
| `PUT /api/payments/{id}/cancel` | [PaymentController.cancel](../backend/src/main/java/br/com/vanroute/backend/controllers/PaymentController.java#L61) | [PaymentService.cancel](../backend/src/main/java/br/com/vanroute/backend/services/PaymentService.java#L126) | mesma conferência, grava `CANCELED` e notifica o pai |

**Onde as parcelas nascem:** no aceite do contrato, [ContractService.acceptContract](../backend/src/main/java/br/com/vanroute/backend/services/ContractService.java#L184), com as datas de [ContractService.installmentDates](../backend/src/main/java/br/com/vanroute/backend/services/ContractService.java#L250).

**Notificação:** [NotificationService.send](../backend/src/main/java/br/com/vanroute/backend/services/NotificationService.java#L50) grava a notificação e manda pelo WebSocket `/notifications` depois do commit.

**Banco e modelos:**
- [V30__payments_due_date_paid_at.sql](../backend/src/main/resources/db/migration/V30__payments_due_date_paid_at.sql): `payment_date` vira `due_date` e entra `paid_at`.
- [V31__contract_cancellations_and_pix_key.sql](../backend/src/main/resources/db/migration/V31__contract_cancellations_and_pix_key.sql): colunas `pix_key_type` e `pix_key` em `drivers`.
- Modelos: [Payment.java](../backend/src/main/java/br/com/vanroute/backend/models/contract/Payment.java#L1), [Driver.java](../backend/src/main/java/br/com/vanroute/backend/models/user/Driver.java#L1), [PixKeyType.java](../backend/src/main/java/br/com/vanroute/backend/models/user/enums/PixKeyType.java#L1).
- DTOs: [PaymentResponseDTO.java](../backend/src/main/java/br/com/vanroute/backend/dtos/payment/PaymentResponseDTO.java#L1), [MarkPaidRequestDTO.java](../backend/src/main/java/br/com/vanroute/backend/dtos/payment/MarkPaidRequestDTO.java#L1), [PixKeyDTO.java](../backend/src/main/java/br/com/vanroute/backend/dtos/user/PixKeyDTO.java#L1).
- Consultas: [PaymentRepository](../backend/src/main/java/br/com/vanroute/backend/repositories/PaymentRepository.java#L19) (atrasadas por contrato).

**Testes:** [PaymentServiceTest](../backend/src/test/java/br/com/vanroute/backend/services/PaymentServiceTest.java), [PixKeysTest](../backend/src/test/java/br/com/vanroute/backend/utils/PixKeysTest.java) e as seções 13f e 13g de [run-api-tests.sh](../backend/api-tests/run-api-tests.sh).
