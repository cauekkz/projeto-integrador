# Pendências do backend

O que ainda falta, tirado do código e do banco atuais (V29). Decisões em aberto e colunas pouco usadas estão em `docs/SUGESTOES_FUTURAS.md`.

---

## 1. Funcionalidades que o banco já prevê, mas o código não faz

### 1.1 Pagamento
- O aceite do contrato gera as parcelas em `payments` (status `PENDING`), mas **nenhuma rota lê essas parcelas**: o pai e o motorista não as veem.
- Ninguém cobra, dá baixa (`PAID`) ou marca atraso (`OVERDUE`). `payment_method` (`PIX`, `CREDIT_CARD`, `BOLETO`, `CASH`) nunca é preenchido.
- `responsibles.financial_status` fica sempre `PENDING`: decidir junto com o pagamento.
- A cobrança deve usar o endereço de cobrança (`responsibles.address_id`).

### 1.2 Ciclo de vida do contrato
- Contrato vencido continua `ACTIVE` para sempre: falta marcar `INACTIVE` depois da `endDate` (um job diário).
- Não existe rota para **cancelar um contrato ativo**, nem para editar uma proposta.
- `SUSPENDED` e `OVERDUE` existem no enum, mas nada os usa (dependem do pagamento).

### 1.3 Documentos do motorista
- A CNH (cadastro) e o CRLV (van) são **validados** (assinatura ICP-Brasil), mas o PDF **não é guardado** em `documents`.
- O enum `DocumentType` prevê documentos que nenhuma rota recebe:
  - `SCHOOL_TRANSPORT_COURSE`: o curso de condutor de escolares, que o CTB exige do motorista de van escolar;
  - `SCHOOL_AUTHORIZATION`: a autorização do município/DETRAN para transporte escolar;
  - `LIABILITY_TERM`: o termo de responsabilidade.
- `drivers.approval_status` fica sempre `APPROVED`: não existe aprovação ou suspensão de motorista.

### 1.4 Vínculo do responsável
- `student_responsibles.relation_type` (`FINANCIAL`, `LEGAL`, `WITHDRAWAL`) é gravado e serve de filtro, mas **nenhuma regra usa**. Exemplos possíveis: só quem é `WITHDRAWAL` pode receber a criança; só `FINANCIAL` recebe a cobrança.

### 1.5 Conta do usuário
- `users.status` só usa `CHECK_EMAIL` e `ACTIVE`. `INACTIVE`, `SUSPENDED` e `BLOCKED` não têm rota nem regra.
- Não existe "excluir minha conta" para o próprio usuário (só o `DELETE /api/users/**` de admin). A LGPD pede isso.

---

## 2. LGPD
- Termos de Uso e Política de Privacidade (dados de crianças, CPF e localização em tempo real).
- Registrar o aceite no cadastro: quem aceitou, qual versão e quando.
- O usuário poder exportar e apagar os próprios dados.

---

## 3. Antes de ir para produção
- **Segredos:** `JWT_KEY`, `DATABASE_PASSWORD`, `MINIO_ACCESS_KEY` e `MINIO_SECRET_KEY` só por variável de ambiente. Os valores padrão do `application.yml` são de desenvolvimento.
- **Limite de requisições** no login, no cadastro e no envio de código por e-mail.
- **CI** com o GitHub Actions (testes unitários em todo PR).
- **Push com o app fechado** (Firebase Cloud Messaging): hoje a notificação só aparece com o app aberto.
- **HTTPS, backup do banco, logs e monitoramento.**
- **Serviços pagos:** a chave grátis do OpenRouteService dá 200 rotas por dia; o e-mail precisa de um provedor de verdade.
- **Limpeza:** espaços no fim de `spring.profiles.active=local` no `application.properties`.

---

## 4. Avisos para o time
- **Migration que já rodou não se edita nem se renumera.** Mudança de banco sempre numa migration nova, com o próximo número (a próxima livre é a **V30**).
- Banco local com erro do Flyway: zere com `docker compose down -v` (ou `DROP DATABASE vanroute_db; CREATE DATABASE vanroute_db;`) e suba o backend; as migrations rodam do zero. Dados de exemplo: `backend/api-tests/demo/seed.sh`.
- Depois de puxar a branch, rode `./mvnw clean test` (classes apagadas podem ficar no `target/`).
