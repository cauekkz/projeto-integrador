# Pendências do backend

O que ainda falta, tirado do código e do banco atuais (até a **V37**). Decisões em aberto e colunas pouco usadas estão em `docs/SUGESTOES_FUTURAS.md`.

---

## 1. Funcionalidades que ainda faltam

### 1.1 Pagamento
Feito:
- lista de parcelas com filtros, chave Pix do motorista e baixa manual, com trava contra duplo clique (`docs/FLUXO_PAGAMENTO.md`);
- o worker diário grava `OVERDUE` e avisa o pai;
- a situação financeira do responsável é calculada na hora pelas parcelas (a coluna `responsibles.financial_status` saiu na V36);

Falta:
- Cobrança automática (Mercado Pago, issue #192): só depois de validar o Pix manual. Sem ela, a baixa continua sendo só a palavra do motorista: o pai avisa pelo chat que pagou e não há comprovante nem contestação no app.
- `responsibles.address_id` (endereço de cobrança) só faz sentido com cobrança por gateway.

### 1.2 Ciclo de vida do contrato
Feito (`docs/FLUXO_CONTRATO.md`):
- validação da proposta e PDF congelado em `contracts/` (só PDF, até 10 MB);
- retirada pelo motorista;
- contrato `SCHEDULED` até a data de início e renovação (no máximo o contrato atual e um próximo, sem sobrepor período);
- cancelamento com **aviso prévio de 30 dias** (a outra parte pode aceitar antes; o motorista cancela na hora com parcela vencida há 30+ dias);
- trava contra duplo clique (`FOR UPDATE` e `UNIQUE` nas parcelas).

O worker encerra (`INACTIVE`), ativa os agendados, aplica o fim do aviso, expira propostas e fecha viagens esquecidas abertas.

Falta:
- Rota para editar uma proposta (hoje o motorista retira e manda outra).
- Multa e outras cláusulas do PDF ficam fora do app (decisão).

### 1.3 Motorista: o que o app confere e o que não confere
- **CNH no cadastro** ([CnhParser](../backend/src/main/java/br/com/vanroute/backend/services/CnhParser.java)):
  - confere a assinatura ICP-Brasil;
  - lê do OCR o CPF (com dígito verificador), o nome, o nascimento e a validade (da zona de leitura de máquina, com dígito verificador) e a categoria;
  - grava o nome e o CPF lidos da CNH (sem campo de nome no cadastro); exige que a CNH esteja válida, que seja categoria **D ou E** e que o motorista tenha **21 anos ou mais**.
- **CRLV:** confere a assinatura e recusa licenciamento de mais de um ano atrás.
- **Os PDFs não são guardados.**
- **Falta (não está na CNH nem no CRLV):** curso de condutor de escolares, autorização do município para transporte escolar, vistoria semestral da van e seguro. Para isso é preciso voltar os tipos de documento do motorista e da van (`DocumentType`/`DocumentEntityType`, hoje comentados) e criar um fluxo de aprovação.
- `drivers.approval_status` fica sempre `APPROVED`: não existe aprovação ou suspensão de motorista por um admin.

### 1.4 Vínculo do responsável
- Feito: o admin vê, remove e passa o admin (`GET/DELETE /api/student/{id}/responsibles/...`); qualquer responsável sai sozinho, menos o admin; ninguém sai com contrato ou proposta em andamento com o aluno.
- `student_responsibles.relation_type` (`FINANCIAL`, `LEGAL`, `WITHDRAWAL`) é **só um rótulo** (decisão): os outros responsáveis acompanham o aluno, e quem paga e vê as parcelas é quem assinou o contrato.
- Não há como provar a guarda da criança no cadastro do aluno: quem cadastra declara ser responsável (termos, item 5).

### 1.5 Conta do usuário
Feito:
- "Excluir minha conta" (`DELETE /api/users/me`): apaga os dados pessoais e os arquivos enviados e fecha os WebSockets abertos.
- Status sem uso comentados. `INACTIVE` do usuário é a conta excluída.

---

## 2. LGPD
Feito no backend:
- **Termos versionados**, hoje na **versão 3** ([v3.md](../backend/src/main/resources/terms/v3.md); a v3 incluiu a foto do aluno e a leitura das mensagens do chat), escrita a partir do que o código faz: dados coletados, quem vê o quê, operadores (OpenRouteService na Alemanha, Gmail), retenção e exclusão.
- **Aceite obrigatório no cadastro** e **de novo a cada versão nova**: até aceitar, a API responde **403** `TERMS_NOT_ACCEPTED`, menos ver e aceitar os termos, ver a conta, sair, exportar e excluir.
- **Exportar os dados** (`GET /api/users/me/export`, uma vez a cada 5 minutos). Inclui viagens e embarques dos filhos, ocorrências, documento, assinaturas e cancelamentos dos contratos, e as avaliações recebidas.
- **Excluir a conta.**
- O motorista vê a inadimplência do pai **só com ele mesmo**.

Falta:
- Identificar o controlador (razão social/CNPJ) e o encarregado quando houver empresa. Hoje o contato é `cauevaccari2007@gmail.com`.
- Prazo de retenção do histórico (contratos, viagens, mensagens): hoje fica enquanto a outra parte tiver conta.

---

## 3. Antes de ir para produção
Problemas de publicação do front (Nginx, links do MinIO, mapa e HTTPS): [`DEPLOY.md`](DEPLOY.md).

- **Perfil:** suba com `SPRING_PROFILES_ACTIVE=prod` (sem o `application-local.*`).
  - Assim o Swagger (`/swagger-ui`, `/v3/api-docs`) e o log de SQL ficam **desligados**: eles só ligam no perfil `local` ([application-local.yml](../backend/src/main/resources/application-local.yml)).
  - O perfil `local` é o padrão do `application.properties`, por isso no desenvolvimento tudo continua igual.
- **Segredos:**
  - `DATABASE_PASSWORD`, `JWT_KEY`, `MINIO_ACCESS_KEY` e `MINIO_SECRET_KEY` não têm valor padrão: vêm das variáveis de ambiente (Docker, CI, servidor) ou do `.env` da raiz. Sem elas o backend não sobe.
  - O worker também exige `DATABASE_PASSWORD` (e `DATABASE_SSLMODE=require` com o banco em outra máquina).
- **Senhas:** Argon2id (parâmetros mínimos da OWASP).
- **Login:**
  - 5 senhas erradas no mesmo CPF, ou 30 no mesmo IP, bloqueiam por 15 minutos.
  - Atrás do Nginx, o IP vem do `X-Forwarded-For` (`server.forward-headers-strategy: framework`), então o proxy precisa mandar esse cabeçalho.
  - Ainda vale um `limit_req` no Nginx para o cadastro.
- **Fuso:** o backend fixa a JVM em `America/Sao_Paulo` ([BackendApplication](../backend/src/main/java/br/com/vanroute/backend/BackendApplication.java)) e o worker grava no mesmo fuso. As colunas não têm fuso: não mude um dos dois sem o outro.
- **Origens:** defina `ALLOWED_ORIGINS` com o domínio do front (vale pro CORS e pros WebSockets).
- **WebSocket:** o token vai na URL (`?token=`), porque o navegador não manda cabeçalho no WebSocket. Configure o proxy para **não** logar a query string dessas rotas.
- **Push com o app fechado** (Firebase Cloud Messaging, issue #150): hoje a notificação só aparece com o app aberto. Para "a van chegou", isso é essencial.
- **HTTPS, backup do banco, logs e monitoramento** (issue #187). O Docker de tudo já existe (`docs/DOCKER.md`).
- **Serviços pagos (decidido):**
  - Fica no plano grátis do OpenRouteService e no Gmail (cerca de 500 e-mails por dia).
  - Reavaliar quando hospedar: um provedor de e-mail transacional e um plano pago de mapas.
- **Worker:**
  - Suba o `worker/` junto, numa instância só, com as mesmas variáveis `DATABASE_*`.
  - Ele roda todo dia às 00:05 (`WORKER_RUN_AT`).

---

## 4. Avisos para o time
- **Migration que já rodou não se edita nem se renumera.** Mudança de banco sempre numa migration nova, com o próximo número (a próxima livre é a **V38**).
- Banco local com erro do Flyway: zere com `docker compose down -v` (ou `DROP DATABASE vanroute_db; CREATE DATABASE vanroute_db;`) e suba o backend; as migrations rodam do zero. Dados de exemplo: `backend/api-tests/demo/seed.sh`.
- Depois de puxar a branch, rode `./mvnw clean test` (classes apagadas podem ficar no `target/`).
- **Arquivo local obrigatório:** copie o `.env.example` da raiz para `.env` (fica fora do git) e preencha. Ele vale para o Docker e para o backend rodando na máquina; sem ele o backend não sobe.
- **Termos v3:** quem só aceitou uma versão anterior (contas do banco local antigo) precisa aceitar de novo no app, ou a API responde 403 `TERMS_NOT_ACCEPTED`. O seed já cria tudo com o aceite da versão atual.
- **Perfis nas rotas** (`SecurityConfig`): rota só de motorista chamada por responsável (ou o contrário) dá **403** antes de chegar no service.
- **Documentos reais nunca vão pro git** (CNH, CRLV): o `.gitignore` bloqueia. Os testes de assinatura ICP usam `backend/src/test/resources/documents/validCNH.pdf` e `invalidCNH.pdf`, que cada um coloca na própria máquina; sem eles, esses 2 testes são pulados (no Actions também). O teste da leitura da CNH ([CnhParserTest](../backend/src/test/java/br/com/vanroute/backend/services/CnhParserTest.java)) usa texto inventado.
