# VanRoute

> Sistema Inteligente de Gestão e Monitoramento de Transporte Escolar

## 📖 Sobre o Projeto

O **VanRoute** é uma plataforma Full Stack desenvolvida para modernizar a gestão do transporte escolar. O sistema conecta **pais e responsáveis**, **motoristas**, **empresas de transporte** e futuramente **escolas**, oferecendo monitoramento em tempo real, gestão financeira, comunicação instantânea e otimização de rotas.

## 🎯 Objetivos

- Aumentar a segurança dos alunos.
- Permitir acompanhamento da van em tempo real.
- Automatizar processos administrativos e financeiros.
- Melhorar a comunicação entre responsáveis, motoristas e empresas.
- Gerar rotas inteligentes para reduzir tempo e custos.

## 🌎 ODS

O projeto está alinhado à **ODS 11 – Cidades e Comunidades Sustentáveis**, especialmente à meta **11.2**, promovendo transporte seguro, acessível e eficiente.

---

# Funcionalidades

## Área dos Pais

- Localização da van em tempo real
- Histórico de viagens
- Notificações de embarque e desembarque
- Aviso quando a van estiver próxima
- Pagamento de mensalidades
- Histórico financeiro
- Consulta de presença
- Comunicação com motorista
- Recebimento de ocorrências

## Área do Motorista

- Login seguro
- Dashboard operacional
- Dashboard financeiro
- Lista de passageiros
- Confirmação de embarque e desembarque
- Rota inteligente
- Registro de ocorrências
- Comunicação com responsáveis
- Gestão de contratos

## Área Administrativa

- Aprovação de motoristas
- Validação de documentos
- Gestão de empresas
- Gestão de pagamentos
- Avaliações dos responsáveis
- Relatórios
- Suspensão/Bloqueio de motoristas
- Monitoramento das viagens

## Futuramente

- Integração direta com escolas
- Calendário escolar
- Vinculação entre escolas, vans e alunos

---

# 🏗 Arquitetura

```text
Angular
   │
REST API
   │
Spring Boot
   │
PostgreSQL
```

---

# 🛠 Tecnologias

## Backend (`backend/`)

- Java 21 + Spring Boot 4 (Web, Security, Data JPA, WebSocket, Mail, Data Redis)
- JWT (login) e Argon2 (senhas)
- PostgreSQL 15 + Flyway (migrations)
- Redis 7 (códigos de e-mail, refresh token, chat e posição da van ao vivo)
- MinIO / S3 (AWS SDK) para os arquivos
- PDFBox + Tess4J (OCR em português) e Bouncy Castle (assinatura ICP-Brasil da CNH e do CRLV)
- OpenRouteService (endereços e trajetos)
- Maven (wrapper `mvnw`)

## Frontend (`frontend/`)

- Angular 21 (standalone + signals) e TypeScript
- CSS puro com tokens de tema (`--cor-*`), modo claro e escuro
- MapLibre GL + componente de mapa do mapcn (React 19 dentro do Angular), mapas CARTO
- Tailwind CSS 4 (só nas peças do mapa)
- Bootstrap Icons, Glisse (vidro), RxJS, jwt-decode
- Vitest + jsdom nos testes

## Worker (`worker/`)

- Go 1.25 + pgx: tarefas diárias no banco ([`docs/WORKER.md`](docs/WORKER.md))

---

# 📁 Estrutura

```text
projeto-integrador/
├── backend/             API Spring Boot
├── frontend/            app Angular
├── worker/              tarefas diárias em Go
├── docker/              script que cria o banco de testes
├── docker-compose.yml   Postgres, Redis, MinIO (e o worker)
├── scripts/run.bat      sobe tudo no Windows
├── docs/                fluxos, pendências e deploy
└── .env.example         variáveis de produção
```

---

# ⚙️ Como rodar o projeto na sua máquina

Passo a passo do zero até o app abrindo em `http://localhost:4200`. Se seguir na ordem, funciona de primeira.

## Visão geral: o que roda e onde

