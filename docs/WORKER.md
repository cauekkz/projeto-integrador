# Worker diário (Go)

Programa separado do backend, em `worker/`, que roda as tarefas de manutenção do banco. Ele fica de fora do backend de propósito: se um dia o backend tiver várias instâncias, uma tarefa agendada dentro dele rodaria uma vez em cada uma. O worker roda numa instância só.

## Quando roda

Ao subir e depois a cada `WORKER_INTERVAL` (padrão `24h`). Cada tarefa roda numa **transação própria**: se der erro no meio, nada dela fica gravado, as outras rodam mesmo assim e ela tenta de novo no próximo ciclo ([main.go](../worker/cmd/main.go#L67)).

**"Hoje" é sempre o dia em São Paulo**, calculado no Go ([jobs.go](../worker/internal/jobs/jobs.go#L14)) e passado pra query. Nunca use `CURRENT_DATE`/`NOW()` do banco: ele roda em UTC, e das 21h à meia-noite já é o dia seguinte lá.

## As tarefas (nessa ordem)

| Tarefa | O que faz | Código |
|---|---|---|
| Usuários sem e-mail confirmado | apaga quem está `CHECK_EMAIL` há mais de 48h (motorista/responsável saem junto, em cascata) | [CleanupUnverifiedUsers](../worker/internal/jobs/users.go#L10) |
| Propostas expiradas | proposta `PENDING` com data de início **antes de hoje** vira `CANCELED`, o documento `REJECTED`, entra a mensagem `REJECTION` "Proposta Expirada" no chat (o card fecha) e os dois recebem `CONTRACT_EXPIRED`. Fica no histórico | [ExpireProposals](../worker/internal/jobs/proposals.go#L15) |
| Contratos encerrados | contrato `ACTIVE` com data de fim **antes de hoje** vira `INACTIVE`; o aluno sai dos pontos das rotas do motorista, das faltas futuras e dos grupos dele; parcela futura (se existir) vira `CANCELED`; pedido de cancelamento sem resposta vira `WITHDRAWN`; os dois recebem `CONTRACT_ENDED`. **Com a van rodando com o aluno, espera o próximo ciclo** | [EndContracts](../worker/internal/jobs/contracts.go#L17) |
| Parcelas atrasadas | parcela `PENDING` com vencimento antes de hoje vira `OVERDUE` e o responsável recebe `PAYMENT` "Parcela atrasada" (uma vez só) | [MarkOverduePayments](../worker/internal/jobs/payments.go#L15) |
| Situação financeira | `OVERDUE` pra quem tem parcela atrasada, `UP_TO_DATE` pra quem não tem | [RefreshFinancialStatus](../worker/internal/jobs/payments.go#L45) |

As notificações são gravadas igual às do backend ([notify](../worker/internal/jobs/jobs.go#L51)), mas **não chegam pelo WebSocket** (o worker não tem a conexão): aparecem no próximo `GET /api/notifications`.

## Regra que existe nos dois lados

A limpeza de **"Contratos encerrados"** é a mesma do cancelamento no backend, [ContractCancellationService.cancel](../backend/src/main/java/br/com/vanroute/backend/services/ContractCancellationService.java#L186). Se uma mudar (ex.: tabela nova ligada ao aluno na rota), **mude a outra**. O mesmo vale pra "atrasada": no backend é [PaymentRepository.OVERDUE](../backend/src/main/java/br/com/vanroute/backend/repositories/PaymentRepository.java#L19), que conta tanto `OVERDUE` quanto `PENDING` vencida (a que o worker ainda não marcou).

O backend também atualiza a situação financeira **na hora** quando o motorista dá baixa ou dispensa uma parcela ([PaymentService.refreshFinancialStatus](../backend/src/main/java/br/com/vanroute/backend/services/PaymentService.java#L152)); o worker pega quem ficou atrasado de um dia pro outro.

## Rodar

```bash
cd worker
go run ./cmd                      # usa DATABASE_HOST/PORT/NAME/USERNAME/PASSWORD (padrão: o banco local do docker compose)
WORKER_INTERVAL=1m go run ./cmd   # ciclo a cada minuto, pra testar
```

## Docker

O [Dockerfile](../worker/Dockerfile) tem dois estágios: o primeiro usa uma imagem **com Go** só pra compilar; o segundo copia **só o binário** pra uma imagem mínima (distroless, ~20 MB), sem Go, sem código-fonte e sem shell. A máquina que roda o container não precisa de Go nem do código.

```bash
docker compose --profile worker up -d --build worker   # sobe junto com o banco
docker logs -f vanroute-worker                          # cada ciclo loga o que mudou
```

Ele fica num **perfil** (`worker`): o `docker compose up` normal e o CI continuam subindo só banco, Redis e MinIO.

## Testes

```bash
cd worker
go test ./...
```

Rodam contra o PostgreSQL de verdade (o mesmo do backend), cada teste numa transação **desfeita no fim**: nada fica gravado. Sem banco no ar, são pulados. Cobrem: o dia em São Paulo quando em UTC já é amanhã; contrato encerrado (sai da rota, grupos e faltas; parcela paga fica; espera com a van rodando; o que termina hoje continua); proposta expirada (a que começa hoje continua); parcela atrasada avisada uma vez só; situação financeira; e a limpeza de usuários (48h). Código: [jobs_test.go](../worker/internal/jobs/jobs_test.go).
