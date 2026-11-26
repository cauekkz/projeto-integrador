# Deploy: problemas conhecidos

**O app ainda não foi publicado.** Já dá pra subir tudo no Docker com um comando (README, seção "Rodar tudo no Docker"); aqui ficam os pontos a resolver quando chegar a hora de publicar num servidor de verdade.

A lista geral de produção (perfil, segredos, login, fuso, worker) está em [`PENDENCIAS_E_ERROS.md`, seção 3](PENDENCIAS_E_ERROS.md). A preparação para produção é a issue #187.

---

## 1. Como as peças conversam no Docker

Resolvido em 14/11 e testado de ponta a ponta (site, login, chat ao vivo, upload e download de foto e PDF, viagem com mapa, cadastro de motorista com OCR da CNH e CRLV):

- **Um Dockerfile por serviço:** `backend/`, `frontend/` e `worker/`. O banco, o Redis e o MinIO usam imagens prontas.
- **O Nginx do front é a única porta aberta.** Ele faz o mesmo que o `proxy.conf.json` do `ng serve` ([`frontend/nginx.conf`](../frontend/nginx.conf)):
  - `/api/` → backend;
  - `/ws/` → WebSockets do backend (sem log, porque o token vai na URL);
  - `/storage/` → MinIO, com o mesmo `Host` da assinatura dos links;
  - uploads até 11 MB, gzip e cache dos arquivos do site.
- **Links do MinIO:** o backend assina com `http://minio:9000`, e a `urlDoStorage` do front troca por `/storage`. Assim a assinatura continua valendo, igual no desenvolvimento.
- **Segredos:** só no `.env` (modelo: `.env.example`), fora do git, e nunca dentro da imagem (`backend/.dockerignore`).
- **Sem `JWT_KEY`** (ou com menos de 32 caracteres), o backend se recusa a subir, com a mensagem no log.

## 2. Ainda falta para um servidor de verdade

- **HTTPS na frente do Nginx** (Caddy, Traefik, Cloudflare Tunnel ou o HTTPS da hospedagem). Sem ele não tem GPS no celular (item 4).
- **MinIO com domínio próprio (opcional):** se os arquivos forem servidos por outro endereço, defina o `MINIO_ENDPOINT` com ele e ajuste a `urlDoStorage` do front. Hoje tudo passa pelo `/storage` do mesmo site, o que é o mais simples.
- **Backup** do volume do Postgres e do MinIO, **logs** e **monitoramento** (issue #187).

### Imagem pronta do site (sem `npm build` no servidor)

O `npm build` do Angular é a parte mais lenta e pesada do `--build`, e trava em servidor pequeno. Por isso o site pode ir como **imagem pronta**: você gera na sua máquina, manda pro Docker Hub, e o servidor só baixa. O backend e o worker continuam sendo construídos no servidor.

**Na sua máquina** (a cada versão nova do front):

```bash
docker login                                                        # uma vez, com a conta do Docker Hub
bash scripts/publicar-front.sh seu-usuario/vanroute-frontend        # gera e manda (tag latest)
bash scripts/publicar-front.sh seu-usuario/vanroute-frontend v1.2   # ou com versão (v1.2 e latest)
```

- Gera para `linux/amd64` e `linux/arm64` (Oracle Ampere, por exemplo). O `npm build` roda uma vez só, na sua máquina: o HTML/JS é o mesmo, só o Nginx muda de arquitetura.
- A imagem não tem segredo nenhum: as chaves ficam no backend e no `.env`. O Nginx acha o backend e o MinIO pelo nome do serviço (`backend`, `minio`), então a mesma imagem serve pra qualquer servidor.
- No Docker Hub a imagem nasce **pública**. Se deixar privada, rode `docker login` também no servidor.

**No servidor**, no `.env`:

```
FRONTEND_IMAGE=seu-usuario/vanroute-frontend:latest
COMPOSE_FILE=docker-compose.yml:docker-compose.prod.yml
```

e suba como sempre:

```bash
docker compose --profile app up -d --build
```

O [`docker-compose.prod.yml`](../docker-compose.prod.yml) troca o `build: ./frontend` pela imagem do `FRONTEND_IMAGE` e baixa a versão mais nova a cada `up`. Sem o `FRONTEND_IMAGE`, o compose avisa e não sobe. Na sua máquina nada muda: sem o `COMPOSE_FILE`, o front continua sendo compilado do código.

**Atualizar o site:** rode o `publicar-front.sh` de novo na sua máquina e, no servidor, `docker compose --profile app up -d frontend`.

---

## 3. Mapa: fundo do CARTO

**O que cada serviço faz:**

| Serviço | Papel | Quem chama |
|---|---|---|
| **OpenRouteService** | geocodifica endereços e calcula o trajeto e o tempo (a linha azul e o "em 10 min") | o backend, com a chave |
| **MapLibre** (biblioteca, dentro do front) | desenha o mapa na tela: fundo, linha, van e pinos | o front (`shared/mapcn`) |
| **CARTO basemaps** | estilo e dados do fundo (ruas e bairros), claro e escuro | o navegador, sem chave |

O CARTO não calcula rota: só entrega o fundo. A rota vem sempre do OpenRouteService.

**Situação (14/11):** o problema antigo do worker da MapLibre sumiu com a troca para o `mapcn`. O mapa carregou sem erro, em viagem, para o motorista e para o pai.

**Antes de publicar:** os basemaps gratuitos do CARTO têm termos de uso próprios, com limite de uso e restrição para uso comercial. Confira os termos atuais no site do CARTO; se não servirem, troque o estilo em `shared/mapcn/map.tsx` por outro provedor.

---

## 4. HTTPS é obrigatório

- O navegador só libera o GPS em HTTPS (ou `localhost`). Sem isso, o motorista não consegue iniciar a viagem com localização.
- Com HTTPS, os WebSockets viram `wss://`. O `wsUrl` já faz essa troca.
- Para testar no celular agora, sem deploy: túnel (cloudflared) apontando para o `ng serve`.

---

## 5. O que ainda não existe

- Provedor de e-mail e plano de mapas, se o Gmail (cerca de 500 e-mails por dia) e o plano grátis do OpenRouteService não derem conta.

---

## 6. Antes de publicar

- `ALLOWED_ORIGINS` com o domínio do site (vale para o CORS e os WebSockets).
- O front é pesado: 869 kB brutos, cerca de 205 kB comprimidos. O Nginx precisa de `gzip on`, e o mapa (1 MB) já só carrega nas telas de viagem. Dá para reduzir carregando mais telas sob demanda.
- O build avisa que passou do limite de 500 kB. Só é aviso: não impede o deploy.

---

## Problemas do ambiente local

- **Porta 5432 ocupada:** se o PostgreSQL do sistema estiver ligado, o `vanroute-db` não sobe. Pare o do sistema (`sudo systemctl stop postgresql`).
- **Fedora (SELinux):** resolvido com o `:z` no mount do `docker/postgres-init` no `docker-compose.yml`.
- **`api-tests/demo/seed.sh`** não tem permissão de execução: rode com `bash backend/api-tests/demo/seed.sh`.
- **`api-tests/run-api-tests.sh`** fala direto com o backend (porta 9090) e com o MinIO (porta 9000): rode com o backend fora do Docker, não pelo Nginx.
