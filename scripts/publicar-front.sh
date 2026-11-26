#!/usr/bin/env bash
# Gera a imagem do site (frontend/Dockerfile) e manda pro registro (Docker Hub, GHCR...),
# pra o servidor so baixar, sem rodar o npm build la.
#
# Uso, da raiz do projeto (uma vez antes: docker login):
#   bash scripts/publicar-front.sh seu-usuario/vanroute-frontend          -> tag latest
#   bash scripts/publicar-front.sh seu-usuario/vanroute-frontend v1.2     -> tag v1.2 e latest
# Sem argumento, usa o FRONTEND_IMAGE do .env.
#
# Gera pra linux/amd64 (Intel/AMD) e linux/arm64 (ex.: Oracle Ampere). O npm build roda uma vez so.
# PLATAFORMAS=linux/amd64 bash scripts/publicar-front.sh ...  -> so uma arquitetura (mais rapido)
set -euo pipefail

cd "$(dirname "$0")/.."

imagem="${1:-}"
if [[ -z "$imagem" && -f .env ]]; then
  imagem="$(grep -E '^FRONTEND_IMAGE=' .env | tail -1 | cut -d= -f2- || true)"
fi
if [[ -z "$imagem" ]]; then
  echo "Diga a imagem: bash scripts/publicar-front.sh seu-usuario/vanroute-frontend [versao]" >&2
  exit 1
fi
imagem="${imagem%%:*}"   # tira a tag, se veio junto
versao="${2:-}"
plataformas="${PLATAFORMAS:-linux/amd64,linux/arm64}"

tags=(-t "$imagem:latest")
[[ -n "$versao" ]] && tags+=(-t "$imagem:$versao")

# imagem de varias arquiteturas precisa de um builder "docker-container" (o padrao do Docker nao gera)
if ! docker buildx inspect vanroute-builder >/dev/null 2>&1; then
  docker buildx create --name vanroute-builder --driver docker-container >/dev/null
fi

echo "Gerando $imagem (${versao:-latest}) para $plataformas..."
docker buildx build \
  --builder vanroute-builder \
  --platform "$plataformas" \
  "${tags[@]}" \
  --push \
  ./frontend

echo
echo "Pronto: $imagem:${versao:-latest}"
echo "No servidor: FRONTEND_IMAGE=$imagem:${versao:-latest} no .env e"
echo "  docker compose -f docker-compose.yml -f docker-compose.prod.yml --profile app up -d --build"
