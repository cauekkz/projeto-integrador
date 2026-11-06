# Deploy: problemas conhecidos

**O app ainda não foi publicado.** Nada aqui atrapalha o desenvolvimento local: são os pontos a resolver quando chegar a hora de publicar.

A lista geral de produção (perfil, segredos, login, fuso, worker) está em [`PENDENCIAS_E_ERROS.md`, seção 3](PENDENCIAS_E_ERROS.md). A preparação para produção é a issue #187.

---

## 1. Front não fala com o backend no Docker

**Situação:** o `frontend/Dockerfile` e o `frontend/nginx.conf` existem, mas ainda não são usados. O `docker-compose.yml` não tem o front nem o backend.

**O problema:** o navegador sempre pede `/api/...` para o mesmo endereço do site.
- **No PC:** o `ng serve` recebe esse pedido e repassa ao backend, porque tem o `proxy.conf.json`.
- **No Docker:** quem recebe é o Nginx, que hoje só entrega os arquivos do site. Um `GET /api/chats` volta com o `index.html` no lugar dos dados, e o app quebra.

**Correção:** o Nginx faz o mesmo que o `proxy.conf.json`. Exemplo, ainda não testado (`backend` e `minio` são os nomes dos serviços no Docker):

```nginx
client_max_body_size 11m;   # o padrão é 1 MB; a CNH e o CRLV sobem por aqui

location /api/ {
    proxy_pass http://backend:9090;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;   # o limite de login por IP usa isso
    proxy_set_header X-Forwarded-Proto $scheme;
}

location /ws/ {
    proxy_pass http://backend:9090/;          # a barra final tira o /ws
    proxy_http_version 1.1;
    proxy_set_header Upgrade $http_upgrade;
    proxy_set_header Connection "upgrade";
    proxy_read_timeout 1h;                    # sem isso, a conexão cai a cada 60 s
    access_log off;                           # o token vai na URL: não pode ir pro log
}

location /storage/ {
    proxy_pass http://minio:9000/;
    client_max_body_size 11m;
}
```

Fica pendente o item 2, porque o link assinado depende do endereço do MinIO.

---

## 2. Links de upload e download do MinIO

**O problema:** o backend assina os links com o `MINIO_ENDPOINT` (hoje `http://localhost:9000`). A função `urlDoStorage` do front só troca esse endereço exato por `/storage`, e a assinatura só vale para o endereço com que foi gerada.

**Em produção é preciso decidir:**
- **MinIO com domínio próprio:** `MINIO_ENDPOINT` com o domínio público, HTTPS e CORS liberado para o site. A `urlDoStorage` passa a não trocar nada.
- **MinIO atrás do mesmo Nginx:** o proxy precisa mandar o mesmo `Host` usado na assinatura. Ajustar a `urlDoStorage` para o endereço real.

---

## 3. Mapa vetorial nunca carrega

**O que cada serviço faz:**

| Serviço | Papel | Quem chama |
|---|---|---|
| **OpenRouteService** | geocodifica endereços e calcula o trajeto e o tempo (a linha azul e o "em 10 min") | o backend, com a chave |
| **OpenFreeMap** | desenho do mapa (ruas e bairros), vetorial. É o escolhido | o navegador, sem chave |
| **OpenStreetMap** | desenho do mapa, em imagens prontas. É o plano B | o navegador, sem chave |

O OpenStreetMap e o OpenFreeMap não calculam rota: só desenham o fundo. A rota vem sempre do OpenRouteService, e só um dos dois fundos aparece por vez.

**O problema:** a MapLibre 6 carrega um arquivo auxiliar (`maplibre-gl-worker.mjs`), e o build do Angular não o copia. O console mostra `Worker failed to load`, e o mapa cai sempre no plano B, tanto em desenvolvimento quanto no build de produção.

**Por que importa:** o usuário não percebe, mas o servidor público do OpenStreetMap não é feito para uso pesado de um app em produção e pode bloquear.

**Correção:** a receita testada está em [`FLUXO_FRONT.md`, seção 2.3](FLUXO_FRONT.md). Sem ela, não publique.

---

## 4. HTTPS é obrigatório

- O navegador só libera o GPS em HTTPS (ou `localhost`). Sem isso, o motorista não consegue iniciar a viagem com localização.
- Com HTTPS, os WebSockets viram `wss://`. O `wsUrl` já faz essa troca.
- Para testar no celular agora, sem deploy: túnel (cloudflared) apontando para o `ng serve`.

---

## 5. O que ainda não existe

- `Dockerfile` do backend.
- Front e backend no `docker-compose.yml` (ou um compose de produção).
- HTTPS, backup do banco, logs e monitoramento.
- Provedor de e-mail e plano de mapas, se o Gmail e o plano grátis não derem conta.

---

## 6. Antes de publicar

- `ALLOWED_ORIGINS` com o domínio do site (vale para o CORS e os WebSockets).
- O front é pesado: 869 kB brutos, cerca de 205 kB comprimidos. O Nginx precisa de `gzip on`, e o mapa (1 MB) já só carrega nas telas de viagem. Dá para reduzir carregando mais telas sob demanda.
- O build avisa que passou do limite de 500 kB. Só é aviso: não impede o deploy.

---

## Problemas do ambiente local

Não são de deploy, mas já pararam o projeto em máquina de desenvolvimento:

- **Porta 5432 ocupada:** se o PostgreSQL do sistema estiver ligado, o `vanroute-db` não sobe. Pare o do sistema com `sudo systemctl stop postgresql`.
- **Fedora (SELinux):** com o volume do banco novo, o container não consegue ler `docker/postgres-init` e fica reiniciando (`Permission denied`). O mount precisa de `:z` no `docker-compose.yml`, que ainda não tem.
- **`api-tests/demo/seed.sh`** não tem permissão de execução: rode com `bash api-tests/demo/seed.sh`.
