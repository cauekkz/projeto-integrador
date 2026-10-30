#!/usr/bin/env bash
# Testes de API do VanRoute em ordem de dependencia (ver docs/RELATORIO_AUDITORIA_VANROUTE.md).
#
# Pre-requisitos (ver README do relatorio):
#   docker compose up -d db redis minio
#   docker exec vanroute-db psql -U postgres -c 'CREATE DATABASE vanroute_audit'   # base isolada
#   python3 -I backend/api-tests/stubs.py &                                          # SMTP + geocoder locais
#   backend iniciado com DATABASE_NAME=vanroute_audit, SPRING_MAIL_HOST=127.0.0.1, SPRING_MAIL_PORT=2525,
#   SPRING_MAIL_USERNAME/PASSWORD=stub, SPRING_MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_ENABLE=false,
#   ORS_GEOCODE_URL=http://127.0.0.1:8089/pelias/v1/search, MINIO_BUCKET_NAME=vanroute-audit
#   e, pra trip: ORS_DIRECTIONS_URL=http://127.0.0.1:8089/v2/directions/driving-car, TRIP_ETA_REFRESH_SECONDS=3,
#   TRIP_OFF_ROUTE_MIN_SECONDS=0
#
# Cada execucao cria dados ficticios novos (CPFs validos aleatorios), entao pode ser repetida.
# Motoristas sao inseridos via SQL na base isolada porque o cadastro exige uma CNH digital
# assinada ICP-Brasil real, impossivel de gerar para dados ficticios.
set -u
BASE=${BASE:-http://localhost:9090}
DB=${DB:-vanroute_audit}
# REAL_MAPS=1: backend usando a API real do OpenRouteService (sem ORS_*_URL apontando pro stubs.py).
# A linha real segue as ruas e as chamadas nao passam pelo log do simulador: a contagem de chamadas fica de fora (SKIP)
REAL_MAPS=${REAL_MAPS:-0}
PASS=0; FAIL=0; SKIP=0; RESULTS=()
BODY=""; CODE=""

psql_audit() { docker exec -i vanroute-db psql -U postgres -d "$DB" -tA "$@"; }
redis() { docker exec vanroute-redis redis-cli "$@"; }

cpf() { python3 -I -c '
import random
d=[random.randint(0,9) for _ in range(9)]
for n in (10,11):
    s=sum(x*w for x,w in zip(d,range(n,1,-1)));r=(s*10)%11;d.append(0 if r==10 else r)
print("".join(map(str,d)))'; }

# req NOME ESPERADO METODO URL [TOKEN] [JSON]
req() {
  local name=$1 expected=$2 method=$3 url=$4 token=${5:-} data=${6:-}
  local args=(-s -o /tmp/vr_body -w '%{http_code}' -X "$method" "$BASE$url")
  [ -n "$token" ] && args+=(-H "Authorization: Bearer $token")
  [ -n "$data" ] && args+=(-H 'Content-Type: application/json' -d "$data")
  CODE=$(curl "${args[@]}"); BODY=$(cat /tmp/vr_body)
  check "$name" "$expected" "$CODE"
}

check() {
  local name=$1 expected=$2 got=$3
  if [[ "$got" =~ ^($expected)$ ]]; then PASS=$((PASS+1)); RESULTS+=("PASS | $name | esperado $expected | obtido $got")
  else FAIL=$((FAIL+1)); RESULTS+=("FAIL | $name | esperado $expected | obtido $got | ${BODY:0:160}"); fi
}

skip_real() { # skip_real NOME: conferencia que so existe com o simulador
  SKIP=$((SKIP+1)); RESULTS+=("SKIP | $1 (so com o simulador)")
}

assert() { # assert NOME CONDICAO_BASH
  if eval "$2"; then PASS=$((PASS+1)); RESULTS+=("PASS | $1"); else FAIL=$((FAIL+1)); RESULTS+=("FAIL | $1"); fi
}

RUN=$RANDOM$RANDOM
# enderecos sao ruas publicas reais de Sao Paulo: com a API real, rua inventada e recusada
addr='{"street":"Avenida Paulista","zipCode":"01310-100","city":"Sao Paulo","neighborhood":"Bela Vista","number":1000,"state":"SP"}'

signup_responsible() { # signup_responsible CPF EMAIL PHONE -> cria e ativa; imprime token
  req "responsible signup ($2)" 200 POST /api/responsible/auth/signup "" \
    "{\"name\":\"Resp Teste\",\"email\":\"$2\",\"password\":\"Senha@123\",\"confirmPassword\":\"Senha@123\",\"cpf\":\"$1\",\"phone\":\"$3\",\"address\":$addr}"
}

verify_and_login() { # verify_and_login CPF EMAIL VAR
  local code; code=$(redis GET "verificationEmail:email:$2")
  req "verify-email ($2)" 200 POST /api/auth/verify-email "" "{\"email\":\"$2\",\"code\":\"$code\"}"
  req "login ($2)" 200 POST /api/auth/login "" "{\"cpf\":\"$1\",\"passwordHash\":\"Senha@123\"}"
  printf -v "$3" '%s' "$(echo "$BODY" | jq -r .token)"
}

seed_driver() { # seed_driver CPF EMAIL PHONE HASH -> imprime link_code
  local link; link=$(python3 -I -c 'import random;print("".join(random.choice("ABCDEFGHJKLMNPQRSTUVWXYZ23456789") for _ in range(9)))')
  psql_audit -q <<SQL >/dev/null
INSERT INTO roles_tb (nome) VALUES ('ROLE_DRIVER') ON CONFLICT (nome) DO NOTHING;
WITH u AS (INSERT INTO users (id,name,password_hash,cpf,email,phone,status,created_at)
           VALUES (gen_random_uuid(),'Motorista Teste','$4','$1','$2','$3','ACTIVE',now()) RETURNING id)
, r AS (INSERT INTO user_roles (user_id, role_id) SELECT u.id, (SELECT id FROM roles_tb WHERE nome='ROLE_DRIVER') FROM u)
INSERT INTO drivers (user_id, approval_status, link_code) SELECT id,'APPROVED','$link' FROM u;
SQL
  echo "$link"
}

echo "== 1. Infra e rotas publicas"
req "openapi docs publico" 200 GET /v3/api-docs
req "rota protegida sem token" 401 GET /api/chats
req "token invalido" 401 GET /api/chats "token.invalido.xyz"

echo "== 2. Cadastro, verificacao de e-mail e login (responsaveis)"
R1_CPF=$(cpf); R1_EMAIL="r1.$RUN@teste.local"; R1_PHONE="11$RUN"1
R2_CPF=$(cpf); R2_EMAIL="r2.$RUN@teste.local"; R2_PHONE="11$RUN"2
signup_responsible "$R1_CPF" "$R1_EMAIL" "$R1_PHONE"
assert "signup nao expoe senha" "! grep -qi 'senha@123\|password' <<<\"\$BODY\""
assert "signup geocodificou endereco" "[ \"\$(echo \"\$BODY\" | jq .address.latitude)\" != null ]"
req "signup duplicado" 409 POST /api/responsible/auth/signup "" \
  "{\"name\":\"Dup\",\"email\":\"$R1_EMAIL\",\"password\":\"Senha@123\",\"confirmPassword\":\"Senha@123\",\"cpf\":\"$R1_CPF\",\"phone\":\"$R1_PHONE\",\"address\":$addr}"
req "signup CPF invalido" 400 POST /api/responsible/auth/signup "" \
  "{\"name\":\"X\",\"email\":\"x$RUN@teste.local\",\"password\":\"Senha@123\",\"confirmPassword\":\"Senha@123\",\"cpf\":\"12345678900\",\"phone\":\"1\",\"address\":$addr}"
req "signup senhas diferentes" 400 POST /api/responsible/auth/signup "" \
  "{\"name\":\"X\",\"email\":\"x$RUN@teste.local\",\"password\":\"Senha@123\",\"confirmPassword\":\"Outra@123\",\"cpf\":\"$(cpf)\",\"phone\":\"2\",\"address\":$addr}"
req "signup sem endereco" 400 POST /api/responsible/auth/signup "" \
  "{\"name\":\"X\",\"email\":\"x$RUN@teste.local\",\"password\":\"Senha@123\",\"confirmPassword\":\"Senha@123\",\"cpf\":\"$(cpf)\",\"phone\":\"3\"}"
req "signup endereco nao geocodificavel" 400 POST /api/responsible/auth/signup "" \
  "{\"name\":\"X\",\"email\":\"y$RUN@teste.local\",\"password\":\"Senha@123\",\"confirmPassword\":\"Senha@123\",\"cpf\":\"$(cpf)\",\"phone\":\"4$RUN\",\"address\":{\"street\":\"NAOEXISTE\",\"zipCode\":\"0\",\"city\":\"X\"}}"
req "signup JSON malformado" 400 POST /api/responsible/auth/signup "" "{nao-json"
req "login antes de verificar e-mail" 400 POST /api/auth/login "" "{\"cpf\":\"$R1_CPF\",\"passwordHash\":\"Senha@123\"}"
req "verify-email codigo errado" 400 POST /api/auth/verify-email "" "{\"email\":\"$R1_EMAIL\",\"code\":\"000000x\"}"
req "send-verification-code" 200 POST "/api/auth/send-verification-code?email=$R1_EMAIL"
req "send-verification-code e-mail inexistente" 400 POST "/api/auth/send-verification-code?email=nao.$RUN@teste.local"
verify_and_login "$R1_CPF" "$R1_EMAIL" R1
R1_REFRESH=$(echo "$BODY" | jq -r .refreshToken)
assert "login devolve refreshToken (30 dias)" "[ \${#R1_REFRESH} -gt 30 ] && [ \"\$(echo \"\$BODY\" | jq .refreshExpiresIn)\" = 2592000000 ]"
req "refresh: troca por um par novo" 200 POST /api/auth/refresh "" "{\"refreshToken\":\"$R1_REFRESH\"}"
R1_REFRESH2=$(echo "$BODY" | jq -r .refreshToken); R1_TOKEN2=$(echo "$BODY" | jq -r .token)
assert "refresh: par novo e diferente do anterior" "[ -n \"$R1_TOKEN2\" ] && [ \"$R1_REFRESH2\" != \"$R1_REFRESH\" ]"
req "refresh: o JWT novo funciona" 200 GET /api/users/me "$R1_TOKEN2"
req "refresh: o refreshToken usado nao vale de novo" 401 POST /api/auth/refresh "" "{\"refreshToken\":\"$R1_REFRESH\"}"
req "refresh: reuso = roubo, derruba a sessao (o par novo tambem cai)" 401 POST /api/auth/refresh "" "{\"refreshToken\":\"$R1_REFRESH2\"}"
req "refresh: sem refreshToken" 400 POST /api/auth/refresh "" '{}'
req "login de novo (depois de derrubada)" 200 POST /api/auth/login "" "{\"cpf\":\"$R1_CPF\",\"passwordHash\":\"Senha@123\"}"
R1_REFRESH3=$(echo "$BODY" | jq -r .refreshToken)
req "logout" 204 POST /api/auth/logout "" "{\"refreshToken\":\"$R1_REFRESH3\"}"
req "refresh: depois do logout nao vale" 401 POST /api/auth/refresh "" "{\"refreshToken\":\"$R1_REFRESH3\"}"
req "login senha errada" 401 POST /api/auth/login "" "{\"cpf\":\"$R1_CPF\",\"passwordHash\":\"Errada@123\"}"
req "login CPF inexistente" 401 POST /api/auth/login "" "{\"cpf\":\"00000000000\",\"passwordHash\":\"Senha@123\"}"
req "login sem campos" 400 POST /api/auth/login "" "{}"
assert "JWT tem claim exp" "echo \"\$R1\" | cut -d. -f2 | tr '_-' '/+' | base64 -d 2>/dev/null | grep -q '\"exp\"'"
signup_responsible "$R2_CPF" "$R2_EMAIL" "$R2_PHONE"
verify_and_login "$R2_CPF" "$R2_EMAIL" R2

echo "== 3. Motoristas (seed SQL na base isolada) e validacao de CNH"
HASH=$(psql_audit -c "SELECT password_hash FROM users WHERE cpf='$R1_CPF'")
D1_CPF=$(cpf); D2_CPF=$(cpf)
D1_LINK=$(seed_driver "$D1_CPF" "d1.$RUN@teste.local" "21$RUN"1 "$HASH")
D2_LINK=$(seed_driver "$D2_CPF" "d2.$RUN@teste.local" "21$RUN"2 "$HASH")
req "login motorista D1" 200 POST /api/auth/login "" "{\"cpf\":\"$D1_CPF\",\"passwordHash\":\"Senha@123\"}"; D1=$(echo "$BODY" | jq -r .token)
req "login motorista D2" 200 POST /api/auth/login "" "{\"cpf\":\"$D2_CPF\",\"passwordHash\":\"Senha@123\"}"; D2=$(echo "$BODY" | jq -r .token)
printf '%%PDF-1.4\nCNH DIGITAL ficticia sem assinatura\n%%%%EOF\n' > /tmp/vr_fake.pdf
CODE=$(curl -s -o /tmp/vr_body -w '%{http_code}' -X POST "$BASE/api/driver/auth/verify-CNH" -F "documentPdf=@/tmp/vr_fake.pdf;type=application/pdf"); BODY=$(cat /tmp/vr_body)
check "verify-CNH PDF sem assinatura ICP" 422 "$CODE"
CODE=$(curl -s -o /tmp/vr_body -w '%{http_code}' -X POST "$BASE/api/driver/auth/signup" -F "documentPdf=@/tmp/vr_fake.pdf;type=application/pdf" -F name=X -F email=z$RUN@teste.local -F password=Senha@123 -F confirmPassword=Senha@123 -F phone=9$RUN); BODY=$(cat /tmp/vr_body)
check "driver signup CNH invalida" 422 "$CODE"
CODE=$(curl -s -o /tmp/vr_body -w '%{http_code}' -X POST "$BASE/api/driver/auth/signup" -F name=X); BODY=$(cat /tmp/vr_body)
check "driver signup campos ausentes" 400 "$CODE"

echo "== 4. Escolas (motorista)"
req "D1 cria escola" 200 POST /api/school/add-school "$D1" "{\"name\":\"Escola Teste $RUN\",\"phone\":\"1133334444\",\"email\":\"escola$RUN@teste.local\",\"addressRequestDTO\":{\"street\":\"Rua Vergueiro\",\"zipCode\":\"01504-000\",\"city\":\"Sao Paulo\",\"number\":1,\"state\":\"SP\"}}"
SCHOOL=$(echo "$BODY" | jq -r .id)
assert "escola com coordenadas persistidas" "[ \"\$(psql_audit -c \"SELECT a.latitude IS NOT NULL FROM schools s JOIN addresses a ON a.id=s.address_id WHERE s.id='$SCHOOL'\")\" = t ]"
assert "vinculo driver_schools criado" "[ \"\$(psql_audit -c \"SELECT count(*) FROM driver_schools WHERE school_id='$SCHOOL'\")\" = 1 ]"
SCHOOL_JSON="{\"name\":\"Escola Teste $RUN\",\"phone\":\"1133334444\",\"email\":\"escola$RUN@teste.local\",\"addressRequestDTO\":{\"street\":\"Rua Vergueiro\",\"zipCode\":\"01504-000\",\"city\":\"Sao Paulo\",\"number\":1,\"state\":\"SP\"}}"
req "D2 cadastra a mesma escola (vincula)" 200 POST /api/school/add-school "$D2" "$SCHOOL_JSON"
assert "D2 recebeu a escola existente" "[ \"\$(echo \"\$BODY\" | jq -r .id)\" = \"$SCHOOL\" ]"
assert "escola nao duplicada e 2 vinculos" "[ \"\$(psql_audit -c \"SELECT count(*) FROM driver_schools WHERE school_id='$SCHOOL'\")\" = 2 ]"
req "D2 lista suas escolas" 200 GET /api/school/my-schools "$D2"
assert "my-schools traz a escola com endereco" "echo \"\$BODY\" | jq -e '.[] | select(.id==\"$SCHOOL\" and .address.state==\"SP\" and (.name|length>0))' >/dev/null"
req "responsavel lista escolas de motorista" 403 GET /api/school/my-schools "$R1"
req "D2 cadastra a mesma escola de novo" 409 POST /api/school/add-school "$D2" "$SCHOOL_JSON"
req "responsavel tenta criar escola" 403 POST /api/school/add-school "$R1" "{\"name\":\"X\",\"email\":\"x@x\",\"addressRequestDTO\":$addr}"

echo "== 4b. Van (CRLV digital)"
CRLV=$(ls "$(cd "$(dirname "$0")/../.." && pwd)"/CRLV*.pdf* 2>/dev/null | head -1)
upload_crlv() { CODE=$(curl -s -o /tmp/vr_body -w '%{http_code}' -X POST "$BASE/api/vehicle/add-vehicle" -H "Authorization: Bearer $2" -F "documentPdf=@$1;type=application/pdf"); BODY=$(cat /tmp/vr_body); }
upload_crlv /tmp/vr_fake.pdf "$D1"; check "van: PDF que nao e CRLV assinado" 422 "$CODE"
upload_crlv /tmp/vr_fake.pdf "$R1"; check "van: responsavel nao cadastra van" 403 "$CODE"
req "van: responsavel lista vans" 403 GET /api/vehicle/my-vehicles "$R1"
req "van: desvincular van que nao e dele" 404 PUT "/api/vehicle/00000000-0000-0000-0000-000000000000/unlink" "$D1"
if [ -n "$CRLV" ]; then
  upload_crlv "$CRLV" "$D1"; check "van: D1 cadastra pelo CRLV" 201 "$CODE"
  VAN=$(echo "$BODY" | jq -r .id)
  assert "van: dados lidos do CRLV (placa, renavam, chassi, passageiros, licenciamento)" "echo \"\$BODY\" | jq -e '(.plate|length)==7 and (.renavam|length)==11 and (.chassis|length)==17 and .capacity>0 and .licensingYear>2000 and .status==\"ACTIVE\"' >/dev/null"
  upload_crlv "$CRLV" "$D1"; check "van: D1 de novo com a mesma van" 409 "$CODE"
  upload_crlv "$CRLV" "$D2"; check "van: D2 com a mesma van (vincula, nao duplica)" 201 "$CODE"
  assert "van: mesma van para os dois" "[ \"\$(echo \"\$BODY\" | jq -r .id)\" = \"$VAN\" ]"
  assert "van: nao duplicou no banco" "[ \"\$(psql_audit -c \"SELECT count(*) FROM vehicles WHERE id='$VAN'\")\" = 1 ]"
  req "van: D1 lista suas vans" 200 GET /api/vehicle/my-vehicles "$D1"
  assert "van: my-vehicles traz a van" "echo \"\$BODY\" | jq -e '.[] | select(.id==\"$VAN\")' >/dev/null"
  req "van: D1 desvincula" 204 PUT "/api/vehicle/$VAN/unlink" "$D1"
  req "van: D1 sem van ativa" 200 GET /api/vehicle/my-vehicles "$D1"
  assert "van: lista vazia depois do unlink" "! echo \"\$BODY\" | jq -e '.[] | select(.id==\"$VAN\")' >/dev/null"
  upload_crlv "$CRLV" "$D1"; check "van: D1 volta a usar a mesma van (historico)" 201 "$CODE"
  assert "van: 2 vinculos do D1 com a van, so 1 ativo" "[ \"\$(psql_audit -c \"SELECT count(*) || '/' || count(*) FILTER (WHERE end_date IS NULL) FROM driver_vehicles dv JOIN drivers d ON d.user_id=dv.driver_id JOIN users u ON u.id=d.user_id WHERE u.cpf='$D1_CPF' AND dv.vehicle_id='$VAN'\")\" = '2/1' ]"
else
  echo "   (sem CRLV na raiz: cadastro real nao testado)"
fi

echo "== 5. Alunos e vinculos responsavel-aluno"
STUDENT_JSON="{\"name\":\"Aluno Ficticio\",\"notes\":\"teste\",\"birthDate\":\"2016-05-10\",\"relationType\":\"FINANCIAL\",\"address\":$addr}"
req "R1 cria aluno" 200 POST /api/student/create-student "$R1" "$STUDENT_JSON"
STUDENT=$(echo "$BODY" | jq -r .studentResponseDTO.id); STUDENT_ADDR=$(echo "$BODY" | jq -r .studentResponseDTO.studentAddressResponseDTO.id)
assert "vinculo admin persistido" "[ \"\$(psql_audit -c \"SELECT is_admin FROM student_responsibles WHERE student_id='$STUDENT'\")\" = t ]"
assert "coordenadas do aluno persistidas" "[ \"\$(psql_audit -c \"SELECT a.latitude IS NOT NULL FROM student_addresses sa JOIN addresses a ON a.id=sa.address_id WHERE sa.student_id='$STUDENT'\")\" = t ]"
req "R1 cria aluno sem nome" 400 POST /api/student/create-student "$R1" "{\"birthDate\":\"2016-05-10\",\"relationType\":\"FINANCIAL\",\"address\":$addr}"
req "R1 cria aluno data futura" 400 POST /api/student/create-student "$R1" "{\"name\":\"X\",\"birthDate\":\"2999-01-01\",\"relationType\":\"FINANCIAL\",\"address\":$addr}"
req "R1 cria aluno sem endereco" 400 POST /api/student/create-student "$R1" "{\"name\":\"X\",\"birthDate\":\"2016-05-10\",\"relationType\":\"FINANCIAL\"}"

echo "   enderecos do aluno (onde e buscado/deixado; o motorista so escolhe entre eles)"
saddr() { echo "{\"label\":\"$1\",\"address\":{\"street\":\"$2\",\"zipCode\":\"01000-000\",\"city\":\"Sao Paulo\",\"number\":10,\"state\":\"SP\"}}"; }
req "enderecos do aluno" 200 GET "/api/student/$STUDENT/addresses" "$R1"
assert "o endereco do cadastro virou o Casa" "echo \"\$BODY\" | jq -e 'length==1 and .[0].label==\"Casa\" and .[0].id==\"$STUDENT_ADDR\" and .[0].address.latitude != null' >/dev/null"
HOME_ADDR=$(echo "$BODY" | jq -r '.[0].address.id')
req "aluno ganha endereco Padaria" 201 POST "/api/student/$STUDENT/addresses" "$R1" "$(saddr Padaria 'Rua Haddock Lobo')"
STUDENT_PADARIA=$(echo "$BODY" | jq -r .id); PADARIA_ADDR=$(echo "$BODY" | jq -r .address.id)
req "aluno ganha endereco Ingles" 201 POST "/api/student/$STUDENT/addresses" "$R1" "$(saddr Ingles 'Rua Bela Cintra')"; STUDENT_INGLES=$(echo "$BODY" | jq -r .id)
req "mesmo endereco 2x no aluno" 409 POST "/api/student/$STUDENT/addresses" "$R1" "{\"label\":\"De novo\",\"addressId\":\"$PADARIA_ADDR\"}"
req "endereco novo E existente juntos" 400 POST "/api/student/$STUDENT/addresses" "$R1" "{\"label\":\"X\",\"addressId\":\"$PADARIA_ADDR\",\"address\":$addr}"
req "endereco sem nada" 400 POST "/api/student/$STUDENT/addresses" "$R1" '{"label":"X"}'
req "reaproveitar endereco que nao e de filho dele" 403 POST "/api/student/$STUDENT/addresses" "$R1" '{"label":"X","addressId":"00000000-0000-0000-0000-000000000000"}'
req "motorista nao ve enderecos do aluno" 403 GET "/api/student/$STUDENT/addresses" "$D1"
assert "endereco igual nao duplica: pai e mae da mesma casa caem no mesmo endereco" "[ \"\$(psql_audit -c \"SELECT count(DISTINCT address_id) FROM responsibles r JOIN users u ON u.id=r.user_id WHERE u.cpf IN ('$R1_CPF','$R2_CPF')\")\" = 1 ]"
assert "a casa do aluno e o mesmo endereco de cobranca do pai (digitou igual)" "[ \"\$(psql_audit -c \"SELECT r.address_id FROM responsibles r JOIN users u ON u.id=r.user_id WHERE u.cpf='$R1_CPF'\")\" = \"$HOME_ADDR\" ]"
req "R1 lista filhos" 200 GET /api/student/my-children "$R1"
assert "my-children de R1 tem 1 aluno" "[ \"\$(echo \"\$BODY\" | jq .totalElements)\" = 1 ]"
req "R1 filtra filhos por nome" 200 GET "/api/student/my-children?studentName=aluno&isAdmin=true" "$R1"
assert "filtro por nome encontra" "[ \"\$(echo \"\$BODY\" | jq .totalElements)\" = 1 ]"
req "R1 gera codigo de vinculo" 200 POST /api/student/generate-link "$R1" "{\"id\":\"$STUDENT\",\"relationType\":\"WITHDRAWAL\"}"; LINK=$BODY
req "R2 gera codigo sem ser admin" 403 POST /api/student/generate-link "$R2" "{\"id\":\"$STUDENT\",\"relationType\":\"WITHDRAWAL\"}"
req "R2 confirma codigo invalido" 400 POST /api/student/confirm-link "$R2" "{\"code\":\"INVALIDO1\"}"
req "R2 confirma codigo" 201 POST /api/student/confirm-link "$R2" "{\"code\":\"$LINK\"}"
req "R2 reutiliza codigo" 400 POST /api/student/confirm-link "$R2" "{\"code\":\"$LINK\"}"
req "R2 (outro responsavel) ve os enderecos do aluno" 200 GET "/api/student/$STUDENT/addresses" "$R2"
assert "R2 ve Casa, Padaria e Ingles" "[ \"\$(echo \"\$BODY\" | jq -r '[.[].label] | join(\",\")')\" = \"Casa,Padaria,Ingles\" ]"
req "R2 lista filhos (nao admin)" 200 GET "/api/student/my-children?isAdmin=false" "$R2"
assert "R2 ve aluno vinculado" "[ \"\$(echo \"\$BODY\" | jq .totalElements)\" = 1 ]"
req "motorista tenta criar aluno" 403 POST /api/student/create-student "$D1" "$STUDENT_JSON"
req "R1 cria aluno com 2 enderecos de uma vez" 200 POST /api/student/create-student "$R1" "{\"name\":\"Aluno Dois Enderecos\",\"birthDate\":\"2015-01-01\",\"relationType\":\"FINANCIAL\",\"addresses\":[{\"addressId\":\"$HOME_ADDR\"},$(saddr Natacao 'Rua Augusta')]}"
MULTI=$(echo "$BODY" | jq -r .studentResponseDTO.id)
req "enderecos do aluno de 2 enderecos" 200 GET "/api/student/$MULTI/addresses" "$R1"
assert "Casa (reaproveitada) + Natacao" "[ \"\$(echo \"\$BODY\" | jq -r '[.[].label] | join(\",\")')\" = \"Casa,Natacao\" ] && [ \"\$(echo \"\$BODY\" | jq -r '.[0].address.id')\" = \"$HOME_ADDR\" ]"
req "mesmo endereco 2x no cadastro" 400 POST /api/student/create-student "$R1" "{\"name\":\"X\",\"birthDate\":\"2015-01-01\",\"relationType\":\"FINANCIAL\",\"addresses\":[{\"addressId\":\"$HOME_ADDR\"},{\"address\":$addr}]}"

echo "== 6. Codigo de convite do motorista -> chat"
req "D1 ve seus dados (me)" 200 GET /api/users/me "$D1"
assert "me do motorista traz o linkCode" "[ \"\$(echo \"\$BODY\" | jq -r .linkCode)\" = \"$D1_LINK\" ] && echo \"\$BODY\" | jq -e '.roles | index(\"ROLE_DRIVER\") != null' >/dev/null"
D1_LINK=$(echo "$BODY" | jq -r .linkCode); D1_ID=$(echo "$BODY" | jq -r .id)
req "R1 ve seus dados (me)" 200 GET /api/users/me "$R1"
assert "me do responsavel sem linkCode e sem senha" "echo \"\$BODY\" | jq -e '.linkCode==null and (.id|length)==36' >/dev/null && ! grep -qi 'password' <<<\"\$BODY\""
R1_ID=$(echo "$BODY" | jq -r .id)
assert "sub do token e o id do usuario" "[ \"\$(echo \"\$R1\" | cut -d. -f2 | tr '_-' '/+' | base64 -d 2>/dev/null | jq -r .sub 2>/dev/null)\" = \"$R1_ID\" ]"
req "me sem token" 401 GET /api/users/me
req "R1 resgata convite D1" 201 POST /api/invite-code/redeem "$R1" "{\"inviteCode\":\"$D1_LINK\"}"; CHAT=$(echo "$BODY" | jq -r .)
req "R1 resgata de novo (mesmo chat)" 201 POST /api/invite-code/redeem "$R1" "{\"inviteCode\":\"$D1_LINK\"}"
assert "chat reaproveitado" "[ \"\$(echo \"\$BODY\" | jq -r .)\" = \"$CHAT\" ]"
req "convite formato invalido" 400 POST /api/invite-code/redeem "$R1" "{\"inviteCode\":\"abc\"}"
req "convite inexistente" 404 POST /api/invite-code/redeem "$R1" "{\"inviteCode\":\"ZZZZZZZZZ\"}"

echo "== 7. Chat"
req "R1 envia mensagem" 201 POST "/api/chats/$CHAT/messages" "$R1" '{"content":"Ola motorista","messageType":"REGULAR_CHAT"}'
req "tentativa de forjar PROPOSAL pelo endpoint de mensagens" 201 POST "/api/chats/$CHAT/messages" "$R1" '{"content":"fake","messageType":"PROPOSAL","payload":"{\"contractId\":\"x\"}"}'
assert "forjada vira REGULAR_CHAT sem payload" "echo \"\$BODY\" | jq -e '.messageType==\"REGULAR_CHAT\" and .payload==null' >/dev/null"
req "mensagem vazia" 400 POST "/api/chats/$CHAT/messages" "$R1" '{"content":"  "}'
req "D1 lista chats" 200 GET /api/chats "$D1"
assert "D1 tem 1 chat" "[ \"\$(echo \"\$BODY\" | jq .totalElements)\" = 1 ]"
req "D1 le mensagens" 200 GET "/api/chats/$CHAT/messages" "$D1"
assert "senderId da mensagem do R1 = id do /me (front sabe quais sao suas)" "echo \"\$BODY\" | jq -e '.content[] | select(.content==\"Ola motorista\" and .senderId==\"$R1_ID\")' >/dev/null"
assert "mensagem persistida" "echo \"\$BODY\" | jq -e '.content[] | select(.content==\"Ola motorista\")' >/dev/null"
req "D2 le chat alheio" "403|400" GET "/api/chats/$CHAT/messages" "$D2"
req "D2 envia em chat alheio" "403|400" POST "/api/chats/$CHAT/messages" "$D2" '{"content":"intruso"}'
WS=$(curl -s -o /dev/null -w '%{http_code}' --max-time 3 --http1.1 -H 'Connection: Upgrade' -H 'Upgrade: websocket' -H 'Sec-WebSocket-Version: 13' -H 'Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==' "$BASE/chat/$CHAT?token=$R1")
check "handshake WebSocket autenticado" 101 "$WS"
WS=$(curl -s -o /dev/null -w '%{http_code}' --max-time 3 --http1.1 -H 'Connection: Upgrade' -H 'Upgrade: websocket' -H 'Sec-WebSocket-Version: 13' -H 'Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==' "$BASE/chat/$CHAT")
check "handshake WebSocket sem token" 401 "$WS"
while IFS='|' read -r st name detail; do
  if [ "$st" = PASS ]; then PASS=$((PASS+1)); RESULTS+=("PASS | $name"); else FAIL=$((FAIL+1)); RESULTS+=("FAIL | $name | ${detail:0:160}"); fi
done < <(BASE=$BASE CHAT=$CHAT TOKEN_SENDER=$R1 TOKEN_RECEIVER=$D1 TOKEN_OUTSIDER=$D2 node "$(dirname "$0")/ws-test.mjs" 2>&1 || echo "FAIL|ws-test.mjs nao executou|")

echo "== 8. Contratos (proposta no chat, aceite e recusa)"
req "D1 lista chats (outro usuario = R1)" 200 GET /api/chats "$D1"
assert "otherUserId do chat e o id do R1" "echo \"\$BODY\" | jq -e '.content[] | select(.id==\"$CHAT\" and .otherUserId==\"$R1_ID\")' >/dev/null"
req "D1 ve os filhos do pai do chat" 200 GET "/api/chats/$CHAT/students" "$D1"
assert "filhos do pai: so id e nome" "echo \"\$BODY\" | jq -e '.[] | select(.id==\"$STUDENT\" and .name==\"Aluno Ficticio\" and (keys == [\"id\",\"name\"]))' >/dev/null"
req "R1 (responsavel) tenta ver filhos pelo chat" 403 GET "/api/chats/$CHAT/students" "$R1"
req "D2 (fora do chat) tenta ver filhos" 400 GET "/api/chats/$CHAT/students" "$D2"
contract() { echo "{\"responsibleId\":\"$R1_ID\",\"studentId\":\"$1\",\"schoolId\":\"$SCHOOL\",\"contract\":{\"periodicity\":\"MONTHLY\",\"value\":350.00,\"startDate\":\"2027-02-01\",\"endDate\":\"2027-06-30\"},\"document\":{\"url\":\"contratos/teste.pdf\",\"type\":\"TRANSPORT_CONTRACT\"}}"; }
req "D1 propoe contrato" 201 POST "/api/chats/$CHAT/contract" "$D1" "$(contract "$STUDENT")"
assert "criacao ja devolve o contrato completo" "echo \"\$BODY\" | jq -e '.contract.schoolId==\"$SCHOOL\" and .contract.status==\"PENDING\" and .contract.value==350 and (.contract.studentName|length>0)' >/dev/null"
UDC=$(echo "$BODY" | jq -r .userDriverContractId); CONTRACT=$(echo "$BODY" | jq -r .contract.contractId)
req "D1 propoe contrato duplicado" 409 POST "/api/chats/$CHAT/contract" "$D1" "$(contract "$STUDENT")"
req "D2 propoe em chat alheio" 400 POST "/api/chats/$CHAT/contract" "$D2" "$(contract "$STUDENT")"
req "D1 tenta aceitar o proprio contrato" 403 PUT "/api/chats/$CHAT/contracts/$CONTRACT/accept" "$D1"
req "R1 aceita contrato" 200 PUT "/api/chats/$CHAT/contracts/$CONTRACT/accept" "$R1"
req "R1 ve detalhes do contrato" 200 GET "/api/contract/$CONTRACT" "$R1"
assert "detalhes: escola, aluno, valor, status, motorista" "echo \"\$BODY\" | jq -e '.schoolId==\"$SCHOOL\" and .studentId==\"$STUDENT\" and .value==350 and .status==\"ACTIVE\" and (.schoolName|length>0) and (.driverName|length>0) and .schoolAddress.state==\"SP\"' >/dev/null"
req "D1 ve detalhes do contrato" 200 GET "/api/contract/$CONTRACT" "$D1"
req "D2 (nao e parte) ve detalhes" 403 GET "/api/contract/$CONTRACT" "$D2"
req "contrato inexistente" 404 GET "/api/contract/00000000-0000-0000-0000-000000000000" "$R1"
assert "contrato ACTIVE" "[ \"\$(psql_audit -c \"SELECT status FROM contracts WHERE id='$CONTRACT'\")\" = ACTIVE ]"
assert "5 parcelas mensais geradas (fev-jun)" "[ \"\$(psql_audit -c \"SELECT count(*) FROM payments WHERE contract_id='$CONTRACT'\")\" = 5 ]"
req "R1 aceita de novo" 409 PUT "/api/chats/$CHAT/contracts/$CONTRACT/accept" "$R1"
req "R1 cria segundo aluno" 200 POST /api/student/create-student "$R1" "$(echo "$STUDENT_JSON" | jq -c '.address.number=77')"; STUDENT2=$(echo "$BODY" | jq -r .studentResponseDTO.id)
STUDENT2_HOME=$(echo "$BODY" | jq -r .studentResponseDTO.studentAddressResponseDTO.id)
req "D1 propoe contrato aluno 2" 201 POST "/api/chats/$CHAT/contract" "$D1" "$(contract "$STUDENT2")"
CONTRACT2=$(echo "$BODY" | jq -r .contract.contractId)
req "R1 recusa contrato 2" 200 PUT "/api/chats/$CHAT/contracts/$CONTRACT2/reject" "$R1"
assert "contrato 2 CANCELED" "[ \"\$(psql_audit -c \"SELECT status FROM contracts WHERE id='$CONTRACT2'\")\" = CANCELED ]"
req "D1 meus contratos ativos" 200 GET "/api/contract/my-contracts?status=ACTIVE" "$D1"
assert "so o ativo, com aluno e escola" "echo \"\$BODY\" | jq -e 'length==1 and .[0].contractId==\"$CONTRACT\" and .[0].studentId==\"$STUDENT\" and .[0].schoolId==\"$SCHOOL\" and .[0].status==\"ACTIVE\"' >/dev/null"
req "R1 todos os seus contratos" 200 GET /api/contract/my-contracts "$R1"
assert "R1 ve o ativo e o cancelado" "echo \"\$BODY\" | jq -e '[.[].status] | (index(\"ACTIVE\") != null) and (index(\"CANCELED\") != null)' >/dev/null"
req "D2 nao ve contratos dos outros" 200 GET /api/contract/my-contracts "$D2"
assert "lista do D2 vazia" "[ \"\$(echo \"\$BODY\" | jq length)\" = 0 ]"
req "status invalido" 400 GET "/api/contract/my-contracts?status=XYZ" "$D1"
req "mensagens de proposta/aceite no chat" 200 GET "/api/chats/$CHAT/messages?size=50" "$R1"
assert "mensagens de contrato com messageType do sistema" "echo \"\$BODY\" | jq -e '[.content[].messageType] | (index(\"PROPOSAL\") != null) and (index(\"APPROVAL\") != null) and (index(\"REJECTION\") != null)' >/dev/null"
assert "chat contem 4 mensagens de contrato + 3 textos (1 via ws-test, 1 forjada)" "[ \"\$(echo \"\$BODY\" | jq .totalElements)\" = 7 ]"

echo "== 9. Rotas: aluno na rota (o ponto nasce do endereco do aluno; a escola do contrato entra junto) -> troca de ordem"
# aluno na rota (POST /api/route/{routeId}/add-student/{studentId}): acao, local e o endereco do aluno (fora da escola)
add() { echo "{\"action\":\"$1\",\"locationType\":\"$2\"${3:+,\"studentAddressId\":\"$3\"}${4:+,\"weekdays\":$4}}"; }
# toda alteracao da rota devolve {stops, pending}. id do student stop na resposta: ssid ALUNO ACAO PONTO
ssid() { echo "$BODY" | jq -r --arg s "$1" --arg a "$2" --arg p "$3" '[.stops[] | select(.id==$p) | .students[] | select(.studentId==$s and .action==$a)][0].id'; }
# id do ponto da resposta: pelo endereco (stop_by_addr ADDRESS_ID) ou o da escola (school_stop)
stop_by_addr() { echo "$BODY" | jq -r --arg a "$1" '.stops[] | select(.address.id==$a) | .id'; }
school_stop() { echo "$BODY" | jq -r '.stops[] | select((.schools|length)>0) | .id'; }
order() { req "D1 lista pontos" 200 GET "/api/route/$ROUTE/stops" "$D1"; echo "$BODY" | jq -r '[.[] | "\(.id):\(.orderIndex)"] | join(",")'; }
req "D1 cria rota de ida" 201 POST /api/route/create-route "$D1" '{"name":"Ida Manha"}'; ROUTE=$(echo "$BODY" | jq -r .id)
req "rota sem nome" 400 POST /api/route/create-route "$D1" '{"name":""}'
req "D1 cria rota sem escola" 201 POST /api/route/create-route "$D1" '{"name":"Sem escola"}'; ROUTE_NS=$(echo "$BODY" | jq -r .id)
req "opcoes de ponto do motorista" 200 GET /api/route/stop-options "$D1"
assert "opcoes: os enderecos do aluno com contrato ativo" "echo \"\$BODY\" | jq -e '([.students[] | select(.studentId==\"$STUDENT\") | .addresses[].label] | join(\",\")) == \"Casa,Padaria,Ingles\" and ([.students[].studentId] | index(\"$STUDENT2\") == null) and (has(\"schools\") | not)' >/dev/null"
req "responsavel nao tem opcoes de ponto" 403 GET /api/route/stop-options "$R1"
req "rota de /stops nao existe mais" 405 POST "/api/route/$ROUTE/stops" "$D1" "[{\"studentAddressId\":\"$STUDENT_ADDR\"}]"
req "student stop: contrato recusado" 403 POST "/api/route/$ROUTE/add-student/$STUDENT2" "$D1" "$(add BOARDING HOME "$STUDENT2_HOME")"
req "student stop: sem acao" 400 POST "/api/route/$ROUTE/add-student/$STUDENT" "$D1" "{\"locationType\":\"HOME\",\"studentAddressId\":\"$STUDENT_ADDR\"}"
req "student stop: rota inexistente" 404 POST "/api/route/00000000-0000-0000-0000-000000000000/add-student/$STUDENT" "$D1" "$(add BOARDING HOME "$STUDENT_ADDR")"
req "student stop: D2 na rota de D1" 403 POST "/api/route/$ROUTE/add-student/$STUDENT" "$D2" "$(add BOARDING HOME "$STUDENT_ADDR")"
req "student stop: embarque na esquina (cria so o ponto da casa)" 200 POST "/api/route/$ROUTE/add-student/$STUDENT" "$D1" "$(add BOARDING HOME "$STUDENT_ADDR")"
HOME_STOP=$(stop_by_addr "$HOME_ADDR"); SS_BOARD=$(ssid "$STUDENT" BOARDING "$HOME_STOP")
assert "esquina(1) com coordenadas e o mesmo endereco do aluno, sem escola" "echo \"\$BODY\" | jq -e --arg h \"$HOME_STOP\" '[.stops[] | \"\\(.id):\\(.orderIndex)\"] == [\"\\(\$h):1\"] and .stops[0].address.latitude != null and (.stops[0].schools|length)==0' >/dev/null"
assert "a resposta ja traz a pendencia (falta o desembarque todo dia)" "echo \"\$BODY\" | jq -e '.pending[0].studentId==\"$STUDENT\" and (.pending[0].missingAlighting|length)==7' >/dev/null"
req "student stop: mesma acao 2x na rota (sem dias = todos)" 409 POST "/api/route/$ROUTE/add-student/$STUDENT" "$D1" "$(add BOARDING SCHOOL)"
req "student stop: desembarque na escola (cria a escola no fim)" 200 POST "/api/route/$ROUTE/add-student/$STUDENT" "$D1" "$(add ALIGHTING SCHOOL)"
SCHOOL_STOP=$(school_stop); SS_ALIGHT=$(ssid "$STUDENT" ALIGHTING "$SCHOOL_STOP"); SCHOOL_STOP_LINK=$(echo "$BODY" | jq -r '.stops[] | .schools[0].id // empty')
assert "esquina(1) escola(2)" "echo \"\$BODY\" | jq -e --arg h \"$HOME_STOP\" --arg s \"$SCHOOL_STOP\" '[.stops[] | \"\\(.id):\\(.orderIndex)\"] == [\"\\(\$h):1\", \"\\(\$s):2\"]' >/dev/null"
assert "school_stops criado junto com o ponto" "[ \"\$(psql_audit -c \"SELECT count(*) FROM school_stops WHERE stop_id='$SCHOOL_STOP' AND school_id='$SCHOOL'\")\" = 1 ] && [ -n \"$SCHOOL_STOP_LINK\" ]"
assert "aluno completo: pendencias vazias na resposta" "[ \"\$(echo \"\$BODY\" | jq '.pending | length')\" = 0 ]"
assert "aluno 2x na rota (embarque + desembarque)" "[ \"\$(psql_audit -c \"SELECT count(*) FROM student_stops ss JOIN route_stops rs ON rs.id=ss.stop_id WHERE rs.route_id='$ROUTE' AND ss.student_id='$STUDENT'\")\" = 2 ]"
req "R1 cria irmao reaproveitando a casa do aluno" 200 POST /api/student/create-student "$R1" "{\"name\":\"Irmao Ficticio\",\"birthDate\":\"2014-03-02\",\"relationType\":\"FINANCIAL\",\"addressId\":\"$HOME_ADDR\"}"
STUDENT3=$(echo "$BODY" | jq -r .studentResponseDTO.id)
req "irmao: enderecos" 200 GET "/api/student/$STUDENT3/addresses" "$R1"; STUDENT3_HOME=$(echo "$BODY" | jq -r '.[0].id')
assert "irmao usa o MESMO endereco (sem digitar de novo)" "[ \"\$(echo \"\$BODY\" | jq -r '.[0].address.id')\" = \"$HOME_ADDR\" ]"
req "irmao: digitar a casa de novo, com a rua escrita diferente (mesmo CEP + numero), cai no endereco que ja existe (409)" 409 POST "/api/student/$STUDENT3/addresses" "$R1" '{"label":"Casa 2","address":{"street":"AV. PAULISTA","zipCode":"01310100","city":"SP","number":1000,"state":"sp"}}'
req "irmao reaproveita a padaria" 201 POST "/api/student/$STUDENT3/addresses" "$R1" "{\"label\":\"Padaria\",\"addressId\":\"$PADARIA_ADDR\"}"; STUDENT3_PADARIA=$(echo "$BODY" | jq -r .id)
req "irmao ganha o proprio Ingles" 201 POST "/api/student/$STUDENT3/addresses" "$R1" "$(saddr Ingles 'Rua Frei Caneca')"; STUDENT3_INGLES=$(echo "$BODY" | jq -r .id)
req "D1 propoe contrato do irmao" 201 POST "/api/chats/$CHAT/contract" "$D1" "$(contract "$STUDENT3")"
CONTRACT3=$(echo "$BODY" | jq -r .contract.contractId)
req "R1 aceita contrato do irmao" 200 PUT "/api/chats/$CHAT/contracts/$CONTRACT3/accept" "$R1"
req "student stop: irmao no MESMO ponto" 200 POST "/api/route/$ROUTE/add-student/$STUDENT3" "$D1" "$(add BOARDING HOME "$STUDENT3_HOME")"; SS_SIB=$(ssid "$STUDENT3" BOARDING "$HOME_STOP")
assert "ponto da esquina tem 2 alunos" "[ \"\$(psql_audit -c \"SELECT count(*) FROM student_stops WHERE stop_id='$HOME_STOP'\")\" = 2 ]"
# desembarque num ponto novo entra no fim: a padaria vira o ponto 3
req "irmao: desembarque na padaria ter/qui (ponto novo, no fim)" 200 POST "/api/route/$ROUTE/add-student/$STUDENT3" "$D1" "$(add ALIGHTING OTHER "$STUDENT3_PADARIA" '["TUESDAY","THURSDAY"]')"
THIRD_STOP=$(stop_by_addr "$PADARIA_ADDR"); SS_SIB_TT=$(ssid "$STUDENT3" ALIGHTING "$THIRD_STOP")

assert "ordem inicial: esquina(1) escola(2) padaria(3)" "[ \"\$(order)\" = \"$HOME_STOP:1,$SCHOOL_STOP:2,$THIRD_STOP:3\" ]"
# padaria 3 <-> 1 jogaria o embarque (esquina) depois do desembarque (escola): a rota recusa
req "troca que poe o embarque depois do desembarque" 400 PUT "/api/route/stop/$THIRD_STOP/swap-order" "$D1" '{"orderIndex":1}'
assert "erro diz que o embarque precisa vir antes" "grep -q 'precisa vir antes do desembarque' <<<\"\$BODY\""
req "troca: escola 2 <-> padaria 3" 200 PUT "/api/route/stop/$THIRD_STOP/swap-order" "$D1" '{"orderIndex":2}'
assert "a troca devolve a rota inteira" "echo \"\$BODY\" | jq -e '(.stops|length)==3 and has(\"pending\")' >/dev/null"
assert "so os dois trocaram: esquina(1) padaria(2) escola(3)" "[ \"\$(order)\" = \"$HOME_STOP:1,$THIRD_STOP:2,$SCHOOL_STOP:3\" ]"
req "troca para posicao vazia" 400 PUT "/api/route/stop/$HOME_STOP/swap-order" "$D1" '{"orderIndex":99}'
req "troca sem valor" 400 PUT "/api/route/stop/$HOME_STOP/swap-order" "$D1" '{}'
req "troca por D2" 403 PUT "/api/route/stop/$HOME_STOP/swap-order" "$D2" '{"orderIndex":1}'
req "troca ponto inexistente" 404 PUT "/api/route/stop/00000000-0000-0000-0000-000000000000/swap-order" "$D1" '{"orderIndex":1}'
req "aluno num endereco que nao e dele" 400 POST "/api/route/$ROUTE/add-student/$STUDENT" "$D1" "$(add ALIGHTING OTHER "$STUDENT3_INGLES" '["SATURDAY"]')"
assert "erro diz que nao e endereco do aluno" "grep -q 'não é desse aluno' <<<\"\$BODY\""


echo "   dias da semana"
req "irmao: desembarque na escola seg/qua" 200 POST "/api/route/$ROUTE/add-student/$STUDENT3" "$D1" "$(add ALIGHTING SCHOOL '' '["MONDAY","WEDNESDAY"]')"; SS_SIB_MW=$(ssid "$STUDENT3" ALIGHTING "$SCHOOL_STOP")
assert "dias gravados (na escola tambem vale dia)" "echo \"\$BODY\" | jq -e --arg id \"$SS_SIB_MW\" '.stops[] | .students[] | select(.id==\$id) | (.weekdays | join(\",\")) == \"MONDAY,WEDNESDAY\"' >/dev/null"
req "irmao: desembarque na quarta de novo (sobrepoe)" 409 POST "/api/route/$ROUTE/add-student/$STUDENT3" "$D1" "$(add ALIGHTING OTHER "$STUDENT3_INGLES" '["WEDNESDAY"]')"
assert "409 e pela sobreposicao de dias" "echo \"\$BODY\" | grep -q 'algum desses dias'"
req "dia invalido" 400 POST "/api/route/$ROUTE/add-student/$STUDENT3" "$D1" "$(add BOARDING HOME "$STUDENT3_PADARIA" '["FUNDAY"]')"
req "pendencias da rota" 200 GET "/api/route/$ROUTE/pending" "$D1"
assert "irmao sem desembarque sex/sab/dom; aluno 1 completo" "echo \"\$BODY\" | jq -e 'length==1 and .[0].studentId==\"$STUDENT3\" and (.[0].missingAlighting|join(\",\"))==\"FRIDAY,SATURDAY,SUNDAY\" and (.[0].missingBoarding|length)==0' >/dev/null"
req "pendencias por D2" 403 GET "/api/route/$ROUTE/pending" "$D2"
req "GET pontos traz alunos e escolas com ids" 200 GET "/api/route/$ROUTE/stops" "$D1"
assert "ponto da escola lista a escola e os alunos" "echo \"\$BODY\" | jq -e '.[] | select(.id==\"$SCHOOL_STOP\") | (.schools | length == 1) and (.students | length == 2)' >/dev/null"
assert "student stop traz o nome do aluno" "echo \"\$BODY\" | jq -e '.[] | .students[] | select(.studentId==\"$STUDENT3\" and (.studentName|length>0))' >/dev/null"
assert "student stop listado com id e dias" "echo \"\$BODY\" | jq -e '.[] | .students[] | select(.id==\"$SS_SIB_TT\" and (.weekdays | join(\",\")) == \"TUESDAY,THURSDAY\")' >/dev/null"
req "editar dias: ter/qui -> ter/qui/sex" 200 PUT "/api/route/student-stop/$SS_SIB_TT/weekdays" "$D1" '{"weekdays":["TUESDAY","THURSDAY","FRIDAY"]}'
assert "dias editados" "echo \"\$BODY\" | jq -e --arg id \"$SS_SIB_TT\" '.stops[] | .students[] | select(.id==\$id) | (.weekdays | join(\",\")) == \"TUESDAY,THURSDAY,FRIDAY\"' >/dev/null"
assert "a edicao ja traz a pendencia (sab/dom)" "echo \"\$BODY\" | jq -e '(.pending[0].missingAlighting|join(\",\"))==\"SATURDAY,SUNDAY\"' >/dev/null"
req "editar dias batendo com o seg/qua" 409 PUT "/api/route/student-stop/$SS_SIB_TT/weekdays" "$D1" '{"weekdays":["MONDAY"]}'
req "editar dias por D2" 403 PUT "/api/route/student-stop/$SS_SIB_TT/weekdays" "$D2" '{"weekdays":["SATURDAY"]}'
req "pendencias depois da edicao" 200 GET "/api/route/$ROUTE/pending" "$D1"
assert "agora faltam so sab/dom" "echo \"\$BODY\" | jq -e '.[0].studentId==\"$STUDENT3\" and (.[0].missingAlighting|join(\",\"))==\"SATURDAY,SUNDAY\"' >/dev/null"
req "excluir desembarques do irmao (limpeza)" 200 DELETE "/api/route/student-stop/$SS_SIB_TT" "$D1"
req "excluir desembarque seg/qua do irmao" 200 DELETE "/api/route/student-stop/$SS_SIB_MW" "$D1"
assert "a exclusao ja traz a pendencia (irmao sem desembarque nenhum)" "echo \"\$BODY\" | jq -e '.pending[0].studentId==\"$STUDENT3\" and (.pending[0].missingAlighting|length)==7' >/dev/null"

echo "   mover e excluir"
req "D1 poe o aluno na rota sem escola (outro endereco)" 200 POST "/api/route/$ROUTE_NS/add-student/$STUDENT" "$D1" "$(add BOARDING HOME "$STUDENT_INGLES")"
NS_STOP=$(echo "$BODY" | jq -r '.stops[0].id'); NS_SS=$(ssid "$STUDENT" BOARDING "$NS_STOP")
assert "casa sem escola: so 1 ponto" "[ \"\$(echo \"\$BODY\" | jq '.stops | length')\" = 1 ]"
req "D1: aluno desce na escola na outra rota" 200 POST "/api/route/$ROUTE_NS/add-student/$STUDENT" "$D1" "$(add ALIGHTING SCHOOL)"
NS_SCHOOL_STOP=$(echo "$BODY" | jq -r '.stops[] | .schools[0].id // empty'); NS_SS_A=$(ssid "$STUDENT" ALIGHTING "$(school_stop)")
req "mover aluno SCHOOL para ponto sem a escola" 400 PUT "/api/route/student-stop/$SS_ALIGHT/move" "$D1" "{\"routeStopId\":\"$HOME_STOP\"}"
req "mover aluno: esquina -> padaria (mesma rota)" 200 PUT "/api/route/student-stop/$SS_BOARD/move" "$D1" "{\"routeStopId\":\"$THIRD_STOP\"}"
assert "student stop agora aponta pra padaria" "[ \"\$(psql_audit -c \"SELECT stop_id FROM student_stops WHERE id='$SS_BOARD'\")\" = \"$THIRD_STOP\" ]"
req "mover aluno para ponto de outra rota" 400 PUT "/api/route/student-stop/$SS_BOARD/move" "$D1" "{\"routeStopId\":\"$NS_STOP\"}"
req "mover aluno para onde ja esta" 409 PUT "/api/route/student-stop/$SS_BOARD/move" "$D1" "{\"routeStopId\":\"$THIRD_STOP\"}"
req "mover aluno sem ponto" 400 PUT "/api/route/student-stop/$SS_BOARD/move" "$D1" '{}'
req "mover aluno por D2" 403 PUT "/api/route/student-stop/$SS_BOARD/move" "$D2" "{\"routeStopId\":\"$HOME_STOP\"}"
req "excluir ponto com aluno (irmao na esquina)" 409 DELETE "/api/route/stop/$HOME_STOP" "$D1"
req "excluir escola usada por alunos da rota" 409 DELETE "/api/route/school-stop/$SCHOOL_STOP_LINK" "$D1"
req "excluir student stop por D2" 403 DELETE "/api/route/student-stop/$SS_SIB" "$D2"
req "excluir student stop inexistente" 404 DELETE "/api/route/student-stop/00000000-0000-0000-0000-000000000000" "$D1"
req "excluir student stop do irmao" 200 DELETE "/api/route/student-stop/$SS_SIB" "$D1"
req "excluir ponto vazio (esquina, ordem 1)" 200 DELETE "/api/route/stop/$HOME_STOP" "$D1"
assert "pontos de baixo subiram: padaria(1) escola(2)" "[ \"\$(order)\" = \"$THIRD_STOP:1,$SCHOOL_STOP:2\" ]"
req "excluir ponto por D2" 403 DELETE "/api/route/stop/$SCHOOL_STOP" "$D2"
req "escola com aluno descendo nela" 409 DELETE "/api/route/school-stop/$NS_SCHOOL_STOP" "$D1"
req "tira o desembarque na escola" 200 DELETE "/api/route/student-stop/$NS_SS_A" "$D1"
req "excluir escola sem aluno descendo/embarcando nela" 200 DELETE "/api/route/school-stop/$NS_SCHOOL_STOP" "$D1"
assert "a resposta mostra a pendencia (aluno sem desembarque)" "echo \"\$BODY\" | jq -e '.pending[0].studentId==\"$STUDENT\" and (.pending[0].missingAlighting|length)==7' >/dev/null"
req "tira o aluno da outra rota" 200 DELETE "/api/route/student-stop/$NS_SS" "$D1"
assert "school stop removido" "[ \"\$(psql_audit -c \"SELECT count(*) FROM school_stops WHERE id='$NS_SCHOOL_STOP'\")\" = 0 ]"

req "D1 consulta rota" 200 GET "/api/route/$ROUTE" "$D1"
req "D2 consulta rota de D1" 403 GET "/api/route/$ROUTE" "$D2"
req "D1 lista rotas+paradas" 200 GET /api/route/get-all-routes-and-stops "$D1"
assert "D1 ve suas 2 rotas" "[ \"\$(echo \"\$BODY\" | jq length)\" = 2 ]"
req "D2 lista rotas+paradas" 200 GET /api/route/get-all-routes-and-stops "$D2"
assert "D2 nao ve rota de D1 (isolamento)" "! echo \"\$BODY\" | jq -e '.[] | select(.id==\"$ROUTE\")' >/dev/null"
req "responsavel lista rotas" 403 GET /api/route/get-all-routes-and-stops "$R1"
req "R1 ve as rotas do filho" 200 GET "/api/student/$STUDENT/routes" "$R1"
assert "so id e nome da rota" "echo \"\$BODY\" | jq -e 'length==1 and .[0].name==\"Ida Manha\" and (.[0] | keys) == [\"id\",\"name\"]' >/dev/null"
req "R2 (outro responsavel vinculado) ve as rotas" 200 GET "/api/student/$STUDENT/routes" "$R2"
req "D1 (motorista) usa a rota de responsavel" 403 GET "/api/student/$STUDENT/routes" "$D1"
req "R1 ve rotas de aluno que nao e dele" 403 GET "/api/student/00000000-0000-0000-0000-000000000000/routes" "$R1"

echo "   adicionar aluno na rota num passo so (o ponto e criado/reaproveitado na ordem certa)"
stops_of() { echo "$BODY" | jq -r '[.stops[] | "\(.orderIndex):\(if (.schools|length)>0 then "escola" else "casa" end):\(.students|length)"] | join(",")'; }
req "D1 cria rota de volta" 201 POST /api/route/create-route "$D1" '{"name":"Volta Tarde"}'; ROUTE_V=$(echo "$BODY" | jq -r .id)
req "volta: aluno desce em casa (so a casa)" 200 POST "/api/route/$ROUTE_V/add-student/$STUDENT" "$D1" "$(add ALIGHTING HOME "$STUDENT_ADDR")"
assert "volta: casa(1), sem escola" "[ \"\$(stops_of)\" = \"1:casa:1\" ]"
req "volta: aluno embarca na escola (a escola entra ANTES da casa)" 200 POST "/api/route/$ROUTE_V/add-student/$STUDENT" "$D1" "$(add BOARDING SCHOOL)"
assert "volta: escola(1) e casa(2)" "[ \"\$(stops_of)\" = \"1:escola:1,2:casa:1\" ]"
req "volta: irmao desce na mesma casa (mesmo ponto)" 200 POST "/api/route/$ROUTE_V/add-student/$STUDENT3" "$D1" "$(add ALIGHTING HOME "$STUDENT3_HOME")"
req "volta: irmao embarca na escola" 200 POST "/api/route/$ROUTE_V/add-student/$STUDENT3" "$D1" "$(add BOARDING SCHOOL)"
assert "volta: irmaos no mesmo ponto da casa, sem ponto novo" "[ \"\$(stops_of)\" = \"1:escola:2,2:casa:2\" ]"
req "volta: rota pronta" 200 GET "/api/route/$ROUTE_V/pending" "$D1"
assert "volta: sem pendencias" "[ \"\$(echo \"\$BODY\" | jq length)\" = 0 ]"
req "add aluno: escola com studentAddressId" 400 POST "/api/route/$ROUTE_V/add-student/$STUDENT" "$D1" "$(add ALIGHTING SCHOOL "$STUDENT_ADDR")"
req "add aluno: casa sem studentAddressId" 400 POST "/api/route/$ROUTE_V/add-student/$STUDENT" "$D1" "$(add ALIGHTING HOME)"
req "add aluno: endereco de outro aluno" 400 POST "/api/route/$ROUTE_V/add-student/$STUDENT3" "$D1" "$(add ALIGHTING OTHER "$STUDENT_INGLES")"
req "add aluno: sem contrato ativo" 403 POST "/api/route/$ROUTE_V/add-student/$STUDENT2" "$D1" "$(add ALIGHTING HOME "$STUDENT2_HOME")"
req "add aluno: D2 na rota de D1" 403 POST "/api/route/$ROUTE_V/add-student/$STUDENT" "$D2" "$(add ALIGHTING HOME "$STUDENT_ADDR")"
req "D1 cria outra ida" 201 POST /api/route/create-route "$D1" '{"name":"Ida 2"}'; ROUTE_I2=$(echo "$BODY" | jq -r .id)
req "ida 2: comeca pelo desembarque na escola" 200 POST "/api/route/$ROUTE_I2/add-student/$STUDENT" "$D1" "$(add ALIGHTING SCHOOL)"
req "ida 2: embarque em casa entra ANTES da escola" 200 POST "/api/route/$ROUTE_I2/add-student/$STUDENT" "$D1" "$(add BOARDING HOME "$STUDENT_ADDR")"
assert "ida 2: casa(1) e escola(2) (a escola desceu uma posicao)" "[ \"\$(stops_of)\" = \"1:casa:1,2:escola:1\" ]"
req "ida 2: irmao embarca na padaria (entra antes da escola, nao no fim)" 200 POST "/api/route/$ROUTE_I2/add-student/$STUDENT3" "$D1" "$(add BOARDING OTHER "$STUDENT3_PADARIA")"
req "ida 2: irmao desce na escola" 200 POST "/api/route/$ROUTE_I2/add-student/$STUDENT3" "$D1" "$(add ALIGHTING SCHOOL)"
assert "ida 2: casa(1), padaria(2), escola(3)" "[ \"\$(stops_of)\" = \"1:casa:1,2:casa:1,3:escola:2\" ]"
req "D1 cria rota com posicao escolhida" 201 POST /api/route/create-route "$D1" '{"name":"Ida 3"}'; ROUTE_P=$(echo "$BODY" | jq -r .id)
req "ida 3: desce na escola" 200 POST "/api/route/$ROUTE_P/add-student/$STUDENT" "$D1" "$(add ALIGHTING SCHOOL)"
req "ida 3: embarque escolhendo a posicao 2 (depois da escola)" 400 POST "/api/route/$ROUTE_P/add-student/$STUDENT" "$D1" "{\"action\":\"BOARDING\",\"locationType\":\"HOME\",\"studentAddressId\":\"$STUDENT_ADDR\",\"orderIndex\":2}"
assert "a regra da ordem vale com a posicao escolhida" "grep -q 'precisa vir antes do desembarque' <<<\"\$BODY\""
req "ida 3: posicao 0" 400 POST "/api/route/$ROUTE_P/add-student/$STUDENT" "$D1" "{\"action\":\"BOARDING\",\"locationType\":\"HOME\",\"studentAddressId\":\"$STUDENT_ADDR\",\"orderIndex\":0}"
req "ida 3: embarque escolhendo a posicao 1" 200 POST "/api/route/$ROUTE_P/add-student/$STUDENT" "$D1" "{\"action\":\"BOARDING\",\"locationType\":\"HOME\",\"studentAddressId\":\"$STUDENT_ADDR\",\"orderIndex\":1}"
assert "ida 3: casa(1), escola(2)" "[ \"\$(stops_of)\" = \"1:casa:1,2:escola:1\" ]"

echo "== 10. Storage (MinIO): upload, anexo no chat e download autorizado"
IMG="$(cd "$(dirname "$0")/../.." && pwd)/images.jpeg"
PDF="$(cd "$(dirname "$0")/../.." && pwd)/teste.pdf"; [ -f "$PDF" ] || PDF=/tmp/vr_fake.pdf
[ -f "$IMG" ] || { python3 -I -c 'import sys;sys.stdout.buffer.write(bytes.fromhex("ffd8ffe000104a46494600010100000100010000ffd9"))' > /tmp/vr_img.jpeg; IMG=/tmp/vr_img.jpeg; }
req "presigned-url (imagem)" 200 GET "/api/storage/presigned-url?fileName=foto.jpeg&contentType=image/jpeg" "$R1"
UP=$(echo "$BODY" | jq -r .uploadUrl); KEY=$(echo "$BODY" | jq -r .fileKey)
UPCODE=$(curl -s -o /dev/null -w '%{http_code}' -X PUT -H 'Content-Type: image/jpeg' --data-binary @"$IMG" "$UP")
check "upload da imagem via URL pre-assinada" 200 "$UPCODE"
req "presigned-url sem token" 401 GET "/api/storage/presigned-url?fileName=a.pdf"
req "download-url antes de enviar no chat (ninguem tem acesso)" 403 GET "/api/storage/download-url?fileKey=$KEY" "$D1"
req "mensagem com imagem anexada" 201 POST "/api/chats/$CHAT/messages" "$R1" "{\"attachmentType\":\"IMAGE\",\"attachmentUrl\":\"$KEY\"}"
req "D1 le mensagem com anexo" 200 GET "/api/chats/$CHAT/messages?size=100" "$D1"
assert "anexo persistido com o fileKey" "echo \"\$BODY\" | jq -e '.content[] | select(.attachmentUrl==\"$KEY\" and .attachmentType==\"IMAGE\")' >/dev/null"
req "D1 (participante) pede download-url" 200 GET "/api/storage/download-url?fileKey=$KEY" "$D1"
DL=$(echo "$BODY" | jq -r .downloadUrl)
DLCODE=$(curl -s -o /tmp/vr_dl.jpeg -w '%{http_code} %{content_type}' "$DL")
check "download pela URL assinada" "200 image/jpeg" "$DLCODE"
assert "imagem baixada identica a enviada (sha256)" "[ \"\$(sha256sum < \"$IMG\")\" = \"\$(sha256sum < /tmp/vr_dl.jpeg)\" ]"
req "R1 (remetente) pede download-url" 200 GET "/api/storage/download-url?fileKey=$KEY" "$R1"
req "D2 (fora do chat) pede download-url" 403 GET "/api/storage/download-url?fileKey=$KEY" "$D2"
req "R2 (fora do chat) pede download-url" 403 GET "/api/storage/download-url?fileKey=$KEY" "$R2"
req "download-url de arquivo inexistente" 403 GET "/api/storage/download-url?fileKey=nao-existe.jpeg" "$D1"
req "download-url sem token" 401 GET "/api/storage/download-url?fileKey=$KEY"
GETCODE=$(curl -s -o /dev/null -w '%{http_code}' "http://localhost:9000/vanroute-audit/$KEY")
check "bucket privado: download anonimo direto negado" 403 "$GETCODE"
EXPCODE=$(curl -s -o /dev/null -w '%{http_code}' "${DL%%X-Amz-Signature=*}X-Amz-Signature=0000")
check "URL com assinatura adulterada negada" 403 "$EXPCODE"

echo "   PDF anexado no chat"
req "R1 presigned-url (PDF no chat)" 200 GET "/api/storage/presigned-url?fileName=teste.pdf&contentType=application/pdf" "$R1"
UP=$(echo "$BODY" | jq -r .uploadUrl); CHATPDF=$(echo "$BODY" | jq -r .fileKey)
check "upload do PDF do chat" 200 "$(curl -s -o /dev/null -w '%{http_code}' -X PUT -H 'Content-Type: application/pdf' --data-binary @"$PDF" "$UP")"
req "mensagem com PDF anexado" 201 POST "/api/chats/$CHAT/messages" "$R1" "{\"content\":\"segue o documento\",\"attachmentType\":\"OTHER\",\"attachmentUrl\":\"$CHATPDF\"}"
req "D1 pede download do PDF do chat" 200 GET "/api/storage/download-url?fileKey=$CHATPDF" "$D1"
check "PDF do chat baixa identico" 0 "$(curl -s "$(echo "$BODY" | jq -r .downloadUrl)" | cmp -s - "$PDF"; echo $?)"
echo "   documento de contrato"
req "D1 presigned-url (PDF)" 200 GET "/api/storage/presigned-url?fileName=contrato.pdf&contentType=application/pdf" "$D1"
UP=$(echo "$BODY" | jq -r .uploadUrl); PDFKEY=$(echo "$BODY" | jq -r .fileKey)
UPCODE=$(curl -s -o /dev/null -w '%{http_code}' -X PUT -H 'Content-Type: application/pdf' --data-binary @"$PDF" "$UP")
check "upload do PDF do contrato" 200 "$UPCODE"
DOC_CONTRACT="{\"responsibleId\":\"$R1_ID\",\"studentId\":\"$STUDENT2\",\"schoolId\":\"$SCHOOL\",\"contract\":{\"periodicity\":\"MONTHLY\",\"value\":300.00,\"startDate\":\"2027-02-01\",\"endDate\":\"2027-03-31\"},\"document\":{\"url\":\"$PDFKEY\",\"type\":\"TRANSPORT_CONTRACT\"}}"
req "D1 propoe contrato com o PDF" 201 POST "/api/chats/$CHAT/contract" "$D1" "$DOC_CONTRACT"
PDF_CONTRACT=$(echo "$BODY" | jq -r .contract.contractId)
req "R1 ve a proposta com o documento" 200 GET "/api/contract/$PDF_CONTRACT" "$R1"
assert "detalhe traz o fileKey do PDF e status PENDING" "echo \"\$BODY\" | jq -e '.documentFileKey==\"$PDFKEY\" and .documentType==\"TRANSPORT_CONTRACT\" and .status==\"PENDING\"' >/dev/null"
req "R1 (parte do contrato) baixa o PDF" 200 GET "/api/storage/download-url?fileKey=$PDFKEY" "$R1"
DLCODE=$(curl -s -o /tmp/vr_dl.pdf -w '%{http_code} %{content_type}' "$(echo "$BODY" | jq -r .downloadUrl)")
check "download do PDF pela URL assinada" "200 application/pdf" "$DLCODE"
assert "PDF baixado identico ao enviado (sha256)" "[ \"\$(sha256sum < \"$PDF\")\" = \"\$(sha256sum < /tmp/vr_dl.pdf)\" ]"
req "D1 (parte do contrato) baixa o PDF" 200 GET "/api/storage/download-url?fileKey=$PDFKEY" "$D1"
req "R2 (nao e parte do contrato) baixa o PDF" 403 GET "/api/storage/download-url?fileKey=$PDFKEY" "$R2"
req "D2 (nao e parte do contrato) baixa o PDF" 403 GET "/api/storage/download-url?fileKey=$PDFKEY" "$D2"

echo "== 10b. Execucao da rota (trip)"
DIRLOG="$(dirname "$0")/out/directions.log"; touch "$DIRLOG"
dircalls() { wc -l < "$DIRLOG" | tr -d ' '; }
TODAY=$(TZ=America/Sao_Paulo date +%F); TOMORROW=$(TZ=America/Sao_Paulo date -d tomorrow +%F)
if [ -z "${VAN:-}" ]; then
  # sem CRLV na raiz: van criada direto na base de teste so pra poder iniciar a trip
  VAN=$(psql_audit -c "INSERT INTO vehicles (id, plate, capacity, status) VALUES (gen_random_uuid(), 'TST$RANDOM', 15, 'ACTIVE') RETURNING id" | head -1)
  psql_audit -c "INSERT INTO driver_vehicles (id, driver_id, vehicle_id, start_date) SELECT gen_random_uuid(), u.id, '$VAN', CURRENT_DATE FROM users u WHERE u.cpf='$D1_CPF'" >/dev/null
fi
# irmao volta pra rota: embarca na padaria e desce na escola, todos os dias
req "trip: irmao embarca na padaria" 200 POST "/api/route/$ROUTE/add-student/$STUDENT3" "$D1" "$(add BOARDING HOME "$STUDENT3_PADARIA")"
req "trip: nao inicia com pendencia (irmao sem desembarque)" 400 POST "/api/trip/start/$ROUTE" "$D1" '{"latitude":-23.56,"longitude":-46.64}'
assert "erro diz quem e o que falta" "grep -q 'Irmao Ficticio sem desembarque' <<<\"\$BODY\""
req "trip: irmao desce na escola" 200 POST "/api/route/$ROUTE/add-student/$STUDENT3" "$D1" "$(add ALIGHTING SCHOOL)"
req "trip: rota sem pendencias" 200 GET "/api/route/$ROUTE/pending" "$D1"
assert "trip: pendencias vazias" "[ \"\$(echo \"\$BODY\" | jq length)\" = 0 ]"
# coordenadas reais dos pontos (embarque/desembarque so a ate 1 km do ponto): a van comeca a ~220 m do embarque
req "trip: coordenadas dos pontos" 200 GET "/api/route/$ROUTE/stops" "$D1"
BOARD_LAT=$(echo "$BODY" | jq -r --arg s "$THIRD_STOP" '.[] | select(.id==$s) | .address.latitude')
BOARD_LNG=$(echo "$BODY" | jq -r --arg s "$THIRD_STOP" '.[] | select(.id==$s) | .address.longitude')
SCHOOL_LAT=$(echo "$BODY" | jq -r --arg s "$SCHOOL_STOP" '.[] | select(.id==$s) | .address.latitude')
SCHOOL_LNG=$(echo "$BODY" | jq -r --arg s "$SCHOOL_STOP" '.[] | select(.id==$s) | .address.longitude')
POS_LAT=$(python3 -I -c "print(round($BOARD_LAT + 0.002, 6))"); POS_LNG=$BOARD_LNG
POS="{\"latitude\":$POS_LAT,\"longitude\":$POS_LNG}"
# manda o GPS da van pelo WebSocket (como o app do motorista faz)
move_van() { # move_van TRIP LAT LNG
  node -e 'const [u,la,ln]=process.argv.slice(1); const ws=new WebSocket(u);
    ws.onopen=()=>ws.send(JSON.stringify({type:"position",latitude:+la,longitude:+ln}));
    ws.onmessage=(e)=>{const m=JSON.parse(e.data); if(m.latitude===+la&&m.longitude===+ln){ws.close();process.exit(0)}};
    setTimeout(()=>process.exit(1),5000)' "${BASE/http/ws}/trip/$1?token=$D1" "$2" "$3"
}
req "trip: D2 inicia rota de D1" 403 POST "/api/trip/start/$ROUTE" "$D2" "$POS"
req "trip: sem van e sem van padrao" 400 POST "/api/trip/start/$ROUTE" "$D1" "$POS"
req "trip: van que nao e do motorista" 400 POST "/api/trip/start/$ROUTE" "$D1" '{"latitude":-23.56,"longitude":-46.64,"vehicleId":"00000000-0000-0000-0000-000000000000"}'
req "trip: sem posicao" 400 POST "/api/trip/start/$ROUTE" "$D1" '{}'
req "trip: van padrao da rota" 200 PUT "/api/route/$ROUTE/set-vehicle/$VAN" "$D1"

echo "   falta avisada antes da trip"
req "falta: R1 avisa falta de amanha" 201 POST "/api/student/$STUDENT3/absences" "$R1" "{\"routeId\":\"$ROUTE\",\"date\":\"$TOMORROW\"}"; ABS_TOMORROW=$(echo "$BODY" | jq -r .id)
req "falta: mesma falta de novo" 409 POST "/api/student/$STUDENT3/absences" "$R1" "{\"routeId\":\"$ROUTE\",\"date\":\"$TOMORROW\"}"
req "falta: data que ja passou" 400 POST "/api/student/$STUDENT3/absences" "$R1" "{\"routeId\":\"$ROUTE\",\"date\":\"2020-01-01\"}"
req "falta: rota em que o aluno nao esta" 400 POST "/api/student/$STUDENT3/absences" "$R1" "{\"routeId\":\"$ROUTE_NS\",\"date\":\"$TOMORROW\"}"
req "falta: motorista nao avisa falta" 403 POST "/api/student/$STUDENT3/absences" "$D1" "{\"routeId\":\"$ROUTE\",\"date\":\"$TOMORROW\"}"
req "falta: R1 lista faltas" 200 GET "/api/student/$STUDENT3/absences" "$R1"
assert "falta: lista traz a de amanha" "echo \"\$BODY\" | jq -e '.[] | select(.id==\"$ABS_TOMORROW\" and .date==\"$TOMORROW\")' >/dev/null"
req "falta: motorista recebeu aviso no chat" 200 GET "/api/chats/$CHAT/messages?size=200" "$D1"
assert "falta: mensagem ABSENCE no chat" "echo \"\$BODY\" | jq -e '.content[] | select(.messageType==\"ABSENCE\" and (.content|test(\"não vai\")))' >/dev/null"
req "falta: R1 desfaz a de amanha" 204 DELETE "/api/student/$STUDENT3/absences/$ABS_TOMORROW" "$R1"

echo "   iniciar"
CALLS0=$(dircalls)
req "trip: D1 inicia (van padrao)" 201 POST "/api/trip/start/$ROUTE" "$D1" "$POS"; TRIP=$(echo "$BODY" | jq -r .id)
assert "trip: STARTED, com a van e os 2 alunos pendentes" "echo \"\$BODY\" | jq -e '.status==\"STARTED\" and .vehicleId==\"$VAN\" and ([.stops[].students[] | select(.status==\"PENDING\")] | length)==4' >/dev/null"
assert "trip: so os pontos com aluno hoje (padaria e escola), em ordem" "[ \"\$(echo \"\$BODY\" | jq -r '[.stops[].routeStopId] | join(\",\")')\" = \"$THIRD_STOP,$SCHOOL_STOP\" ]"
if [ "$REAL_MAPS" = 1 ]; then
  # API real: a linha segue as ruas e comeca na rua mais proxima da van
  assert "trip: trajeto real calculado a partir da posicao do motorista" "echo \"\$BODY\" | jq -e '.path.distance > 0 and (.path.coordinates|length) > 2 and ((.path.coordinates[0][0] - $POS_LNG)|fabs) < 0.005 and ((.path.coordinates[0][1] - $POS_LAT)|fabs) < 0.005' >/dev/null"
  skip_real "trip: 1 chamada na API de rotas"
else
  assert "trip: trajeto calculado a partir da posicao do motorista" "echo \"\$BODY\" | jq -e '.path.distance > 0 and .path.coordinates[0] == [$POS_LNG,$POS_LAT] and (.path.coordinates|length)==3' >/dev/null"
  assert "trip: 1 chamada na API de rotas" "[ \$(( \$(dircalls) - $CALLS0 )) = 1 ]"
fi
req "trip: iniciar de novo no mesmo dia" 409 POST "/api/trip/start/$ROUTE" "$D1" "$POS"
req "trip: trip de hoje (continuar)" 200 GET "/api/trip/route/$ROUTE/today" "$D1"
assert "trip: e a mesma trip" "[ \"\$(echo \"\$BODY\" | jq -r .id)\" = \"$TRIP\" ]"
assert "trip: so 1 trip ativa hoje no banco" "[ \"\$(psql_audit -c \"SELECT count(*) FROM trips WHERE route_id='$ROUTE' AND date='$TODAY' AND status <> 'CANCELED'\")\" = 1 ]"

echo "   trip do filho (o pai pega o tripId e abre o WebSocket)"
req "today-trips: R1 acha a trip do filho" 200 GET "/api/student/$STUDENT/today-trips" "$R1"
assert "today-trips: tripId e status do filho" "echo \"\$BODY\" | jq -e '.[] | select(.tripId==\"$TRIP\" and .boardingStatus==\"PENDING\")' >/dev/null"
req "today-trips: D1 nao usa" 403 GET "/api/student/$STUDENT/today-trips" "$D1"

echo "   falta durante a trip"
CALLS2=$(dircalls)
req "falta: motorista tira o irmao no meio da trip (nao apareceu)" 200 PUT "/api/trip/$TRIP/student/$STUDENT3/absent" "$D1"
assert "falta: irmao ABSENT pelo motorista" "echo \"\$BODY\" | jq -e '[.stops[].students[] | select(.studentId==\"$STUDENT3\") | .status] | all(. == \"ABSENT\")' >/dev/null"
if [ "$REAL_MAPS" = 1 ]; then skip_real "falta: trajeto recalculado (1 chamada)"
else assert "falta: trajeto recalculado (1 chamada)" "[ \$(( \$(dircalls) - $CALLS2 )) = 1 ]"; fi
req "falta: pai avisa falta de quem ja esta ausente" 409 POST "/api/student/$STUDENT3/absences" "$R1" "{\"routeId\":\"$ROUTE\",\"date\":\"$TODAY\"}"

echo "   embarque e desembarque"
move_van "$TRIP" "$(python3 -I -c "print(round($BOARD_LAT + 0.05, 6))")" "$BOARD_LNG" || FAIL=$((FAIL+1))
req "embarque: van a 5 km do ponto nao embarca" 409 PUT "/api/trip/$TRIP/student/$STUDENT/board" "$D1"
assert "embarque: erro diz a distancia" "grep -q 'km do ponto' <<<\"\$BODY\""
move_van "$TRIP" "$BOARD_LAT" "$BOARD_LNG" || FAIL=$((FAIL+1))
req "embarque: aluno embarca" 200 PUT "/api/trip/$TRIP/student/$STUDENT/board" "$D1"
assert "embarque: BOARDED" "[ \"\$(echo \"\$BODY\" | jq -r .status)\" = BOARDED ]"
req "falta: pai avisa falta de quem ja embarcou" 409 POST "/api/student/$STUDENT/absences" "$R1" "{\"routeId\":\"$ROUTE\",\"date\":\"$TODAY\"}"
req "embarque: de novo" 409 PUT "/api/trip/$TRIP/student/$STUDENT/board" "$D1"
req "embarque: ausente nao embarca" 409 PUT "/api/trip/$TRIP/student/$STUDENT3/board" "$D1"
req "embarque: aluno que nao esta na trip" 404 PUT "/api/trip/$TRIP/student/00000000-0000-0000-0000-000000000000/board" "$D1"
req "embarque: D2 na trip de D1" 403 PUT "/api/trip/$TRIP/student/$STUDENT/alight" "$D2"
req "trip: depois do embarque" 200 GET "/api/trip/$TRIP" "$D1"
assert "trip: IN_PROGRESS, padaria concluida, escola pendente" "echo \"\$BODY\" | jq -e '.status==\"IN_PROGRESS\" and .stops[0].done==true and .stops[1].done==false' >/dev/null"
move_van "$TRIP" "$SCHOOL_LAT" "$SCHOOL_LNG" || FAIL=$((FAIL+1))
req "desembarque: aluno desce" 200 PUT "/api/trip/$TRIP/student/$STUDENT/alight" "$D1"
assert "desembarque: DELIVERED com horarios no banco" "[ \"\$(psql_audit -c \"SELECT status || '/' || (boarding_time IS NOT NULL) || '/' || (unboarding_time >= boarding_time) FROM boardings WHERE trip_id='$TRIP' AND student_id='$STUDENT'\")\" = 'DELIVERED/true/true' ]"

echo "   recomecar e finalizar"
req "recomecar: restart cancela e cria outra" 201 POST "/api/trip/start/$ROUTE?restart=true" "$D1" "$POS"; TRIP2=$(echo "$BODY" | jq -r .id)
assert "recomecar: trip nova do zero, antiga CANCELED" "[ \"$TRIP2\" != \"$TRIP\" ] && [ \"\$(psql_audit -c \"SELECT status FROM trips WHERE id='$TRIP'\")\" = CANCELED ] && echo \"\$BODY\" | jq -e '[.stops[].students[] | select(.studentId==\"$STUDENT\") | .status] | all(. == \"PENDING\")' >/dev/null"
req "recomecar: trip cancelada nao muda mais" 409 PUT "/api/trip/$TRIP/student/$STUDENT/board" "$D1"
echo "   websocket da trip (GPS do motorista, mapa e tempo pros pais)"
while IFS='|' read -r st name detail; do
  if [ "$st" = PASS ]; then PASS=$((PASS+1)); RESULTS+=("PASS | $name")
  elif [ "$st" = SKIP ]; then SKIP=$((SKIP+1)); RESULTS+=("SKIP | $name (so com o simulador)"); else FAIL=$((FAIL+1)); RESULTS+=("FAIL | $name | ${detail:0:200}"); fi
done < <(BASE=$BASE ROUTE=$ROUTE BOARD_LAT=$BOARD_LAT BOARD_LNG=$BOARD_LNG SCHOOL_LAT=$SCHOOL_LAT SCHOOL_LNG=$SCHOOL_LNG TRIP=$TRIP2 TOKEN_DRIVER=$D1 TOKEN_R1=$R1 TOKEN_R2=$R2 TOKEN_OUTSIDER=$D2 STUDENT=$STUDENT STUDENT3=$STUDENT3 \
  BOARD_STOP=$THIRD_STOP SCHOOL_STOP=$SCHOOL_STOP DIRLOG="$DIRLOG" REAL_MAPS=$REAL_MAPS node "$(dirname "$0")/trip-ws-test.mjs" 2>&1 || echo "FAIL|trip-ws-test.mjs nao executou|")
req "falta de hoje do irmao (avisada no meio da trip)" 200 GET "/api/student/$STUDENT3/absences" "$R1"
ABS_TODAY=$(echo "$BODY" | jq -r --arg d "$TODAY" '.[] | select(.date==$d) | .id')
req "falta: nao da pra desfazer depois que a viagem comecou" 409 DELETE "/api/student/$STUDENT3/absences/$ABS_TODAY" "$R1"
req "falta: rota do dia ja terminou" 409 POST "/api/student/$STUDENT/absences" "$R1" "{\"routeId\":\"$ROUTE\",\"date\":\"$TODAY\"}"
assert "finalizar: FINISHED com hora de fim no banco" "[ \"\$(psql_audit -c \"SELECT status || '/' || (end_time IS NOT NULL) FROM trips WHERE id='$TRIP2'\")\" = 'FINISHED/true' ]"
req "finalizar: embarque depois de finalizada" 409 PUT "/api/trip/$TRIP2/student/$STUDENT/board" "$D1"
req "finalizar: iniciar de novo no mesmo dia" 409 POST "/api/trip/start/$ROUTE" "$D1" "$POS"
req "finalizar: nem com restart" 409 POST "/api/trip/start/$ROUTE?restart=true" "$D1" "$POS"

echo "== 13a. Responsavel tira um endereco que esta na rota (apaga so a ligacao)"
req "R1 tira a Padaria do aluno (ele embarca nela)" 204 DELETE "/api/student/$STUDENT/addresses/$STUDENT_PADARIA" "$R1"
req "enderecos depois" 200 GET "/api/student/$STUDENT/addresses" "$R1"
assert "Padaria sumiu da lista" "! echo \"\$BODY\" | jq -e '.[] | select(.label==\"Padaria\")' >/dev/null"
req "rota depois" 200 GET "/api/route/$ROUTE/pending" "$D1"
assert "o aluno saiu do ponto: a rota ficou com pendencia de embarque" "echo \"\$BODY\" | jq -e '.[] | select(.studentId==\"$STUDENT\" and (.missingBoarding|length)==7)' >/dev/null"
assert "a Padaria continua pro irmao (so a ligacao do aluno saiu)" "[ \"\$(psql_audit -c \"SELECT count(*) FROM student_addresses WHERE student_id='$STUDENT3' AND address_id='$PADARIA_ADDR'\")\" = 1 ]"
req "motorista nao tira endereco do aluno" 403 DELETE "/api/student/$STUDENT/addresses/$STUDENT_INGLES" "$D1"
req "R1 tira o Ingles" 204 DELETE "/api/student/$STUDENT/addresses/$STUDENT_INGLES" "$R1"
req "o ultimo endereco nao pode sair" 409 DELETE "/api/student/$STUDENT/addresses/$STUDENT_ADDR" "$R1"

echo "== 13b. Notificacoes (contrato e viagem)"
types() { echo "$BODY" | jq -r '[.content[].type] | join(",")'; }
req "notificacoes do R1" 200 GET "/api/notifications?size=100" "$R1"
R1_TYPES=$(types)
assert "R1: proposta de contrato com contractId" "echo \"\$BODY\" | jq -e '.content[] | select(.type==\"CONTRACT_PROPOSED\" and .payload.contractId==\"$CONTRACT\")' >/dev/null"
assert "R1: a van saiu, com o tripId" "echo \"\$BODY\" | jq -e '.content[] | select(.type==\"TRIP_STARTED\" and .payload.tripId==\"$TRIP\")' >/dev/null"
assert "R1: a van esta indo buscar (proximo ponto e do filho)" "grep -q VAN_ON_THE_WAY <<<\"$R1_TYPES\""
assert "R1: embarcou / chegou na escola / irmao ausente" "echo \"\$BODY\" | jq -e '([.content[] | select(.type==\"STUDENT_BOARDED\")] | length) > 0 and ([.content[] | select(.type==\"STUDENT_DELIVERED\" and (.title|test(\"escola\")))] | length) > 0 and ([.content[] | select(.type==\"STUDENT_ABSENT\" and .payload.studentIds[0]==\"$STUDENT3\")] | length) > 0' >/dev/null"
assert "R1: nao recebe aviso de contrato aceito (isso e pro motorista)" "! grep -q CONTRACT_ACCEPTED <<<\"$R1_TYPES\""
req "notificacoes do D1" 200 GET "/api/notifications?size=100" "$D1"
assert "D1: contrato aceito e recusado + falta avisada pelo pai" "echo \"\$BODY\" | jq -e '([.content[].type] | index(\"CONTRACT_ACCEPTED\") != null) and ([.content[].type] | index(\"CONTRACT_REJECTED\") != null) and ([.content[].type] | index(\"ABSENCE_REPORTED\") != null)' >/dev/null"
assert "D1: endereco removido avisa o motorista" "echo \"\$BODY\" | jq -e '.content[] | select(.type==\"ROUTE\" and .payload.routeId==\"$ROUTE\" and .payload.studentId==\"$STUDENT\")' >/dev/null"
req "R1 nao lidas" 200 GET /api/notifications/unread-count "$R1"
assert "R1 tem nao lidas" "[ \$(echo \"\$BODY\" | jq .count) -gt 0 ]"
req "R1 lista so nao lidas" 200 GET "/api/notifications?unread=true" "$R1"
NOTIF=$(echo "$BODY" | jq -r '.content[0].id')
req "R2 tenta marcar notificacao do R1" 404 PUT "/api/notifications/$NOTIF/read" "$R2"
req "R1 marca como lida" 204 PUT "/api/notifications/$NOTIF/read" "$R1"
req "notificacao lida" 200 GET "/api/notifications?size=100" "$R1"
assert "read=true na lida" "echo \"\$BODY\" | jq -e '.content[] | select(.id==\"$NOTIF\" and .read==true)' >/dev/null"
req "R1 marca todas como lidas" 204 PUT /api/notifications/read-all "$R1"
req "R1 nao lidas depois" 200 GET /api/notifications/unread-count "$R1"
assert "zero nao lidas" "[ \$(echo \"\$BODY\" | jq .count) = 0 ]"
req "notificacoes sem token" 401 GET /api/notifications

echo "== 11. Usuarios"
echo "   foto de perfil"
req "foto: tipo nao imagem" 400 GET "/api/users/photo-upload-url?fileName=a.pdf&contentType=application/pdf" "$R1"
req "foto: URL de upload" 200 GET "/api/users/photo-upload-url?fileName=eu.jpeg&contentType=image/jpeg" "$R1"
PHOTO=$(echo "$BODY" | jq -r .fileKey); UP=$(echo "$BODY" | jq -r .uploadUrl)
assert "chave na pasta do usuario" "[[ \"$PHOTO\" == profile/$R1_ID/* ]]"
check "foto: upload no MinIO" 200 "$(curl -s -o /dev/null -w '%{http_code}' -X PUT -H 'Content-Type: image/jpeg' --data-binary @"$IMG" "$UP")"
req "foto: arquivo nao enviado" 400 PUT /api/users/update-photo "$R1" "{\"fileKey\":\"profile/$R1_ID/naoexiste.jpeg\"}"
req "foto: anexo privado do chat como foto" 400 PUT /api/users/update-photo "$R1" "{\"fileKey\":\"$KEY\"}"
req "foto: R2 usando a foto do R1" 400 PUT /api/users/update-photo "$R2" "{\"fileKey\":\"$PHOTO\"}"
req "foto: sem fileKey" 400 PUT /api/users/update-photo "$R1" '{}'
req "foto: R1 atualiza" 200 PUT /api/users/update-photo "$R1" "{\"fileKey\":\"$PHOTO\"}"
req "R1 ve a foto no me" 200 GET /api/users/me "$R1"
assert "me traz photoKey" "[ \"\$(echo \"\$BODY\" | jq -r .photoKey)\" = \"$PHOTO\" ]"
req "D1 ve a foto no chat" 200 GET /api/chats "$D1"
assert "chat traz otherUserPhotoKey" "echo \"\$BODY\" | jq -e '.content[] | select(.otherUserPhotoKey==\"$PHOTO\")' >/dev/null"
req "D2 (sem chat com R1) baixa a foto" 200 GET "/api/storage/download-url?fileKey=$PHOTO" "$D2"
check "foto baixada identica" 0 "$(curl -s "$(echo "$BODY" | jq -r .downloadUrl)" | cmp -s - "$IMG"; echo $?)"
req "D2 continua sem acesso ao anexo privado" 403 GET "/api/storage/download-url?fileKey=$KEY" "$D2"
req "foto: nova URL" 200 GET "/api/users/photo-upload-url?fileName=nova.jpeg&contentType=image/jpeg" "$R1"
PHOTO2=$(echo "$BODY" | jq -r .fileKey)
check "foto nova: upload" 200 "$(curl -s -o /dev/null -w '%{http_code}' -X PUT -H 'Content-Type: image/jpeg' --data-binary @"$IMG" "$(echo "$BODY" | jq -r .uploadUrl)")"
req "foto: troca pela nova" 200 PUT /api/users/update-photo "$R1" "{\"fileKey\":\"$PHOTO2\"}"
assert "foto antiga apagada do bucket" "! docker exec vanroute-minio sh -c 'ls /data/vanroute-audit/$PHOTO' >/dev/null 2>&1 && docker exec vanroute-minio sh -c 'ls /data/vanroute-audit/$PHOTO2' >/dev/null 2>&1"
req "GET /users/{id} removido (expunha CPF, e-mail e telefone)" "40[0-9]" GET "/api/users/$R1_ID" "$R1"
req "me" 200 GET /api/users/me "$R1"
assert "me nao expoe passwordHash" "! echo \"\$BODY\" | grep -qi 'passwordHash\|\\\$2a\\\$'"
req "envia codigo de atualizacao" 200 POST /api/users/send-code-updateUser "$R2"
req "R2 ve seus dados (me)" 200 GET /api/users/me "$R2"; R2_ID=$(echo "$BODY" | jq -r .id)
UCODE=$(redis GET "User:SendCodeUser:$R2_ID")
assert "codigo de atualizacao fica na chave do proprio usuario" "[ -n \"$UCODE\" ] && [ -z \"\$(redis GET User:SendCodeUser)\" ]"
req "R1 tenta usar o codigo do R2" 400 PUT /api/users/update-user "$R1" "{\"email\":\"x.$RUN@teste.local\",\"phone\":\"1\",\"code\":\"$UCODE\"}"
req "atualiza usuario codigo errado" 400 PUT /api/users/update-user "$R2" '{"email":"novo@teste.local","phone":"1","code":"errado"}'
req "atualiza usuario" 200 PUT /api/users/update-user "$R2" "{\"email\":\"r2b.$RUN@teste.local\",\"phone\":\"11${RUN}9\",\"code\":\"$UCODE\"}"
assert "e-mail atualizado" "[ \"\$(psql_audit -c \"SELECT email FROM users WHERE cpf='$R2_CPF'\")\" = r2b.$RUN@teste.local ]"
req "DELETE usuario sem ADMIN" 403 DELETE "/api/users/$R1_ID/123456" "$R2"

echo
printf '%s\n' "${RESULTS[@]}"
echo
echo "TOTAL: $((PASS+FAIL)) cenarios | PASS: $PASS | FAIL: $FAIL$([ "$SKIP" -gt 0 ] && echo " | SKIP: $SKIP (API real)")"
