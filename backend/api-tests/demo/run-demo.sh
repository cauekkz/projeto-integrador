#!/usr/bin/env bash
# Sobe tudo pra pagina de teste com um comando:
#   bash backend/api-tests/demo/run-demo.sh      -> abre http://localhost:8090 ; Ctrl+C desliga tudo
#   DEMO_PORT=8091 bash ...                       -> pagina em outra porta
#   DEMO_MAPS=sim bash ...                        -> usa o simulador de mapas mesmo com chave (ex.: cota diaria da chave acabou)
#
# - banco ISOLADO vanroute_audit (o seu vanroute_db nao e tocado), Redis e MinIO do docker compose
# - e-mail falso (stubs.py): o cadastro dos pais funciona sem mandar e-mail de verdade
# - mapas: com a chave em application-local.properties usa o OpenRouteService real; sem ela, o simulador
# - cria o cenario (seed.sh) e serve a pagina (serve.py)
set -euo pipefail
ROOT=$(cd "$(dirname "$0")/../../.." && pwd)
BACK=$ROOT/backend
OUT=$BACK/api-tests/out
JAR=$BACK/target/backend-0.0.1-SNAPSHOT.jar
PAGE_PORT=${DEMO_PORT:-8090}
mkdir -p "$OUT"

for port in 9090 8089 2525 "$PAGE_PORT"; do
  if ss -ltn | grep -q ":$port "; then
    echo "A porta $port ja esta em uso (seu backend ou outra demo rodando?). Pare antes e rode de novo."; exit 1
  fi
done

docker compose -f "$ROOT/docker-compose.yml" up -d db redis minio >/dev/null
docker exec vanroute-db psql -U postgres -tAc "SELECT 1 FROM pg_database WHERE datname='vanroute_audit'" | grep -q 1 \
  || docker exec vanroute-db psql -U postgres -c 'CREATE DATABASE vanroute_audit' >/dev/null

# recompila se o codigo mudou depois do ultimo jar
if [ ! -f "$JAR" ] || [ -n "$(find "$BACK/src/main" -newer "$JAR" -print -quit)" ]; then
  echo "compilando o backend..."
  (cd "$BACK" && ./mvnw -q -DskipTests package)
fi

pids=()
cleanup() { kill "${pids[@]}" 2>/dev/null || true; }
trap cleanup EXIT INT TERM

python3 -I "$BACK/api-tests/stubs.py" > "$OUT/stubs.log" 2>&1 & pids+=($!)

maps=()
if [ "${DEMO_MAPS:-}" != sim ] && grep -q '^openrouteservice.api-key=.\+' "$BACK/src/main/resources/application-local.properties" 2>/dev/null; then
  echo "mapas: OpenRouteService real (sua chave)"
else
  echo "mapas: simulador (DEMO_MAPS=sim ou sem chave em application-local.properties)"
  maps=(ORS_GEOCODE_URL=http://127.0.0.1:8089/pelias/v1/search ORS_DIRECTIONS_URL=http://127.0.0.1:8089/v2/directions/driving-car)
fi

echo "subindo o backend (log em $OUT/backend.log)..."
env DATABASE_NAME=vanroute_audit MINIO_BUCKET_NAME=vanroute-audit \
  SPRING_MAIL_HOST=127.0.0.1 SPRING_MAIL_PORT=2525 SPRING_MAIL_USERNAME=stub SPRING_MAIL_PASSWORD=stub \
  SPRING_MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_ENABLE=false TRIP_ETA_REFRESH_SECONDS=${TRIP_ETA_REFRESH_SECONDS:-120} \
  "${maps[@]}" java -jar "$JAR" --spring.jpa.show-sql=false > "$OUT/backend.log" 2>&1 & pids+=($!)

backend=${pids[-1]}
ready=0
for _ in $(seq 1 90); do
  kill -0 "$backend" 2>/dev/null || { echo "o backend caiu ao subir; veja $OUT/backend.log"; tail -20 "$OUT/backend.log"; exit 1; }
  # pronto = o log desse processo diz "Started" e a porta responde
  if grep -q "Started BackendApplication" "$OUT/backend.log" && curl -s -o /dev/null http://localhost:9090/; then ready=1; break; fi
  sleep 2
done
[ "$ready" = 1 ] || { echo "o backend nao ficou pronto em 3 min; veja $OUT/backend.log"; exit 1; }

bash "$BACK/api-tests/demo/seed.sh"
echo
echo "Abra http://localhost:$PAGE_PORT  (Ctrl+C aqui desliga tudo)"
PORT=$PAGE_PORT python3 -I "$BACK/api-tests/demo/serve.py" & pids+=($!)
wait
