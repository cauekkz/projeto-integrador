# VanRoute

> Transporte escolar acompanhado de perto: o responsável vê a van chegando, o motorista organiza rotas, contratos e mensalidades num lugar só.

O **VanRoute** é um site (que funciona como app no celular) que liga **responsáveis** (pais, mães, avós, tutores) e **motoristas de van escolar**. O motorista monta as rotas e conduz a viagem pelo app; o responsável acompanha a van no mapa em tempo real, avisa faltas, conversa com o motorista e cuida do contrato e das parcelas.

O projeto está alinhado à **ODS 11 – Cidades e Comunidades Sustentáveis** (meta **11.2**: transporte seguro, acessível e eficiente).

---

# 🚐 Como o site funciona

## Os dois perfis

| Perfil | O que faz no app |
|---|---|
| **Responsável** | cadastra os filhos, encontra o motorista pelo código dele, conversa no chat, aceita o contrato, acompanha a viagem no mapa, avisa faltas e vê as parcelas |
| **Motorista** | cadastra a van e as escolas, monta as rotas, propõe contratos, conduz a viagem marcando embarque e desembarque, registra ocorrências, organiza grupos de alunos e dá baixa nas parcelas |

Cada perfil só abre as telas dele: o login devolve um token (JWT) com o papel do usuário, e o front bloqueia as rotas que não são daquele papel.

## A jornada, do cadastro até a van na rua

```text
 1. Cadastro            responsável ou motorista cria a conta e confirma o código que chega no e-mail ("esqueci a senha" usa o mesmo código)
 2. Documentos          o motorista envia a CNH digital; a van entra pelo CRLV digital (PDF assinado)
 3. Filhos              o responsável cadastra os filhos e os endereços de onde cada um é buscado/deixado
 4. Encontro            o motorista passa o código dele (9 caracteres) e o responsável resgata no app → nasce o chat
 5. Proposta            no chat, o motorista monta o contrato (filho, escola, valor, período, PDF) e envia
 6. Aceite              o responsável aceita ou recusa; aceitando, nascem as parcelas
 7. Início              na data de início o contrato fica ativo e o aluno pode entrar nas rotas
 8. Rota                o motorista põe o aluno na rota: ponto de embarque, ponto de desembarque e dias da semana
 9. Viagem              o motorista inicia a viagem; o GPS dele alimenta o mapa de todos ao vivo
10. Pagamento           o responsável paga pelo Pix do motorista; o motorista confere e dá baixa
```

## Área do responsável

| Tela | Rota | O que tem |
|---|---|---|
| **Início** | `/responsible-route` | a viagem de hoje: mapa com a van, o caminho, quanto falta pra chegar e a situação do filho (aguardando, embarcou, entregue, ausente) |
| **Transporte** | `/responsible-home` | um card por filho com o motorista, a van e o status do dia; botão para acompanhar a viagem e avisar falta |
| Filhos | `/dependentes`, `/add-student`, `/student/:id` | cadastrar filho com foto, endereços do filho, outros responsáveis da família (vincular por código, tirar, passar o admin) |
| Motorista | `/driver-code`, `/driver-profile/:id` | resgatar o código do motorista; ver o perfil dele e avaliar de 1 a 5 |
| Faltas | no card do filho | avisar que o filho não vai num dia, ver as faltas marcadas e desfazer (dentro do horário que o backend permite) |

## Área do motorista

| Tela | Rota | O que tem |
|---|---|---|
| **Início** | `/driver-home` | as rotas do motorista, a van, alunos e o card "Em rota agora" quando tem viagem aberta |
| **Rotas** | `/driver-routes`, `/driver-routes/:id` | editor da rota: pontos em ordem no mapa, arrastar pra mudar a ordem, mover aluno de ponto, dias da semana, pendências |
| Viagem | `/driver-route/:id` | iniciar, marcar embarque/desembarque/ausência de cada aluno, finalizar; o app manda o GPS enquanto a viagem roda |
| Histórico | `/trip-history`, `/trip-history/:id/ocorrencias` | viagens passadas e as ocorrências registradas (atraso, pane, trânsito...) |
| Grupos | `/driver-groups`, `/driver-groups/:id` | juntar alunos em grupos e mandar um aviso pra todos os responsáveis do grupo |
| Van, escolas e Pix | `/driver-vehicles`, `/driver-schools`, `/driver-pix` | cadastrar a van pelo CRLV, as escolas atendidas e a chave Pix que recebe as parcelas |
| Código | `/driver-link-code` | o código que o motorista passa pros responsáveis |

## Telas dos dois

