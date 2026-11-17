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

Este passo a passo é pra quem **nunca mexeu no projeto** e sabe pouco de programação. Ele foi escrito pensando no **Windows 10/11**; quem usa Mac ou Linux tem as diferenças anotadas em cada passo.

Siga na ordem, sem pular. Na primeira vez leva de **1 a 2 horas** (a maior parte é esperar downloads). Da segunda vez em diante são uns 5 minutos (veja o [passo 11](#11-da-segunda-vez-em-diante)).

## O que você vai precisar

- Um computador com **8 GB de RAM ou mais** e uns **15 GB livres** no disco.
- Internet (vai baixar bastante coisa na primeira vez).
- Uma **conta Google (Gmail)**: é dela que saem os e-mails com os códigos de cadastro.
- Uma conta grátis no **OpenRouteService** (é o serviço de mapas; o passo 5 mostra como criar).

## Como o projeto é montado (só pra entender)

O VanRoute são **3 partes** que rodam juntas no seu computador:

| Parte | O que é | Onde fica |
|---|---|---|
| **Banco de dados, Redis e MinIO** | onde ficam guardados os dados e os arquivos | rodam dentro do **Docker** (um programa que liga esses serviços sem você precisar instalar cada um) |
| **Backend** | o "cérebro" do sistema, feito em Java | roda numa janela de terminal, no endereço `http://localhost:9090` |
| **Frontend** | o site que você abre no navegador, feito em Angular | roda em outra janela de terminal, no endereço `http://localhost:4200` |

Você só vai usar o endereço do **frontend** (`http://localhost:4200`). Ele conversa com o backend sozinho.

---

## 1. Aprenda a abrir o terminal

O terminal é uma janela onde você digita comandos. Vamos usar muito ele.

- **Windows:** aperte a tecla **Windows**, digite **PowerShell** e clique em **Windows PowerShell**.
- **Mac:** aperte **Cmd + Espaço**, digite **Terminal** e aperte Enter.

Regras pra não errar:

- Cada bloco de código cinza deste guia é um comando. **Copie, cole no terminal (botão direito do mouse cola no PowerShell) e aperte Enter.**
- Espere o comando terminar (o cursor volta a piscar numa linha nova) antes de colar o próximo.
- Se um comando não for reconhecido logo depois de instalar um programa, **feche o terminal e abra de novo**. Às vezes o Windows só "enxerga" o programa novo numa janela nova.

---

## 2. Instale os programas

Instale **os 4** abaixo, um de cada vez. Em todos, pode clicar em **Next / Avançar** e deixar as opções que já vêm marcadas, salvo o que estiver dito na tabela.

| # | Programa | Pra que serve | Onde baixar | Cuidado na instalação |
|---|---|---|---|---|
| 1 | **Git** | baixar o projeto | https://git-scm.com/downloads | nada, só ir avançando |
| 2 | **Java (JDK 21)** | rodar o backend | https://adoptium.net/temurin/releases/?version=21 → escolha seu sistema, **JDK**, arquivo **.msi** (Windows) ou **.pkg** (Mac) | na tela de opções, clique em **"Set JAVA_HOME variable"** e escolha **"Will be installed on local hard drive"** |
| 3 | **Node.js** (versão **LTS**) | rodar o frontend | https://nodejs.org → botão da versão **LTS** | nada, só ir avançando |
| 4 | **Docker Desktop** | rodar banco, Redis e MinIO | https://www.docker.com/products/docker-desktop | no Windows ele pode pedir pra **reiniciar o computador** e pra instalar o **WSL**: aceite tudo. Quando ele abrir pedindo login, pode clicar em **Skip / Continue without signing in** |

Depois de instalar os 4, **abra o Docker Desktop** (procure no menu Iniciar) e espere aparecer **"Engine running"** (ou a baleia ficar verde) no canto de baixo da janela.

Agora abra um **terminal novo** e confira se está tudo certo, um comando por vez:

```powershell
git --version
java -version
node -v
npm -v
docker compose version
```

Cada um tem que responder com um número de versão. O `java -version` tem que mostrar **21** e o `node -v` tem que mostrar **v22** ou mais. Se algum disser "não é reconhecido", feche o terminal, abra outro e tente de novo; se continuar, reinstale aquele programa.

> **Não precisa instalar** Maven, Angular, PostgreSQL nem Redis. O projeto baixa ou liga tudo isso sozinho.

---

## 3. Baixe o projeto

No terminal, entre na pasta **Documentos** e baixe o projeto:

```powershell
cd ~/Documents
git clone https://github.com/cauekkz/projeto-integrador
cd projeto-integrador
```

Pronto: o projeto está em `Documentos\projeto-integrador`. **Todos os próximos comandos são rodados de dentro dessa pasta**, a não ser quando o guia mandar entrar em outra.

> Se você fechar o terminal e abrir outro, volte pra pasta do projeto com `cd ~/Documents/projeto-integrador`.

---

## 4. Ligue o banco de dados (Docker)

Com o **Docker Desktop aberto**, rode:

```powershell
docker compose up -d
```

Na primeira vez ele baixa as imagens e demora alguns minutos. Quando terminar, confira:

```powershell
docker compose ps
```

Tem que aparecer **3 linhas**, `vanroute-db`, `vanroute-redis` e `vanroute-minio`, com **Up** ou **running** na coluna de status.

Isso já deixa pronto, sem você fazer mais nada: o banco `vanroute_db` (e o `vanroute_test`, dos testes automáticos), o Redis e o MinIO (onde ficam as fotos e PDFs). As **tabelas** do banco são criadas sozinhas pelo backend no passo 7.

---

## 5. Pegue as 3 chaves

O backend precisa de **3 segredos**. Abra o **Bloco de Notas** e vá anotando cada um conforme for pegando; no passo 6 você vai colar tudo num arquivo.

### 5.1 E-mail e senha de app do Gmail

O site manda um **código por e-mail** no cadastro e no "esqueci a senha". Quem envia é uma conta Gmail sua, usando uma **senha de app** (uma senha especial de 16 letras, **diferente** da senha normal da conta).

1. Escolha a conta Gmail que vai enviar os e-mails (pode criar uma só pra isso).
2. Entre em https://myaccount.google.com/security e ative a **Verificação em duas etapas** (o Google não deixa criar senha de app sem ela).
3. Entre em https://myaccount.google.com/apppasswords, digite um nome qualquer (ex.: `VanRoute`) e clique em **Criar**.
4. Vai aparecer uma senha de **16 letras**, tipo `abcd efgh ijkl mnop`. **Anote ela sem os espaços** (`abcdefghijklmnop`). Ela só aparece uma vez; se perder, crie outra.
5. Anote também o endereço do Gmail.

### 5.2 Chave do OpenRouteService (mapas)

Serve pra achar no mapa os endereços digitados e traçar o caminho da van.

1. Crie uma conta grátis em https://openrouteservice.org/dev/#/signup e confirme pelo e-mail que eles mandam.
2. Entre no painel: https://openrouteservice.org/dev/#/home
3. Em **Request a token**, deixe o tipo **Standard**, dê um nome qualquer e clique em **CREATE TOKEN**.
4. Copie a chave que aparece (um texto comprido de letras e números) e anote.

### 5.3 Chave do login (JWT)

É um texto aleatório que você mesmo gera. Rode no terminal:

```powershell
node -e "console.log(require('crypto').randomBytes(48).toString('base64'))"
```

Ele imprime um texto comprido e embaralhado. **Copie a linha inteira** e anote.

---

## 6. Crie o arquivo de configuração do backend

As chaves ficam num arquivo chamado `application-local.properties`, que **só existe no seu computador** (ele nunca vai pro GitHub). O projeto já traz um modelo; vamos copiar o modelo e abrir no Bloco de Notas.

**Windows (PowerShell):**

```powershell
Copy-Item backend/src/main/resources/application-local.properties.example backend/src/main/resources/application-local.properties
notepad backend/src/main/resources/application-local.properties
```

**Mac / Linux:**

```bash
cp backend/src/main/resources/application-local.properties.example backend/src/main/resources/application-local.properties
open -e backend/src/main/resources/application-local.properties
```

O arquivo abre. Troque **só o que vem depois do `=`** nestas 4 linhas, colando o que você anotou no passo 5:

```properties
spring.mail.username=o-gmail-do-passo-5.1@gmail.com
spring.mail.password=asenhade16letrassemespacos
openrouteservice.api-key=a-chave-do-passo-5.2
spring.jwt.key=o-texto-que-voce-gerou-no-passo-5.3
```

**Não mexa** nas outras linhas (`spring.datasource.password=123456`, `...access-key=vanroute`, `...secret-key=vanroute123456`): elas já estão com os valores certos do Docker.

Cuidados:

- Não deixe espaço antes nem depois do `=`.
- Não coloque aspas.
- Salve com **Ctrl + S** e feche o Bloco de Notas.

---

## 7. Ligue o backend (deixe essa janela aberta)

No terminal, ainda na pasta do projeto:

**Windows (PowerShell):**

```powershell
cd backend
.\mvnw.cmd spring-boot:run
```

**Mac / Linux:**

```bash
cd backend
./mvnw spring-boot:run
```

- Na primeira vez **demora bastante** (5 a 15 minutos), porque ele baixa tudo o que precisa. Vai passar muito texto na tela; é normal.
- Está pronto quando aparecer uma linha com **`Started BackendApplication`**.
- **Não feche essa janela.** Enquanto ela estiver aberta, o backend está ligado. Se fechar, o site para de funcionar.

Pra conferir: abra http://localhost:9090/swagger-ui.html no navegador. Se aparecer uma página com a lista da API, o backend está funcionando.

---

## 8. Ligue o frontend (numa janela nova)

Abra um **segundo terminal** (o primeiro continua com o backend rodando) e rode:

```powershell
cd ~/Documents/projeto-integrador/frontend
npm install
```

O `npm install` baixa as peças do site e leva alguns minutos. **Só precisa na primeira vez** (ou quando alguém mudar o `package.json`). Depois:

```powershell
npm start
```

Está pronto quando aparecer **`Local: http://localhost:4200/`**. **Não feche essa janela também.**

> **Atalho no Windows:** depois que os passos 1 a 6 estiverem feitos, dá pra ligar Docker + backend + frontend de uma vez dando **dois cliques** no arquivo [`scripts/run.bat`](scripts/run.bat) (ele abre uma janela pra cada um). O `npm install` do passo 8 precisa ter sido feito pelo menos uma vez antes.

---

## 9. Use o site

Abra o navegador (Chrome, Edge ou Firefox) em **http://localhost:4200**.

**O jeito mais fácil de testar é como responsável:**

1. Clique pra **criar conta** e escolha **Responsável**.
2. Preencha os dados com um **e-mail seu de verdade** (é lá que chega o código).
3. Abra o seu e-mail: vai chegar um código de **6 números** enviado pelo Gmail do passo 5.1. Olhe também a caixa de **spam**. Digite o código no site.
4. Pronto: você está dentro. Cadastre um filho, os endereços dele e navegue pelas telas.

**Pra testar como motorista** você precisa da sua **CNH digital em PDF** (aquela baixada do app **Carteira Digital de Trânsito**, com assinatura digital). O sistema lê e confere esse documento de verdade, então foto ou PDF escaneado não passam. Pra cadastrar a van, o mesmo vale pro **CRLV digital**.

**Pra ver o sistema completo** (chat, contrato, viagem com a van no mapa), crie **duas contas**, uma de motorista e uma de responsável, com **e-mails diferentes**. Abra cada uma num navegador diferente (ou uma numa **janela anônima**: Ctrl + Shift + N) e siga a jornada da seção [A jornada, do cadastro até a van na rua](#a-jornada-do-cadastro-até-a-van-na-rua).

### Abrir no celular (opcional)

Com o celular **no mesmo Wi-Fi** do computador:

1. Pare o frontend (na janela dele aperte **Ctrl + C**) e ligue de novo com:

   ```powershell
   npm run celular
   ```

2. Descubra o IP do computador: no PowerShell rode `ipconfig` e procure **Endereço IPv4** (algo como `192.168.0.15`).
3. No navegador do celular, abra `http://192.168.0.15:4200` (trocando pelo seu IP).
4. Se não abrir, o Firewall do Windows pode estar bloqueando: quando ele perguntar, clique em **Permitir acesso** pro Node.js.

> O mapa ao vivo usa o GPS do celular, e o navegador só libera o GPS em endereço `https`. Pra isso existe o túnel do Cloudflare: instale o `cloudflared` (https://developers.cloudflare.com/cloudflare-one/connections/connect-networks/downloads/), rode `cloudflared tunnel --url http://localhost:4200` e abra no celular o endereço `https://....trycloudflare.com` que ele mostrar.

---

## 10. Como desligar

1. Na janela do **frontend**: aperte **Ctrl + C** (se perguntar, digite `S` ou `Y` e Enter).
2. Na janela do **backend**: aperte **Ctrl + C**.
3. Pra desligar o banco (opcional, ele gasta pouca memória), na pasta do projeto:

   ```powershell
   docker compose stop
   ```

Seus dados (contas, filhos, rotas) **continuam salvos** pra próxima vez.

---

## 11. Da segunda vez em diante

Não precisa repetir instalação, chaves nem `npm install`. É só:

1. Abrir o **Docker Desktop** e esperar ficar verde.
2. Terminal 1:

   ```powershell
   cd ~/Documents/projeto-integrador
   docker compose up -d
   cd backend
   .\mvnw.cmd spring-boot:run
   ```

   (Mac/Linux: `./mvnw spring-boot:run` na última linha.)
3. Terminal 2:

   ```powershell
   cd ~/Documents/projeto-integrador/frontend
   npm start
   ```

4. Abrir http://localhost:4200.

No Windows, os passos 2 e 3 podem ser trocados por dois cliques no `scripts/run.bat`.

Pra pegar a versão mais nova do projeto antes de ligar: `git pull` na pasta do projeto e depois `npm install` dentro de `frontend`.

---

## 12. Deu erro?

| O que aparece | Por que acontece | Como resolver |
|---|---|---|
| `'git'` / `'java'` / `'node'` / `'docker'` **não é reconhecido** | o terminal foi aberto antes da instalação | feche o terminal e abra outro; se continuar, reinstale o programa |
| `java -version` mostra outra versão (8, 17...) | tem outro Java instalado na frente | reinstale o JDK 21 marcando **"Set JAVA_HOME variable"** (passo 2) e abra um terminal novo |
| `npm` dá erro de **"execução de scripts foi desabilitada"** | segurança padrão do PowerShell | rode `Set-ExecutionPolicy -Scope CurrentUser RemoteSigned`, responda `S` e abra outro terminal (ou use `npm.cmd` no lugar de `npm`) |
| `docker compose up -d` dá erro de **"cannot connect"** / **"pipe"** | o Docker Desktop está fechado ou ainda ligando | abra o Docker Desktop, espere ficar verde e rode de novo |
| `.\mvnw.cmd` **não é reconhecido** | você não está dentro da pasta `backend` | rode `cd ~/Documents/projeto-integrador/backend` e tente de novo |
| Backend para com erro falando de `JWT_KEY`, `MINIO_ACCESS_KEY` ou `placeholder` | o `application-local.properties` não existe ou falta valor nele | refaça o passo 6 e confira se salvou o arquivo |
| Backend para com `Connection refused` (porta 5432, 6379 ou 9000) | o Docker não está ligado | passo 4 |
| `Port 9090 was already in use` ou `Port 4200 is already in use` | já tem um backend/frontend aberto em outra janela | feche a outra janela (ou aperte Ctrl + C nela) e rode de novo |
| O código de cadastro **não chega** | senha de app errada, com espaços, ou sem verificação em duas etapas | refaça o passo 5.1 e o 6, olhe o spam, e procure por `mail` ou `SMTP` na janela do backend |
| Endereço **não é encontrado** e a rota não aparece no mapa | chave do OpenRouteService faltando ou o limite grátis do dia acabou | passo 5.2 (o limite volta no dia seguinte) |
| O site abre, mas tudo dá erro | o backend está desligado ou ainda ligando | olhe a janela do backend e espere o `Started BackendApplication` |
| O mapa fica em branco | o frontend foi ligado antes do `npm install` terminar | Ctrl + C no frontend e `npm start` de novo |
| Testes do backend falham com **"database vanroute_test does not exist"** | o banco do Docker é de uma versão antiga do projeto | `docker exec vanroute-db createdb -U postgres vanroute_test` |

---

## Para quem desenvolve

### Testes automáticos

```powershell
# backend: usa o banco vanroute_test (nunca o vanroute_db)
cd backend
.\mvnw.cmd test          # Mac/Linux: ./mvnw test

# frontend
cd frontend
npx ng test --watch=false
```

### Worker diário (opcional)

Fecha viagens esquecidas, ativa e encerra contratos e marca parcelas atrasadas. Não é preciso pra testar o site.

```powershell
docker compose --profile worker up -d --build worker
```

Detalhes em [`docs/WORKER.md`](docs/WORKER.md).

### Outras informações

- O front nunca chama o backend direto: o `ng serve` repassa `/api`, `/ws` (WebSocket) e `/storage` (MinIO) pelo [`frontend/proxy.conf.json`](frontend/proxy.conf.json).
- Painel do MinIO (arquivos enviados): http://localhost:9001, usuário `vanroute`, senha `vanroute123456`.
- Banco: PostgreSQL em `localhost:5432`, usuário `postgres`, senha `123456`, banco `vanroute_db`.
- Pelo IntelliJ: abra a pasta `backend/` como projeto Maven, use o JDK 21 e rode a classe `BackendApplication`.
- **Sem Docker?** Dá pra instalar o PostgreSQL 15, o [Memurai](https://www.memurai.com) (no lugar do Redis) e o MinIO direto na máquina, com as mesmas portas, usuários e senhas do [`docker-compose.yml`](docker-compose.yml). O Docker é bem mais simples.
- O que cada arquivo de configuração faz está no [`backend/README.md`](backend/README.md). Mais documentação em [`docs/`](docs/).

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