| Peça | Porta | Como sobe |
|---|---|---|
| **Frontend** (Angular 21) | `4200` | `npm start` dentro de `frontend/` |
| **Backend** (Spring Boot 4, Java 21) | `9090` | `mvnw spring-boot:run` dentro de `backend/` |
| **PostgreSQL 15**: banco `vanroute_db` (e `vanroute_test` dos testes) | `5432` | Docker (`docker compose up -d`) |
| **Redis 7**: códigos de e-mail, refresh token, chat e posição da van ao vivo | `6379` | Docker |
| **MinIO**: arquivos (fotos, CNH, CRLV, PDF dos contratos) | `9000` (API) e `9001` (painel) | Docker |
| **Worker** (Go): tarefas diárias, opcional no desenvolvimento | não usa porta | Docker, perfil `worker` (veja [`docs/WORKER.md`](docs/WORKER.md)) |

O front nunca chama o backend direto: o `ng serve` repassa `/api`, `/ws` (WebSocket) e `/storage` (MinIO) pelo [`frontend/proxy.conf.json`](frontend/proxy.conf.json).

---

## 1. Instale as ferramentas

| Ferramenta | Versão | Onde baixar | Conferir |
|---|---|---|---|
| Git | qualquer recente | https://git-scm.com/downloads | `git --version` |
| JDK (Java) | **21** | https://adoptium.net (Temurin 21) | `java -version` |
| Node.js | **22 ou mais** (vem com o npm) | https://nodejs.org (versão LTS) | `node -v` e `npm -v` |
| Docker Desktop | qualquer recente | https://www.docker.com/products/docker-desktop | `docker compose version` |
| Go (só se for mexer no worker fora do Docker) | 1.25 | https://go.dev/dl | `go version` |