| Tela | Rota | O que tem |
|---|---|---|
| **Conversas** | `/chat`, `/chat-details/:id` | chat em tempo real; a proposta de contrato aparece como um card dentro da conversa |
| Contratos | `/contracts`, `/contract/:id` | proposta, aceite, recusa, retirar proposta, pedir e aceitar cancelamento, assinaturas e o PDF |
| Parcelas | `/payments` | o responsável vê as parcelas e copia a chave Pix; o motorista dá baixa ou dispensa |
| Notificações | `/notifications` | "a van saiu", "Ana embarcou", "proposta nova", "parcela atrasada"... chegam ao vivo e ficam guardadas |
| Perfil | `/profile-info/:tipo`, `/profile-select` | foto, dados pessoais e senha (as mudanças pedem um código enviado por e-mail) |
| Privacidade | `/privacy`, `/terms` | termos de uso (com aceite), exportar meus dados e excluir minha conta (LGPD) |

O rodapé tem 3 botões, **Início**, **Rotas/Transporte** e **Conversas**, e o site tem **modo claro e escuro**.

## Regras que valem pro site todo

- **Contrato manda em tudo:** o aluno só entra na rota e na viagem com contrato **ativo** com aquele motorista.
- **Cancelamento** tem aviso prévio de 30 dias, ou é na hora se a outra parte aceitar.
- **O dinheiro não passa pelo app:** o responsável paga no Pix do motorista, e quem confirma o recebimento é o motorista.
- **Tarefas da madrugada:** um worker em Go roda todo dia às 00:05 (horário de São Paulo) e fecha viagens esquecidas, expira propostas, ativa e encerra contratos e marca parcelas atrasadas.
- **Endereço é único:** mesmo CEP + número é o mesmo lugar; a coordenada vem sempre da busca no mapa, nunca do app.

## Como as coisas acontecem por baixo

```text
Navegador / celular (Angular)
   │  HTTP /api  ·  WebSocket /ws  ·  arquivos /storage
   ▼
Backend Spring Boot ──── PostgreSQL   contas, filhos, rotas, contratos, viagens, parcelas, notificações
   │                ├─── Redis        posição da van ao vivo, chat, códigos de e-mail, refresh token
   │                ├─── MinIO (S3)   fotos, CNH, CRLV e PDFs dos contratos
   │                └─── OpenRouteService   endereço → coordenada, trajeto e tempo de chegada
   ▼
Worker Go (1x por dia) ── PostgreSQL
```

**A van no mapa, ao vivo:** o celular do motorista manda a posição pelo WebSocket `/trip/{tripId}` → o backend guarda o estado ao vivo no Redis e publica a mudança → todo mundo conectado naquela viagem recebe a van, o caminho e o tempo até cada ponto. Embarque, desembarque e falta seguem o mesmo caminho, e as notificações chegam pelo WebSocket `/notifications`.

**Arquivos:** o app pede ao backend um link temporário e envia o arquivo direto pro MinIO; o backend só guarda a chave do arquivo.

**Documentos do motorista:** a CNH e o CRLV digitais são PDFs com assinatura ICP-Brasil. O backend confere a assinatura e lê os dados com OCR em português.

Os detalhes de cada fluxo, chamada por chamada, estão em [`docs/`](docs/):

| Documento | Assunto |
|---|---|
| [`FLUXO_CONTRATO.md`](docs/FLUXO_CONTRATO.md) | chat, proposta, aceite, cancelamento |
| [`FLUXO_ROTA.md`](docs/FLUXO_ROTA.md) | endereços, pontos e como o aluno entra na rota |
| [`FLUXO_TRIP.md`](docs/FLUXO_TRIP.md) | a viagem do dia, WebSocket e notificações |
| [`FLUXO_PAGAMENTO.md`](docs/FLUXO_PAGAMENTO.md) | chave Pix e parcelas |
| [`WORKER.md`](docs/WORKER.md) | as tarefas diárias |
| [`FLUXO_FRONT.md`](docs/FLUXO_FRONT.md) | o que ainda falta no front |
| [`DEPLOY.md`](docs/DEPLOY.md) | produção |

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

# 🌳 Fluxo Git

- `main` → versão estável
- `develop` → desenvolvimento; toda branch nova sai daqui
- `feat/*`, `fix/*` → uma funcionalidade ou correção por branch, com Pull Request para `develop`

## Convenção de commits

`feat`, `fix`, `docs`, `refactor`, `style`, `test`, `chore`, com o escopo quando ajuda: `feat(front): ...`, `fix(back): ...`.

---

# 📌 O que ainda não existe

- Notificação **push com o app fechado** (hoje as notificações chegam com o app aberto) — issues #150 e #172
- **Pagamento automático** pelo Mercado Pago (hoje é Pix direto pro motorista com baixa manual) — issue #192
- Área administrativa, integração com escolas e apps nativos Android/iOS

A lista viva fica no [GitHub Project](https://github.com/users/cauekkz/projects/1) e em [`docs/PENDENCIAS_E_ERROS.md`](docs/PENDENCIAS_E_ERROS.md).

---

# 📄 Licença

Projeto acadêmico (Projeto Integrador). Poderá ser licenciado futuramente.
