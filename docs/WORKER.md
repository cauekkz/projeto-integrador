# Worker diário (Go)

Programa separado do backend, em `worker/`, que roda as tarefas de manutenção do banco. Ele fica de fora do backend de propósito: se um dia o backend tiver várias instâncias, uma tarefa agendada dentro dele rodaria uma vez em cada uma. O worker roda numa instância só (e, se por engano subirem duas, as tarefas são idempotentes e travam as linhas que mexem: nada é feito duas vezes).

## Quando roda

Ao subir e depois **todo dia às `WORKER_RUN_AT`** (padrão `00:05`, horário de São Paulo): logo depois da virada do dia, antes das viagens da manhã. O próximo horário é calculado em [NextRun](../worker/internal/jobs/jobs.go#L36). Pra testar, `WORKER_INTERVAL=1m` troca o horário fixo por um intervalo ([scheduler](../worker/cmd/main.go#L48)).

Cada tarefa roda numa **transação própria**: se der erro no meio, nada dela fica gravado, as outras rodam mesmo assim e ela tenta de novo no próximo ciclo ([runJob](../worker/cmd/main.go#L85)). Se o worker ficar parado alguns dias, o primeiro ciclo pega tudo o que ficou pra trás (as consultas usam "antes de hoje", não "ontem").

**"Hoje" é sempre o dia em São Paulo**, calculado no Go ([jobs.go](../worker/internal/jobs/jobs.go#L14)) e passado pra query. Nunca use `CURRENT_DATE`/`NOW()` do banco: ele roda em UTC, e das 21h à meia-noite já é o dia seguinte lá. Os horários gravados (notificações, mensagens) também são de São Paulo, igual ao backend.

## As tarefas (nessa ordem)

| Tarefa | O que faz | Código |
|---|---|---|
| Usuários sem e-mail confirmado | apaga quem está `CHECK_EMAIL` há mais de 48h (motorista/responsável saem junto, em cascata) | [CleanupUnverifiedUsers](../worker/internal/jobs/users.go#L10) |
| Viagens esquecidas abertas | viagem `STARTED`/`IN_PROGRESS`/`PAUSED` aberta **há mais de 20 horas** (conta da data + hora de início, não "dia anterior": a que começou 23:50 e ainda roda às 00:05 continua) vira `FINISHED`, sem horário de fim (`end_time` nulo = fechada pelo sistema), e o motorista recebe `SYSTEM` "Viagem fechada automaticamente". Sem isso ela contaria como "van rodando" pra sempre | [CloseStaleTrips](../worker/internal/jobs/trips.go#L13) |
| Propostas expiradas | proposta `PENDING` com data de início **antes de hoje** vira `CANCELED`, o documento `REJECTED`, entra a mensagem `REJECTION` "Proposta Expirada" no chat (o card fecha) e os dois recebem `CONTRACT_EXPIRED`. Fica no histórico | [ExpireProposals](../worker/internal/jobs/proposals.go#L15) |
| Cancelamentos no fim do aviso prévio | pedido `PENDING` com `effective_date` **até hoje**, de contrato `SCHEDULED`/`ACTIVE`: o contrato vira `CANCELED`, o pedido `COMPLETED`, as parcelas que vencem **depois** do fim do aviso viram `CANCELED` (as anteriores continuam devidas) e o aluno sai das rotas e grupos ([releaseStudent](../worker/internal/jobs/contracts.go#L20)); os dois recebem `CONTRACT_CANCELED`. **Com a van rodando com o aluno (qualquer viagem aberta, inclusive a que começou antes da meia-noite), espera o próximo ciclo** | [CancelAfterNotice](../worker/internal/jobs/contracts.go#L117) |
| Contratos que começam hoje | contrato `SCHEDULED` com data de início **até hoje** vira `ACTIVE` (só então o aluno pode entrar nas rotas e viagens); os dois recebem `CONTRACT_STARTED` | [ActivateContracts](../worker/internal/jobs/contracts.go#L170) |
| Contratos encerrados | contrato `ACTIVE` com data de fim **antes de hoje** vira `INACTIVE`; o aluno sai dos pontos das rotas do motorista, das faltas futuras e dos grupos dele ([releaseStudent](../worker/internal/jobs/contracts.go#L20)); parcela futura (se existir) vira `CANCELED`; pedido de cancelamento em andamento vira `WITHDRAWN`; os dois recebem `CONTRACT_ENDED`. **Com a van rodando com o aluno (qualquer viagem aberta, inclusive a que começou antes da meia-noite), espera o próximo ciclo** | [EndContracts](../worker/internal/jobs/contracts.go#L58) |
| Parcelas atrasadas | parcela `PENDING` com vencimento antes de hoje vira `OVERDUE` e o responsável recebe `PAYMENT` "Parcela atrasada" (uma vez só) | [MarkOverduePayments](../worker/internal/jobs/payments.go#L15) |

Por que essa ordem:
- as viagens esquecidas fecham antes de mexer em contratos, porque uma viagem aberta segura o aluno na rota;
- o aviso prévio vence antes de o contrato agendado começar, pra não avisar "começou" de um contrato que acabou de ser cancelado;

**Renovação:** [releaseStudent](../worker/internal/jobs/contracts.go#L20) **não** tira o aluno das rotas e grupos se ele tiver outro contrato aceito (`SCHEDULED` ou `ACTIVE`) com o mesmo motorista.

As notificações são gravadas igual às do backend ([notify](../worker/internal/jobs/jobs.go#L65)), mas **não chegam pelo WebSocket** (o worker não tem a conexão): aparecem no próximo `GET /api/notifications`.

## Regra que existe nos dois lados

A limpeza de **cancelamento e encerramento** ([releaseStudent](../worker/internal/jobs/contracts.go#L20) e a checagem de "van rodando", [noTripRunning](../worker/internal/jobs/contracts.go#L12)) é a mesma do cancelamento no backend, [ContractCancellationService.cancel](../backend/src/main/java/br/com/vanroute/backend/services/ContractCancellationService.java#L185). Se uma mudar (ex.: tabela nova ligada ao aluno na rota), **mude a outra**.

O mesmo vale pra "atrasada": no backend é [PaymentRepository.OVERDUE](../backend/src/main/java/br/com/vanroute/backend/repositories/PaymentRepository.java#L22), que conta tanto `OVERDUE` quanto `PENDING` vencida (a que o worker ainda não marcou).

O backend também:
- não espera o worker para tirar da viagem o aluno cujo contrato acabou ou ainda não começou: [TripService.start](../backend/src/main/java/br/com/vanroute/backend/services/TripService.java#L85) só inclui quem tem contrato `ACTIVE` com o motorista.

**Concorrência:** as mudanças de status no backend leem o contrato e a parcela com `SELECT ... FOR UPDATE`, e o worker também trava as linhas que vai mudar (`FOR UPDATE`). Se os dois mexem no mesmo contrato ao mesmo tempo, um espera o outro, e o segundo já vê o status novo.

## Rodar

`DATABASE_PASSWORD` é **obrigatória** (sem valor padrão, igual ao backend). As outras têm padrão:
- `DATABASE_HOST` (`localhost`), `DATABASE_PORT` (`5432`), `DATABASE_NAME` (`vanroute_db`) e `DATABASE_USERNAME` (`postgres`);
- `DATABASE_SSLMODE` (`prefer`; em produção com o banco em outra máquina, `require`).

A senha vai escapada no endereço de conexão ([config.go](../worker/internal/config/config.go#L15)).

```bash
cd worker
DATABASE_PASSWORD=123456 go run ./cmd                      # senha do docker-compose de desenvolvimento
DATABASE_PASSWORD=123456 WORKER_INTERVAL=1m go run ./cmd   # ciclo a cada minuto, pra testar
DATABASE_PASSWORD=123456 WORKER_RUN_AT=03:00 go run ./cmd  # outro horario fixo
```

## Docker

O [Dockerfile](../worker/Dockerfile) tem dois estágios: o primeiro usa uma imagem **com Go** só pra compilar; o segundo copia **só o binário** pra uma imagem mínima (distroless, ~20 MB), sem Go, sem código-fonte e sem shell. A máquina que roda o container não precisa de Go nem do código.

```bash
docker compose --profile worker up -d --build worker   # sobe junto com o banco
docker logs -f vanroute-worker                          # cada ciclo loga o que mudou e quando e o proximo
```

Ele fica num **perfil** (`worker`): o `docker compose up` normal e o CI continuam subindo só banco, Redis e MinIO. Em produção, passe `DATABASE_PASSWORD` (e `DATABASE_SSLMODE`) pelo `.env` do servidor.

## Testes

```bash
cd worker
DATABASE_PASSWORD=123456 go test ./...
```

Rodam contra o PostgreSQL de verdade (o mesmo do backend, depois das migrations), cada teste numa transação **desfeita no fim**: nada fica gravado. Sem banco no ar (ou sem `DATABASE_PASSWORD`), são pulados; no CI falham. Cobrem:
- o dia em São Paulo quando em UTC já é amanhã, e o próximo horário do ciclo;
- contrato encerrado: sai da rota, dos grupos e das faltas; a parcela paga fica; espera com a van rodando; o que termina hoje continua; com a renovação aceita, o aluno fica;
- viagem esquecida: só fecha depois de 20 horas; a que passa da meia-noite não fecha e segura o aluno na rota;
- fim do aviso prévio: cancela, as parcelas vencidas continuam devidas, espera a viagem de hoje, e o que termina amanhã continua;
- contrato agendado ativado no dia;
- proposta expirada (a que começa hoje continua);
- parcela atrasada avisada uma vez só (a paga não muda);
- limpeza de usuários (48h);
- a senha obrigatória e escapada na conexão.

Código: [jobs_test.go](../worker/internal/jobs/jobs_test.go) e [config_test.go](../worker/internal/config/config_test.go).