- **Maven não precisa instalar.** O backend tem o wrapper (`mvnw` / `mvnw.cmd`), que baixa o Maven sozinho.
- **Angular CLI não precisa instalar.** Use `npx ng ...` ou os scripts do `npm`.
- **Sem Docker no Windows?** Dá pra instalar o PostgreSQL 15 (https://www.postgresql.org/download), o [Memurai](https://www.memurai.com) no lugar do Redis e o MinIO (https://min.io/download) direto na máquina, com as mesmas portas, usuários e senhas do [`docker-compose.yml`](docker-compose.yml). O Docker é bem mais simples.

---

## 2. Clone o repositório

```bash
git clone https://github.com/cauekkz/projeto-integrador
cd projeto-integrador
```

---

## 3. Suba o banco, o Redis e o MinIO (Docker)

Com o Docker Desktop aberto, na raiz do projeto:

```bash
docker compose up -d
docker compose ps        # os 3 (vanroute-db, vanroute-redis, vanroute-minio) têm que estar "running"
```

O que já vem pronto, sem fazer nada:
- O Postgres cria o banco **`vanroute_db`** (usuário `postgres`, senha `123456`) e o **`vanroute_test`**, dos testes.
- As **tabelas** são criadas pelo backend, com o **Flyway**, quando ele sobe pela primeira vez (`backend/src/main/resources/db/migration`).
- O **bucket** `vanroute-storage` do MinIO também é criado pelo backend ao subir.
- O painel do MinIO fica em http://localhost:9001 (usuário `vanroute`, senha `vanroute123456`).

> Se o volume do Postgres já existia de antes e não tem o `vanroute_test`:
> `docker exec vanroute-db createdb -U postgres vanroute_test`

---

## 4. Pegue as chaves

O backend precisa de 2 chaves de fora (e-mail e mapas) e de 1 texto secreto que você mesmo gera (JWT).

### 4.1 Senha de app do Gmail: e-mail com os códigos de verificação

O cadastro e o "esqueci a senha" mandam um código por e-mail. O envio é pelo Gmail (SMTP `smtp.gmail.com:587`) e usa uma **senha de app**, **não a senha normal da conta**.

1. Use uma conta Gmail só pro projeto (recomendado).
2. Ligue a **verificação em duas etapas**: https://myaccount.google.com/security → "Verificação em duas etapas". Sem ela, o Google não deixa criar senha de app.
3. Abra https://myaccount.google.com/apppasswords, dê um nome (ex.: `VanRoute`) e clique em **Criar**.
4. O Google mostra uma senha de **16 letras** (`abcd efgh ijkl mnop`). Copie **sem os espaços**; ela só aparece uma vez.
5. Vai no `application-local.properties` (passo 5):
   - `spring.mail.username` = o e-mail;
   - `spring.mail.password` = a senha de 16 letras.

Se a senha da conta Google for trocada, as senhas de app param de valer: crie outra.

### 4.2 Chave do OpenRouteService: endereço → coordenada e trajeto da van

Usada pra achar o endereço digitado no mapa (geocode) e pra traçar o caminho e o tempo das rotas.

1. Crie uma conta grátis em https://openrouteservice.org/dev/#/signup e confirme o e-mail.
2. Entre no **Dashboard** (https://openrouteservice.org/dev/#/home).
3. Em **Request a token**, escolha o tipo **Standard**, dê um nome e clique em **CREATE TOKEN**.
4. Copie a chave pro `openrouteservice.api-key` no `application-local.properties`.

O plano grátis tem limite de chamadas por dia. Por isso, durante a viagem o backend só recalcula o trajeto de tempos em tempos (`trip.eta-refresh-seconds`, no [`application.properties`](backend/src/main/resources/application.properties)).

### 4.3 Chave do JWT: você gera

É o texto que assina os logins. Tem que ter **32 caracteres ou mais**. Gere um aleatório:

```bash
node -e "console.log(require('crypto').randomBytes(48).toString('base64'))"
```

Trocar essa chave depois derruba o login de todo mundo, que precisa entrar de novo.

---

## 5. Configure o backend (`application-local.properties`)

Os segredos de desenvolvimento ficam num arquivo **fora do git**. Copie o modelo:

```bash
# Windows (PowerShell)
Copy-Item backend/src/main/resources/application-local.properties.example backend/src/main/resources/application-local.properties
# Linux / macOS / Git Bash
cp backend/src/main/resources/application-local.properties.example backend/src/main/resources/application-local.properties
```

E preencha:

```properties
# e-mail (passo 4.1)
spring.mail.username=seu.email.do.projeto@gmail.com
spring.mail.password=abcdefghijklmnop

# mapas (passo 4.2)
openrouteservice.api-key=sua-chave-do-openrouteservice

# banco: a mesma senha do docker-compose.yml
spring.datasource.password=123456

# login (passo 4.3)
spring.jwt.key=o-texto-aleatorio-que-voce-gerou

# MinIO: os mesmos do docker-compose.yml
spring.cloud.aws.credentials.access-key=vanroute
spring.cloud.aws.credentials.secret-key=vanroute123456
```

- **Nunca suba esse arquivo pro git.** Ele já está no `.gitignore`.
- O perfil `local` (padrão do [`application.properties`](backend/src/main/resources/application.properties)) liga o Swagger e o log de SQL.
- Sem `spring.jwt.key` ou sem as chaves do MinIO, **o backend não sobe**.

O que cada fonte de configuração faz está explicado no [`backend/README.md`](backend/README.md).

---

## 6. Rode o backend

```bash
cd backend
# Windows
mvnw.cmd spring-boot:run
# Linux / macOS / Git Bash
./mvnw spring-boot:run
```

- A primeira vez demora, porque baixa as dependências e cria as tabelas.
- Pronto quando aparecer `Started BackendApplication` no log.
- Swagger, com todas as rotas da API: http://localhost:9090/swagger-ui.html
- Pelo IntelliJ: abra a pasta `backend/` como projeto Maven, use o JDK 21 e rode a classe `BackendApplication`.

---

## 7. Rode o frontend

Em outro terminal:

```bash
cd frontend
npm install        # só na primeira vez ou quando o package.json mudar
npm start          # = ng serve, já com o proxy pro backend
```

Abra http://localhost:4200.

> **Atalho no Windows:** [`scripts/run.bat`](scripts/run.bat) faz os passos 3, 6 e 7 de uma vez (Docker + backend + frontend, cada um numa janela). Os passos 4 e 5 (chaves) continuam sendo feitos uma vez, à mão.

---

## 8. Crie uma conta e teste

1. Em http://localhost:4200, cadastre-se como **motorista** ou **responsável**.
2. O código de verificação chega no e-mail que você cadastrou. Ele é enviado pelo Gmail do passo 4.1.
3. Motorista: a aprovação usa a **CNH digital** (PDF com assinatura ICP-Brasil) e o **CRLV** da van. A validação lê o documento (OCR em português, `tessdata` já incluso no backend) e confere a assinatura.

### Testar no celular

- **Mesma rede Wi-Fi:** `npm run celular` dentro de `frontend/`. Depois abra `http://IP-DO-PC:4200` no celular.
- **Fora da rede:** túnel do Cloudflare apontando pro `ng serve`:

  ```bash
  cloudflared tunnel --url http://localhost:4200
  ```

  Ele gera um endereço `https://....trycloudflare.com`. O proxy já ajusta a origem do WebSocket pro backend aceitar. Para instalar o `cloudflared`: https://developers.cloudflare.com/cloudflare-one/connections/connect-networks/downloads/

---

## 9. Testes

```bash
# backend: usa o banco vanroute_test (nunca o vanroute_db)
cd backend && ./mvnw test        # Windows: mvnw.cmd test

# frontend
cd frontend && npx ng test --watch=false
```

---

## 10. Worker diário (opcional)

Fecha viagens esquecidas, ativa e encerra contratos e marca parcelas atrasadas. No desenvolvimento não é obrigatório.

```bash
docker compose --profile worker up -d --build worker
```

Detalhes em [`docs/WORKER.md`](docs/WORKER.md).

---

## 11. Deu erro?

| Sintoma | Causa provável | O que fazer |
|---|---|---|
| Backend não sobe e fala de `JWT_KEY`, `MINIO_ACCESS_KEY` ou `placeholder` | falta valor no `application-local.properties` | confira o passo 5 |
| `Connection refused` na porta 5432, 6379 ou 9000 | o Docker não está rodando | abra o Docker Desktop e rode `docker compose up -d` |
| Código de verificação não chega | senha de app errada, com espaços, ou sem verificação em duas etapas | refaça o passo 4.1 e veja o log do backend (erro de `mail`/`SMTP`); olhe também o spam |
| Endereço não é encontrado e a rota não traça | sem chave do OpenRouteService ou limite do dia estourado | passo 4.2 |
| Mapa em branco no front | o `ng serve` foi aberto antes do `npm install` trazer o `maplibre-gl` | pare e rode `npm start` de novo |
| Front abre, mas toda chamada dá erro | backend parado ou em outra porta | o backend tem que estar em `http://localhost:9090` |
| Testes do backend falham com "database vanroute_test does not exist" | volume antigo do Postgres | `docker exec vanroute-db createdb -U postgres vanroute_test` |

Mais documentação em [`docs/`](docs/): fluxos de rota, viagem, contrato e pagamento, pendências e deploy.

---

# 🤝 Como Contribuir


1. Clone seu Fork.
2. Crie uma branch.

```bash
git checkout -b feature/nova-funcionalidade
```

4. Desenvolva.
5. Commit.

```bash
git commit -m "feat: adiciona cadastro de motorista"
```

6. Push.

```bash
git push origin feature/nova-funcionalidade
```

7. Abra um Pull Request.

---

# 🌳 Fluxo Git

- main → Produção
- develop → Desenvolvimento
- feature/* → Novas funcionalidades
- hotfix/* → Correções urgentes

---

# 📝 Convenção de Commits

- feat
- fix
- docs
- refactor
- style
- test
- chore

---

# 📌 Roadmap

- [ ] Aplicativo Android
- [ ] Aplicativo iOS
- [ ] IA para previsão de atrasos
- [ ] Reconhecimento facial
- [ ] QR Code de embarque
- [ ] Integração com escolas
- [ ] Split automático de pagamentos
- [ ] Notificações Push

---

# 📄 Licença

Este projeto é destinado para fins acadêmicos e poderá ser licenciado futuramente.

---

## 👥 Colaboradores

Antes de iniciar o desenvolvimento:

1. Siga o [passo a passo de como rodar](#️-como-rodar-o-projeto-na-sua-máquina): ferramentas, Docker, chaves (Gmail e OpenRouteService), `application-local.properties`, backend e frontend.
2. Crie uma branch a partir de `develop`.
3. Desenvolva e envie um Pull Request.

Bom desenvolvimento! 🚐
