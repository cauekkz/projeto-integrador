# Pendências do backend

O que ainda falta, tirado do código e do banco atuais (V32). Decisões em aberto e colunas pouco usadas estão em `docs/SUGESTOES_FUTURAS.md`.

---

## 1. Funcionalidades que o banco já prevê, mas o código não faz

### 1.1 Pagamento
Feito: lista de parcelas com filtros, chave Pix do motorista e baixa manual (`docs/FLUXO_PAGAMENTO.md`). Falta:
Feito também: o worker diário grava `OVERDUE` e avisa o pai, e `responsibles.financial_status` passou a valer (`UP_TO_DATE`/`OVERDUE`, `docs/WORKER.md`). Falta:
- Cobrança automática (Mercado Pago): só depois de validar o Pix manual.
- `responsibles.address_id` (endereço de cobrança) só faz sentido com cobrança por gateway.

### 1.2 Ciclo de vida do contrato
Feito: validação da proposta, retirada pelo motorista e cancelamento com pedido e aprovação (`docs/FLUXO_CONTRATO.md`, passo 9). Falta:
Feito também: o worker diário marca `INACTIVE` o contrato que passou da `endDate` (o aluno sai das rotas e grupos) e cancela a proposta não respondida até o início. Falta:
- Rota para editar uma proposta (hoje o motorista retira e manda outra).
- `SUSPENDED` e `OVERDUE` do contrato não são usados.

### 1.3 Documentos do motorista (fora por decisão, por enquanto)
- A CNH (cadastro) e o CRLV (van) são **validados** (assinatura ICP-Brasil), mas o PDF **não é guardado**. Decisão: não guardar por enquanto.
- No `DocumentType` e no `DocumentEntityType`, os valores de documento do motorista, da van etc. estão **comentados**; só o contrato (`TRANSPORT_CONTRACT`/`CONTRACT`) é usado. Pra voltar: descomentar e criar as rotas (curso de condutor de escolares, autorização do município, termo de responsabilidade).
- `drivers.approval_status` fica sempre `APPROVED`: não existe aprovação ou suspensão de motorista.

### 1.4 Vínculo do responsável
- `student_responsibles.relation_type` (`FINANCIAL`, `LEGAL`, `WITHDRAWAL`) é gravado e serve de filtro, mas **nenhuma regra usa**. Exemplos possíveis: só quem é `WITHDRAWAL` pode receber a criança; só `FINANCIAL` recebe a cobrança.

### 1.5 Conta do usuário
Feito: "excluir minha conta" (`DELETE /api/users/me`, ver `docs/FLUXO_FRONT.md` seção 12) e status sem uso comentados (`SUSPENDED`/`BLOCKED` do usuário, `SUSPENDED`/`OVERDUE` do contrato, `PENDING`/`DEFAULTING`/`BLOCKED` do financeiro e todos menos `APPROVED` do motorista). `INACTIVE` do usuário agora é a conta excluída.

---

## 2. LGPD
Feito no backend: termos versionados (`GET /api/terms`, texto em `resources/terms/v1.md`, **rascunho a revisar**), registro do aceite (`POST /api/terms/accept`, tabela `terms_acceptances`; o `/api/users/me` diz `termsAccepted`), exportar os dados (`GET /api/users/me/export`, 1 a cada 5 minutos) e excluir a conta (`DELETE /api/users/me`). Falta:
- Revisar o texto dos termos e pôr o e-mail de contato.
- Front: mostrar os termos no cadastro e no login quando `termsAccepted` for `false`.
- Exigir o aceite **no cadastro** (hoje é depois do login, pra não quebrar o cadastro do front atual).

---

## 3. Antes de ir para produção
- **Segredos:** feito. `JWT_KEY`, `DATABASE_PASSWORD`, `MINIO_ACCESS_KEY` e `MINIO_SECRET_KEY` não têm mais valor padrão no `application.yml`: em produção vêm das variáveis de ambiente (sem elas o backend não sobe); em desenvolvimento, do `application-local.properties`.
- **Origens:** defina `ALLOWED_ORIGINS` com o domínio do front (vale pro CORS e pros WebSockets). O padrão aceita só localhost e rede local.
- **Limite de requisições:** na aplicação já tem (código de e-mail: 5 tentativas e 1 reenvio por minuto). Falta o limite por IP no servidor web (Nginx `limit_req`) quando hospedar, principalmente no login e no cadastro.
- **Push com o app fechado** (Firebase Cloud Messaging): hoje a notificação só aparece com o app aberto.
- **HTTPS, backup do banco, logs e monitoramento.**
- **Serviços pagos:** a chave grátis do OpenRouteService dá 200 rotas por dia; o e-mail precisa de um provedor de verdade.
- **Worker:** subir o `worker/` junto (uma instância só) com as mesmas variáveis `DATABASE_*`.

---

## 4. Avisos para o time
- **Migration que já rodou não se edita nem se renumera.** Mudança de banco sempre numa migration nova, com o próximo número (a próxima livre é a **V34**).
- Banco local com erro do Flyway: zere com `docker compose down -v` (ou `DROP DATABASE vanroute_db; CREATE DATABASE vanroute_db;`) e suba o backend; as migrations rodam do zero. Dados de exemplo: `backend/api-tests/demo/seed.sh`.
- Depois de puxar a branch, rode `./mvnw clean test` (classes apagadas podem ficar no `target/`).
- **Arquivo local obrigatório:** copie `backend/src/main/resources/application-local.properties.example` para `application-local.properties` (fica fora do git) e preencha. Sem ele o backend não sobe, porque a senha do banco, a `JWT_KEY` e as chaves do MinIO não têm mais valor padrão.
- **Perfis nas rotas** (`SecurityConfig`): rota só de motorista chamada por responsável (ou o contrário) dá **403** antes de chegar no service.
- **Documentos reais nunca vão pro git** (CNH, CRLV): o `.gitignore` bloqueia. Os testes de assinatura ICP usam `backend/src/test/resources/documents/validCNH.pdf` e `invalidCNH.pdf`, que cada um coloca na própria máquina; sem eles, esses 2 testes são pulados (no Actions também).
