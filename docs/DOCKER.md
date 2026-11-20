# Docker do VanRoute, explicado

Como funciona cada arquivo de Docker do projeto e o que acontece quando você roda o app na sua máquina. O passo a passo curto (instalar, preencher o `.env`, subir) está no [README](../README.md#-rodar-tudo-no-docker).

---

## 1. As quatro palavras

| Palavra | O que é |
|---|---|
| **Imagem** | um "molde" pronto: sistema mínimo + programa + o que ele precisa. Não roda sozinha; é a receita congelada |
| **Container** | uma imagem rodando. Cada serviço do app é **um container separado** |
| **Dockerfile** | a receita de **uma** imagem: de qual imagem parte, o que copia, o que instala, que comando roda ao ligar |
| **docker compose** | lê o `docker-compose.yml`, que lista **todos os serviços** e diz como rodar juntos: qual imagem (ou qual Dockerfile), variáveis, portas, volumes e quem depende de quem |

O compose **não cria** Dockerfile e não é "um Dockerfile gigante": ele **usa** os Dockerfiles que já existem (um por serviço) e **orquestra** os containers.

---

## 2. O que existe no projeto

| Arquivo | Para quê |
|---|---|
| [`backend/Dockerfile`](../backend/Dockerfile) | imagem da API (Java) |
| [`backend/.dockerignore`](../backend/.dockerignore) | o que **não** entra na imagem do backend |
| [`frontend/Dockerfile`](../frontend/Dockerfile) | imagem do site (Angular servido pelo Nginx) |
| [`frontend/nginx.conf`](../frontend/nginx.conf) | configuração do Nginx dentro da imagem do site |
| [`worker/Dockerfile`](../worker/Dockerfile) | imagem das tarefas diárias (Go) |
| [`docker-compose.yml`](../docker-compose.yml) | junta tudo: os 3 acima + banco, Redis e MinIO |
| [`.env.example`](../.env.example) → `.env` | senhas e chaves. O `.env` é seu e fica fora do git |

O banco (Postgres), o Redis e o MinIO **não têm Dockerfile**: usam imagens prontas, baixadas do Docker Hub.

---

## 3. `backend/Dockerfile`, linha por linha

Duas etapas ("multi-stage"): uma **compila**, a outra **roda**. A imagem final só leva o resultado da primeira.

```dockerfile
# --- etapa 1: compilar ---
FROM eclipse-temurin:21-jdk-noble AS build     # imagem com o JDK 21 (compilador do Java)
WORKDIR /src                                   # pasta de trabalho DENTRO da imagem
COPY .mvn .mvn                                 # copia o Maven Wrapper do projeto...
COPY mvnw pom.xml ./                           # ...e o pom.xml
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline   # baixa as dependências do pom.xml
COPY src src                                   # copia o código (backend/src)
RUN ./mvnw -B -q -DskipTests package && cp target/backend-*.jar /app.jar   # COMPILA: gera o .jar

# --- etapa 2: rodar ---
FROM eclipse-temurin:21-jre-noble              # imagem NOVA, só com a JVM (sem compilador)
RUN apt-get update \
    && apt-get install -y --no-install-recommends tesseract-ocr fontconfig fonts-dejavu-core \
    && rm -rf /var/lib/apt/lists/* \
    && useradd --system --uid 10001 vanroute   # Tesseract (OCR da CNH), fontes (PDF → imagem) e um usuário sem privilégio
COPY --from=build /app.jar /app/app.jar        # traz SÓ o .jar da etapa 1
USER vanroute                                  # roda como esse usuário, não como root
EXPOSE 9090                                    # a API escuta na 9090 (dentro da rede do Docker)
ENV SPRING_PROFILES_ACTIVE=prod \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"   # perfil sem Swagger/log de SQL; usa até 75% da memória do container
ENTRYPOINT ["java", "-jar", "/app/app.jar"]    # o comando que roda TODA VEZ que o container liga
```

- **Onde compila:** na linha do `./mvnw ... package` (etapa 1).
- **Onde roda:** no `ENTRYPOINT` (etapa 2). Ligar o container = `java -jar app.jar`.
- **Por que dois `COPY` separados** (primeiro o `pom.xml`, depois o `src`): o Docker guarda cada passo em cache. Se só o código mudou, ele reaproveita as dependências baixadas e só recompila.
- **Por que duas etapas:** a imagem final não leva código-fonte, Maven nem JDK. Fica menor e expõe menos coisa.
- **Configuração:** nenhuma senha está na imagem. Tudo chega por variável de ambiente quando o container liga (seção 7).

`backend/.dockerignore` deixa de fora: `target/` (build local), `api-tests/`, a configuração da sua IDE e PDFs.

---

## 4. `frontend/Dockerfile` e `frontend/nginx.conf`

```dockerfile
FROM node:22-alpine AS builder                 # etapa 1: Node, só pra compilar o Angular
WORKDIR /app
COPY package*.json ./
RUN npm ci                                     # instala as dependências (package-lock.json)
COPY . ./
RUN npm run build -- --configuration production   # gera o site em dist/frontend/browser

FROM nginx:alpine                              # etapa 2: só o Nginx (servidor de arquivos)
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=builder /app/dist/frontend/browser /usr/share/nginx/html
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]             # o que roda ao ligar
```

O site compilado é só HTML, CSS e JavaScript: quem entrega é o Nginx. O `nginx.conf` também faz o papel do `proxy.conf.json` do `npm start`:

| Caminho | Vai para |
|---|---|
| `/` | os arquivos do site (qualquer rota do Angular abre o `index.html`) |
| `/api/...` | o backend (`backend:9090`) |
| `/ws/...` | os WebSockets do backend (chat, viagem, notificações) |
| `/storage/...` | o MinIO (`minio:9000`): fotos e PDFs |

Por isso só a porta do site precisa ficar aberta no seu PC: o navegador fala só com o Nginx.

---

## 5. `worker/Dockerfile`

```dockerfile
FROM golang:1.25-alpine AS build               # etapa 1: Go, pra compilar
WORKDIR /src
COPY go.mod go.sum ./
RUN go mod download                            # dependências
COPY . .
RUN CGO_ENABLED=0 go build -trimpath -ldflags="-s -w" -o /worker ./cmd   # gera um binário só

FROM gcr.io/distroless/static-debian12:nonroot # etapa 2: imagem mínima (~20 MB), sem shell
COPY --from=build /worker /worker
ENTRYPOINT ["/worker"]
```

Ao ligar, ele roda as tarefas uma vez e depois todo dia no horário `WORKER_RUN_AT` ([`docs/WORKER.md`](WORKER.md)).

---

## 6. `docker-compose.yml`, serviço por serviço

| Serviço | Imagem | Porta no seu PC | Volume (dados que ficam) | Perfil |
|---|---|---|---|---|
| `db` | `postgres:15-alpine` (pronta) | `5432` | `postgres_data` | sempre |
| `redis` | `redis:7-alpine` (pronta) | `6379` | `redis_data` | sempre |
| `minio` | MinIO (pronta) | `9000` (API) e `9001` (painel) | `minio_data` | sempre |
| `backend` | `build: ./backend` (seção 3) | nenhuma | — | `app` |
| `frontend` | `build: ./frontend` (seção 4) | `APP_PORT` (padrão `8080`) | — | `app` |
| `worker` | `build: ./worker` (seção 5) | nenhuma | — | `app` e `worker` |

O que cada pedaço do arquivo faz:

- **`image:`** usa uma imagem pronta. **`build: ./backend`** manda o Docker montar a imagem com o Dockerfile daquela pasta.
- **`profiles:`** o serviço só sobe se o perfil for pedido:
  - `docker compose up -d` → só `db`, `redis` e `minio` (pra quem programa com o backend e o front rodando na máquina);
  - `docker compose --profile app up -d --build` → os seis.
- **`environment:`** as variáveis que **aquele** container recebe. `${DATABASE_PASSWORD:-123456}` = o valor do `.env`; se não existir, o que vem depois do `:-`.
- **`depends_on:`** a ordem de subida. O backend espera o banco ficar saudável (`healthcheck` com `pg_isready`).
- **`volumes:`** pastas que guardam dados fora do container. Desligar e ligar de novo não apaga nada; só `down -v` apaga.
  - O `./docker/postgres-init` roda uma vez, quando o banco é criado, e cria também o `vanroute_test` (dos testes). O `:z` é pro Fedora (SELinux); nos outros sistemas é ignorado.
- **`ports:`** `"8080:80"` = a porta 8080 do seu PC vai na porta 80 do container.
- **Rede:** o compose cria uma rede privada onde cada serviço é achado pelo **nome**. Por isso o backend usa `db:5432`, `redis` e `minio:9000`, e não `localhost`.
- **`restart: unless-stopped`:** se cair, o Docker liga de novo.

---

## 7. Do `.env` até dentro do container

```
.env (na raiz, você preenche)
   │  o docker compose lê sozinho o arquivo chamado .env na pasta onde você roda o comando
   ▼
docker-compose.yml          JWT_KEY: ${JWT_KEY:-}      ← troca ${...} pelo valor do .env
   │  entrega ao container só as variáveis listadas no environment: dele
   ▼
container do backend        variável de ambiente JWT_KEY=...
   │
   ▼
Spring (application.yml)    key: ${JWT_KEY}
```

- O arquivo `.env` **não entra** em nenhum container nem imagem: só os valores, e só os que cada serviço lista. O worker recebe só as variáveis do banco; o front, nenhuma.
- As senhas do banco e do MinIO valem **quando o volume é criado** (primeira vez). Trocar depois no `.env` não muda um volume que já existe.
- O mesmo `.env` serve pro backend rodando fora do Docker (`mvnw`, IntelliJ, testes): o `application.properties` importa ele.

---

## 8. Comandos

```bash
docker compose --profile app up -d --build      # monta as imagens (só o que mudou) e sobe tudo
docker compose --profile app ps                 # o que está rodando
docker compose --profile app logs -f backend    # log de um serviço (backend, frontend, worker, db...)
docker compose --profile app restart backend    # reinicia um serviço
docker compose --profile app down               # desliga tudo (os dados ficam nos volumes)
docker compose --profile app down -v            # desliga e APAGA os dados
docker compose up -d                            # só banco, Redis e MinIO (pra programar)
docker images                                   # imagens baixadas e montadas
```

A primeira vez demora alguns minutos: baixa as imagens (JDK, JRE, Node, Nginx, Go, Postgres, Redis, MinIO) e compila o backend, o front e o worker. Das próximas, o Docker reaproveita o que já tem e só refaz o que mudou.
