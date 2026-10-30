#!/usr/bin/env bash
# Cria um cenario pronto pra testar a execucao da rota na pagina demo (dados ficticios, base isolada).
# Pre-requisitos: os mesmos do run-api-tests.sh (backend em DATABASE_NAME=vanroute_audit + stubs.py pro e-mail).
#
#   IDA:   1 motorista com van, 1 escola, 2 pais (1 filho cada, contrato ativo); rota "Ida Manha": casa -> casa -> escola
#   VOLTA: outro motorista, outra escola, outros 2 pais; rota "Volta Tarde": escola -> casa -> casa (todos os dias da semana)
#
# Motorista e van entram por SQL (o cadastro real exige CNH e CRLV digitais assinados).
# Saida: logins (CPF / Senha@123) e ids em api-tests/out/demo.json
set -euo pipefail
BASE=${BASE:-http://localhost:9090}
DB=${DB:-vanroute_audit}
OUT="$(cd "$(dirname "$0")/.." && pwd)/out"; mkdir -p "$OUT"
RUN=$RANDOM$RANDOM

sql() { docker exec -i vanroute-db psql -U postgres -d "$DB" -tA "$@"; }
cpf() { python3 -I -c '
import random
d=[random.randint(0,9) for _ in range(9)]
for n in (10,11):
    s=sum(x*w for x,w in zip(d,range(n,1,-1)));r=(s*10)%11;d.append(0 if r==10 else r)
print("".join(map(str,d)))'; }

# api METODO URL TOKEN [JSON] -> imprime o corpo; para se nao for 2xx
api() {
  local args=(-s -w '\n%{http_code}' -X "$1" "$BASE$2")
  [ -n "$3" ] && args+=(-H "Authorization: Bearer $3")
  [ -n "${4:-}" ] && args+=(-H 'Content-Type: application/json' -d "$4")
  local res; res=$(curl "${args[@]}")
  local code=${res##*$'\n'} body=${res%$'\n'*}
  if [[ ! "$code" =~ ^2 ]]; then echo "ERRO $1 $2 -> $code $body" >&2; exit 1; fi
  echo "$body"
}

addr() { echo "{\"street\":\"$1\",\"zipCode\":\"$2\",\"city\":\"Sao Paulo\",\"neighborhood\":\"$3\",\"number\":$4,\"state\":\"SP\"}"; }

parent() { # parent NOME RUA CEP BAIRRO NUM -> "cpf token"
  local c e; c=$(cpf); e="pai$RANDOM.$RUN@teste.local"
  api POST /api/responsible/auth/signup "" "{\"name\":\"$1\",\"email\":\"$e\",\"password\":\"Senha@123\",\"confirmPassword\":\"Senha@123\",\"cpf\":\"$c\",\"phone\":\"11$RANDOM$RANDOM\",\"address\":$(addr "$2" "$3" "$4" "$5")}" >/dev/null
  local code; code=$(docker exec vanroute-redis redis-cli GET "verificationEmail:email:$e")
  api POST /api/auth/verify-email "" "{\"email\":\"$e\",\"code\":\"$code\"}" >/dev/null
  echo "$c $(api POST /api/auth/login "" "{\"cpf\":\"$c\",\"passwordHash\":\"Senha@123\"}" | jq -r .token)"
}

HASH=""
driver() { # driver NOME -> "cpf token linkCode vanId placa" (motorista e van por SQL, ja aprovados)
  local c; c=$(cpf); local plate; plate="DEM$(printf '%04d' $((RANDOM % 10000)))"
  sql -q <<SQL >/dev/null
INSERT INTO roles_tb (nome) VALUES ('ROLE_DRIVER') ON CONFLICT (nome) DO NOTHING;
WITH u AS (INSERT INTO users (id,name,password_hash,cpf,email,phone,status,created_at)
           VALUES (gen_random_uuid(),'$1','$HASH','$c','motorista$RANDOM.$RUN@teste.local','21$RANDOM$RANDOM','ACTIVE',now()) RETURNING id)
, r AS (INSERT INTO user_roles (user_id, role_id) SELECT u.id, (SELECT id FROM roles_tb WHERE nome='ROLE_DRIVER') FROM u)
, d AS (INSERT INTO drivers (user_id, approval_status, link_code) SELECT id,'APPROVED',upper(substr(md5(random()::text),1,9)) FROM u RETURNING user_id)
, v AS (INSERT INTO vehicles (id, plate, model, year, capacity, status) VALUES (gen_random_uuid(),'$plate','Van Demo',2020,15,'ACTIVE') RETURNING id)
INSERT INTO driver_vehicles (id, driver_id, vehicle_id, start_date) SELECT gen_random_uuid(), d.user_id, v.id, current_date FROM d, v;
SQL
  local t; t=$(api POST /api/auth/login "" "{\"cpf\":\"$c\",\"passwordHash\":\"Senha@123\"}" | jq -r .token)
  echo "$c $t $(api GET /api/users/me "$t" | jq -r .linkCode) $(api GET /api/vehicle/my-vehicles "$t" | jq -r '.[0].id') $plate"
}
school() { # school TOKEN_MOTORISTA NOME RUA CEP BAIRRO NUM -> id
  api POST /api/school/add-school "$1" "{\"name\":\"$2 $RUN\",\"phone\":\"1133334444\",\"email\":\"escola$RANDOM.$RUN@teste.local\",\"addressRequestDTO\":$(addr "$3" "$4" "$5" "$6")}" | jq -r .id
}
kid() { # kid TOKEN_PAI NOME RUA CEP BAIRRO NUM -> "id do aluno  id do endereco Casa dele"
  api POST /api/student/create-student "$1" "{\"name\":\"$2\",\"birthDate\":\"2016-05-10\",\"relationType\":\"FINANCIAL\",\"address\":$(addr "$3" "$4" "$5" "$6")}" \
    | jq -r '"\(.studentResponseDTO.id) \(.studentResponseDTO.studentAddressResponseDTO.id)"'
}
contract() { # contract TOKEN_MOTORISTA LINK ESCOLA TOKEN_PAI ALUNO: chat pelo codigo do motorista, proposta e aceite
  local parentId; parentId=$(api GET /api/users/me "$4" | jq -r .id)
  local chat; chat=$(api POST /api/invite-code/redeem "$4" "{\"inviteCode\":\"$2\"}" | jq -r .)
  local c; c=$(api POST "/api/chats/$chat/contract" "$1" "{\"responsibleId\":\"$parentId\",\"studentId\":\"$5\",\"schoolId\":\"$3\",\"contract\":{\"periodicity\":\"MONTHLY\",\"value\":350.00,\"startDate\":\"2027-02-01\",\"endDate\":\"2027-06-30\"},\"document\":{\"url\":\"demo\",\"type\":\"TRANSPORT_CONTRACT\"}}" | jq -r .contract.contractId)
  api PUT "/api/chats/$chat/contracts/$c/accept" "$4" >/dev/null
}
# "adicionar aluno na rota" num passo so: o backend usa/cria o ponto do endereco na ordem certa e liga o aluno
add_student() { # add_student TOKEN_MOTORISTA ROTA ALUNO ACAO LOCAL [ID_ENDERECO_DO_ALUNO]
  api POST "/api/route/$2/add-student/$3" "$1" "{\"action\":\"$4\",\"locationType\":\"$5\"${6:+,\"studentAddressId\":\"$6\"}}" >/dev/null
}

echo "== IDA (manha): casa -> casa -> escola"
echo "pais..."
read -r P1_CPF P1 <<<"$(parent 'Pai Demo Um' 'Rua Augusta' '01304-000' 'Consolacao' 500)"
read -r P2_CPF P2 <<<"$(parent 'Mae Demo Dois' 'Rua Haddock Lobo' '01414-000' 'Cerqueira Cesar' 400)"
HASH=$(sql -c "SELECT password_hash FROM users WHERE cpf='$P1_CPF'")
echo "motorista, van e escola..."
read -r D_CPF D LINK VAN PLATE <<<"$(driver 'Motorista Demo')"
SCHOOL=$(school "$D" 'Escola Demo' 'Rua Vergueiro' '01504-000' 'Liberdade' 1000)
echo "filhos e contratos..."
read -r K1 K1_HOME <<<"$(kid "$P1" 'Aluno Demo Um' 'Rua Augusta' '01304-000' 'Consolacao' 500)"
read -r K2 K2_HOME <<<"$(kid "$P2" 'Aluna Demo Dois' 'Rua Haddock Lobo' '01414-000' 'Cerqueira Cesar' 400)"
contract "$D" "$LINK" "$SCHOOL" "$P1" "$K1"
contract "$D" "$LINK" "$SCHOOL" "$P2" "$K2"
echo "rota..."
ROUTE=$(api POST /api/route/create-route "$D" '{"name":"Ida Manha"}' | jq -r .id)
# num passo so: o backend cria os pontos na ordem (casa 1, casa 2, escola) e liga os alunos
add_student "$D" "$ROUTE" "$K1" BOARDING HOME "$K1_HOME"
add_student "$D" "$ROUTE" "$K2" BOARDING HOME "$K2_HOME"
add_student "$D" "$ROUTE" "$K1" ALIGHTING SCHOOL
add_student "$D" "$ROUTE" "$K2" ALIGHTING SCHOOL
api PUT "/api/route/$ROUTE/set-vehicle/$VAN" "$D" >/dev/null

echo "== VOLTA (tarde): escola -> casa -> casa (outro motorista e outros pais)"
echo "pais..."
read -r P3_CPF P3 <<<"$(parent 'Pai Demo Tres' 'Rua dos Pinheiros' '05422-000' 'Pinheiros' 500)"
read -r P4_CPF P4 <<<"$(parent 'Mae Demo Quatro' 'Rua Cardeal Arcoverde' '05407-000' 'Pinheiros' 800)"
echo "motorista, van e escola..."
read -r D2_CPF D2 LINK2 VAN2 PLATE2 <<<"$(driver 'Motorista Demo Volta')"
SCHOOL2=$(school "$D2" 'Escola Demo Volta' 'Rua Teodoro Sampaio' '05405-000' 'Pinheiros' 1000)
echo "filhos e contratos..."
read -r K3 K3_HOME <<<"$(kid "$P3" 'Aluno Demo Tres' 'Rua dos Pinheiros' '05422-000' 'Pinheiros' 500)"
read -r K4 K4_HOME <<<"$(kid "$P4" 'Aluna Demo Quatro' 'Rua Cardeal Arcoverde' '05407-000' 'Pinheiros' 800)"
contract "$D2" "$LINK2" "$SCHOOL2" "$P3" "$K3"
contract "$D2" "$LINK2" "$SCHOOL2" "$P4" "$K4"
echo "rota..."
ROUTE2=$(api POST /api/route/create-route "$D2" '{"name":"Volta Tarde"}' | jq -r .id)
# volta: a escola entra antes das casas sozinha
add_student "$D2" "$ROUTE2" "$K3" ALIGHTING HOME "$K3_HOME"
add_student "$D2" "$ROUTE2" "$K4" ALIGHTING HOME "$K4_HOME"
add_student "$D2" "$ROUTE2" "$K3" BOARDING SCHOOL
add_student "$D2" "$ROUTE2" "$K4" BOARDING SCHOOL
api PUT "/api/route/$ROUTE2/set-vehicle/$VAN2" "$D2" >/dev/null

jq -n --arg d "$D_CPF" --arg p1 "$P1_CPF" --arg p2 "$P2_CPF" --arg route "$ROUTE" --arg k1 "$K1" --arg k2 "$K2" --arg plate "$PLATE" \
  --arg d2 "$D2_CPF" --arg p3 "$P3_CPF" --arg p4 "$P4_CPF" --arg route2 "$ROUTE2" --arg k3 "$K3" --arg k4 "$K4" --arg plate2 "$PLATE2" \
  '{senha:"Senha@123", motorista:$d, pai1:$p1, pai2:$p2, routeId:$route, filhoPai1:$k1, filhoPai2:$k2, placa:$plate,
    motorista2:$d2, pai3:$p3, pai4:$p4, routeId2:$route2, filhoPai3:$k3, filhoPai4:$k4, placa2:$plate2}' > "$OUT/demo.json"
echo
echo "Pronto. Senha de todos: Senha@123"
echo " IDA   motorista  CPF $D_CPF  (van $PLATE, rota Ida Manha)"
echo "       pai 1      CPF $P1_CPF  (Aluno Demo Um, Rua Augusta)"
echo "       pai 2      CPF $P2_CPF  (Aluna Demo Dois, Rua Haddock Lobo)"
echo " VOLTA motorista  CPF $D2_CPF  (van $PLATE2, rota Volta Tarde)"
echo "       pai 3      CPF $P3_CPF  (Aluno Demo Tres, Rua dos Pinheiros)"
echo "       pai 4      CPF $P4_CPF  (Aluna Demo Quatro, Rua Cardeal Arcoverde)"
echo "Tambem salvo em $OUT/demo.json"
