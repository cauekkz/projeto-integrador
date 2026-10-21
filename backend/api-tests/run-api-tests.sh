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
#
# Cada execucao cria dados ficticios novos (CPFs validos aleatorios), entao pode ser repetida.
# Motoristas sao inseridos via SQL na base isolada porque o cadastro exige uma CNH digital
# assinada ICP-Brasil real, impossivel de gerar para dados ficticios.
set -u
BASE=${BASE:-http://localhost:9090}
DB=${DB:-vanroute_audit}
PASS=0; FAIL=0; RESULTS=()
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

assert() { # assert NOME CONDICAO_BASH
  if eval "$2"; then PASS=$((PASS+1)); RESULTS+=("PASS | $1"); else FAIL=$((FAIL+1)); RESULTS+=("FAIL | $1"); fi
}

RUN=$RANDOM$RANDOM
addr='{"street":"Rua Ficticia","zipCode":"01000-000","city":"Sao Paulo","neighborhood":"Centro","number":100,"state":"SP"}'

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
req "D1 cria escola" 200 POST /api/school/add-school "$D1" "{\"name\":\"Escola Teste $RUN\",\"phone\":\"1133334444\",\"email\":\"escola$RUN@teste.local\",\"addressRequestDTO\":{\"street\":\"Rua Escola $RUN\",\"zipCode\":\"01000-001\",\"city\":\"Sao Paulo\",\"number\":1,\"state\":\"SP\"}}"
SCHOOL=$(echo "$BODY" | jq -r .id)
assert "escola com coordenadas persistidas" "[ \"\$(psql_audit -c \"SELECT a.latitude IS NOT NULL FROM schools s JOIN addresses a ON a.id=s.address_id WHERE s.id='$SCHOOL'\")\" = t ]"
assert "vinculo driver_schools criado" "[ \"\$(psql_audit -c \"SELECT count(*) FROM driver_schools WHERE school_id='$SCHOOL'\")\" = 1 ]"
SCHOOL_JSON="{\"name\":\"Escola Teste $RUN\",\"phone\":\"1133334444\",\"email\":\"escola$RUN@teste.local\",\"addressRequestDTO\":{\"street\":\"Rua Escola $RUN\",\"zipCode\":\"01000-001\",\"city\":\"Sao Paulo\",\"number\":1,\"state\":\"SP\"}}"
req "D2 cadastra a mesma escola (vincula)" 200 POST /api/school/add-school "$D2" "$SCHOOL_JSON"
assert "D2 recebeu a escola existente" "[ \"\$(echo \"\$BODY\" | jq -r .id)\" = \"$SCHOOL\" ]"
assert "escola nao duplicada e 2 vinculos" "[ \"\$(psql_audit -c \"SELECT count(*) FROM driver_schools WHERE school_id='$SCHOOL'\")\" = 2 ]"
req "D2 lista suas escolas" 200 GET /api/school/my-schools "$D2"
assert "my-schools traz a escola com endereco" "echo \"\$BODY\" | jq -e '.[] | select(.id==\"$SCHOOL\" and .address.state==\"SP\" and (.name|length>0))' >/dev/null"
req "responsavel lista escolas de motorista" 403 GET /api/school/my-schools "$R1"
req "D2 cadastra a mesma escola de novo" 409 POST /api/school/add-school "$D2" "$SCHOOL_JSON"
req "responsavel tenta criar escola" 403 POST /api/school/add-school "$R1" "{\"name\":\"X\",\"email\":\"x@x\",\"addressRequestDTO\":$addr}"

echo "== 5. Alunos e vinculos responsavel-aluno"
STUDENT_JSON="{\"name\":\"Aluno Ficticio\",\"notes\":\"teste\",\"birthDate\":\"2016-05-10\",\"relationType\":\"FINANCIAL\",\"address\":$addr}"
req "R1 cria aluno" 200 POST /api/student/create-student "$R1" "$STUDENT_JSON"
STUDENT=$(echo "$BODY" | jq -r .studentResponseDTO.id); STUDENT_ADDR=$(echo "$BODY" | jq -r .studentResponseDTO.studentAddressResponseDTO.id)
assert "vinculo admin persistido" "[ \"\$(psql_audit -c \"SELECT is_admin FROM student_responsibles WHERE student_id='$STUDENT'\")\" = t ]"
assert "coordenadas do aluno persistidas" "[ \"\$(psql_audit -c \"SELECT a.latitude IS NOT NULL FROM student_addresses sa JOIN addresses a ON a.id=sa.address_id WHERE sa.student_id='$STUDENT'\")\" = t ]"
req "R1 cria aluno sem nome" 400 POST /api/student/create-student "$R1" "{\"birthDate\":\"2016-05-10\",\"relationType\":\"FINANCIAL\",\"address\":$addr}"
req "R1 cria aluno data futura" 400 POST /api/student/create-student "$R1" "{\"name\":\"X\",\"birthDate\":\"2999-01-01\",\"relationType\":\"FINANCIAL\",\"address\":$addr}"
req "R1 lista filhos" 200 GET /api/student/my-children "$R1"
assert "my-children de R1 tem 1 aluno" "[ \"\$(echo \"\$BODY\" | jq .totalElements)\" = 1 ]"
req "R1 filtra filhos por nome" 200 GET "/api/student/my-children?studentName=aluno&isAdmin=true" "$R1"
assert "filtro por nome encontra" "[ \"\$(echo \"\$BODY\" | jq .totalElements)\" = 1 ]"
req "R1 gera codigo de vinculo" 200 POST /api/student/generate-link "$R1" "{\"id\":\"$STUDENT\",\"relationType\":\"WITHDRAWAL\"}"; LINK=$BODY
req "R2 gera codigo sem ser admin" 403 POST /api/student/generate-link "$R2" "{\"id\":\"$STUDENT\",\"relationType\":\"WITHDRAWAL\"}"
req "R2 confirma codigo invalido" 400 POST /api/student/confirm-link "$R2" "{\"code\":\"INVALIDO1\"}"
req "R2 confirma codigo" 201 POST /api/student/confirm-link "$R2" "{\"code\":\"$LINK\"}"
req "R2 reutiliza codigo" 400 POST /api/student/confirm-link "$R2" "{\"code\":\"$LINK\"}"
req "R2 lista filhos (nao admin)" 200 GET "/api/student/my-children?isAdmin=false" "$R2"
assert "R2 ve aluno vinculado" "[ \"\$(echo \"\$BODY\" | jq .totalElements)\" = 1 ]"
req "motorista tenta criar aluno" 403 POST /api/student/create-student "$D1" "$STUDENT_JSON"

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
req "R1 cria segundo aluno" 200 POST /api/student/create-student "$R1" "$STUDENT_JSON"; STUDENT2=$(echo "$BODY" | jq -r .studentResponseDTO.id)
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

echo "== 9. Rotas: route stop (endereco, entra no fim) -> school/student stop -> troca de ordem"
stop_json() { echo "[{\"address\":{\"street\":\"$1\",\"zipCode\":\"01000-000\",\"city\":\"Sao Paulo\",\"number\":10,\"state\":\"SP\"}}]"; }
ss() { echo "{\"action\":\"$1\",\"locationType\":\"$2\"}"; }
order() { req "D1 lista pontos" 200 GET "/api/route/$ROUTE/stops" "$D1"; echo "$BODY" | jq -r '[.[] | "\(.id):\(.orderIndex)"] | join(",")'; }
req "D1 cria rota de ida" 201 POST /api/route/create-route "$D1" '{"name":"Ida Manha"}'; ROUTE=$(echo "$BODY" | jq -r .id)
req "rota sem nome" 400 POST /api/route/create-route "$D1" '{"name":""}'
req "D1 cria rota sem escola" 201 POST /api/route/create-route "$D1" '{"name":"Sem escola"}'; ROUTE_NS=$(echo "$BODY" | jq -r .id)
req "route stop: esquina" 200 POST "/api/route/$ROUTE/stops" "$D1" "$(stop_json 'Rua da Esquina')"; HOME_STOP=$(echo "$BODY" | jq -r '.[0].id')
assert "1o ponto entra na ordem 1, com coordenadas" "[ \"\$(echo \"\$BODY\" | jq '.[0].orderIndex')\" = 1 ] && [ \"\$(echo \"\$BODY\" | jq '.[0].address.latitude')\" != null ]"
req "route stop: escola" 200 POST "/api/route/$ROUTE/stops" "$D1" "$(stop_json 'Rua Escola')"; SCHOOL_STOP=$(echo "$BODY" | jq -r '.[0].id')
assert "2o ponto entra no fim (ordem 2)" "[ \"\$(echo \"\$BODY\" | jq '.[0].orderIndex')\" = 2 ]"
req "route stop: padaria" 200 POST "/api/route/$ROUTE/stops" "$D1" "$(stop_json 'Rua Padaria')"; THIRD_STOP=$(echo "$BODY" | jq -r '.[0].id')
req "route stop sem endereco" 400 POST "/api/route/$ROUTE/stops" "$D1" '[{}]'
req "route stop endereco nao geocodificavel" 400 POST "/api/route/$ROUTE/stops" "$D1" "$(stop_json NAOEXISTE)"
req "D2 cria route stop na rota de D1" 403 POST "/api/route/$ROUTE/stops" "$D2" "$(stop_json 'Rua X')"
req "route stop na rota sem escola" 200 POST "/api/route/$ROUTE_NS/stops" "$D1" "$(stop_json 'Rua Y')"; NS_STOP=$(echo "$BODY" | jq -r '.[0].id')

req "school stop: escola no ponto" 200 POST "/api/route/stop/$SCHOOL_STOP/add-school/$SCHOOL" "$D1"; SCHOOL_STOP_LINK=$(echo "$BODY" | jq -r .id)
assert "school_stops criado" "[ \"\$(psql_audit -c \"SELECT count(*) FROM school_stops WHERE stop_id='$SCHOOL_STOP' AND school_id='$SCHOOL'\")\" = 1 ]"
req "school stop: escola repetida na rota" 409 POST "/api/route/stop/$HOME_STOP/add-school/$SCHOOL" "$D1"
req "school stop: escola inexistente" 404 POST "/api/route/stop/$SCHOOL_STOP/add-school/00000000-0000-0000-0000-000000000000" "$D1"
req "school stop: ponto inexistente" 404 POST "/api/route/stop/00000000-0000-0000-0000-000000000000/add-school/$SCHOOL" "$D1"
req "school stop: D2 na rota de D1" 403 POST "/api/route/stop/$SCHOOL_STOP/add-school/$SCHOOL" "$D2"

req "student stop: rota sem a escola do contrato" 400 POST "/api/route/stop/$NS_STOP/add-student/$STUDENT" "$D1" "$(ss BOARDING HOME)"
req "student stop: contrato recusado" 403 POST "/api/route/stop/$HOME_STOP/add-student/$STUDENT2" "$D1" "$(ss BOARDING HOME)"
req "student stop: sem acao" 400 POST "/api/route/stop/$HOME_STOP/add-student/$STUDENT" "$D1" '{"locationType":"HOME"}'
req "student stop: ponto inexistente" 404 POST "/api/route/stop/00000000-0000-0000-0000-000000000000/add-student/$STUDENT" "$D1" "$(ss BOARDING HOME)"
req "student stop: D2 na rota de D1" 403 POST "/api/route/stop/$HOME_STOP/add-student/$STUDENT" "$D2" "$(ss BOARDING HOME)"
req "student stop: embarque na esquina" 200 POST "/api/route/stop/$HOME_STOP/add-student/$STUDENT" "$D1" "$(ss BOARDING HOME)"; SS_BOARD=$(echo "$BODY" | jq -r .id)
req "student stop: mesma acao 2x na rota (sem dias = todos)" 409 POST "/api/route/stop/$SCHOOL_STOP/add-student/$STUDENT" "$D1" "$(ss BOARDING SCHOOL)"
req "student stop: SCHOOL fora do ponto da escola" 400 POST "/api/route/stop/$HOME_STOP/add-student/$STUDENT" "$D1" "$(ss ALIGHTING SCHOOL)"
req "student stop: desembarque na escola" 200 POST "/api/route/stop/$SCHOOL_STOP/add-student/$STUDENT" "$D1" "$(ss ALIGHTING SCHOOL)"; SS_ALIGHT=$(echo "$BODY" | jq -r .id)
assert "aluno 2x na rota (embarque + desembarque)" "[ \"\$(psql_audit -c \"SELECT count(*) FROM student_stops ss JOIN route_stops rs ON rs.id=ss.stop_id WHERE rs.route_id='$ROUTE' AND ss.student_id='$STUDENT'\")\" = 2 ]"
req "R1 cria irmao" 200 POST /api/student/create-student "$R1" "$STUDENT_JSON"; STUDENT3=$(echo "$BODY" | jq -r .studentResponseDTO.id)
req "D1 propoe contrato do irmao" 201 POST "/api/chats/$CHAT/contract" "$D1" "$(contract "$STUDENT3")"
CONTRACT3=$(echo "$BODY" | jq -r .contract.contractId)
req "R1 aceita contrato do irmao" 200 PUT "/api/chats/$CHAT/contracts/$CONTRACT3/accept" "$R1"
req "student stop: irmao no MESMO ponto" 200 POST "/api/route/stop/$HOME_STOP/add-student/$STUDENT3" "$D1" "$(ss BOARDING HOME)"; SS_SIB=$(echo "$BODY" | jq -r .id)
assert "ponto da esquina tem 2 alunos" "[ \"\$(psql_audit -c \"SELECT count(*) FROM student_stops WHERE stop_id='$HOME_STOP'\")\" = 2 ]"

assert "ordem inicial: esquina(1) escola(2) padaria(3)" "[ \"\$(order)\" = \"$HOME_STOP:1,$SCHOOL_STOP:2,$THIRD_STOP:3\" ]"
req "troca: padaria 3 <-> 1" 200 PUT "/api/route/stop/$THIRD_STOP/swap-order" "$D1" '{"orderIndex":1}'
assert "so os dois trocaram: padaria(1) escola(2) esquina(3)" "[ \"\$(order)\" = \"$THIRD_STOP:1,$SCHOOL_STOP:2,$HOME_STOP:3\" ]"
req "troca para posicao vazia" 400 PUT "/api/route/stop/$HOME_STOP/swap-order" "$D1" '{"orderIndex":99}'
req "troca sem valor" 400 PUT "/api/route/stop/$HOME_STOP/swap-order" "$D1" '{}'
req "troca por D2" 403 PUT "/api/route/stop/$HOME_STOP/swap-order" "$D2" '{"orderIndex":1}'
req "troca ponto inexistente" 404 PUT "/api/route/stop/00000000-0000-0000-0000-000000000000/swap-order" "$D1" '{"orderIndex":1}'
req "route stop novo depois das trocas" 200 POST "/api/route/$ROUTE/stops" "$D1" "$(stop_json 'Rua Nova')"; NEW_STOP=$(echo "$BODY" | jq -r '.[0].id')
assert "novo ponto entra no fim (ordem 4)" "[ \"\$(echo \"\$BODY\" | jq '.[0].orderIndex')\" = 4 ]"


echo "   dias da semana"
req "irmao: desembarque na escola seg/qua" 200 POST "/api/route/stop/$SCHOOL_STOP/add-student/$STUDENT3" "$D1" '{"action":"ALIGHTING","locationType":"SCHOOL","weekdays":["MONDAY","WEDNESDAY"]}'; SS_SIB_MW=$(echo "$BODY" | jq -r .id)
assert "dias gravados" "[ \"\$(echo \"\$BODY\" | jq -r '.weekdays | join(\",\")')\" = \"MONDAY,WEDNESDAY\" ]"
req "irmao: desembarque em outro ponto ter/qui (dias diferentes)" 200 POST "/api/route/stop/$THIRD_STOP/add-student/$STUDENT3" "$D1" '{"action":"ALIGHTING","locationType":"OTHER","weekdays":["TUESDAY","THURSDAY"]}'
SS_SIB_TT=$(echo "$BODY" | jq -r .id)
req "irmao: desembarque na quarta de novo (sobrepoe)" 409 POST "/api/route/stop/$NEW_STOP/add-student/$STUDENT3" "$D1" '{"action":"ALIGHTING","locationType":"OTHER","weekdays":["WEDNESDAY"]}'
assert "409 e pela sobreposicao de dias" "echo \"\$BODY\" | grep -q 'algum desses dias'"
req "dia invalido" 400 POST "/api/route/stop/$THIRD_STOP/add-student/$STUDENT3" "$D1" '{"action":"BOARDING","locationType":"HOME","weekdays":["FUNDAY"]}'
req "pendencias da rota" 200 GET "/api/route/$ROUTE/pending" "$D1"
assert "irmao sem desembarque sex/sab/dom; aluno 1 completo" "echo \"\$BODY\" | jq -e 'length==1 and .[0].studentId==\"$STUDENT3\" and (.[0].missingAlighting|join(\",\"))==\"FRIDAY,SATURDAY,SUNDAY\" and (.[0].missingBoarding|length)==0' >/dev/null"
req "pendencias por D2" 403 GET "/api/route/$ROUTE/pending" "$D2"
req "GET pontos traz alunos e escolas com ids" 200 GET "/api/route/$ROUTE/stops" "$D1"
assert "ponto da escola lista a escola e os alunos" "echo \"\$BODY\" | jq -e '.[] | select(.id==\"$SCHOOL_STOP\") | (.schools | length == 1) and (.students | length == 2)' >/dev/null"
assert "student stop traz o nome do aluno" "echo \"\$BODY\" | jq -e '.[] | .students[] | select(.studentId==\"$STUDENT3\" and (.studentName|length>0))' >/dev/null"
assert "student stop listado com id e dias" "echo \"\$BODY\" | jq -e '.[] | .students[] | select(.id==\"$SS_SIB_TT\" and (.weekdays | join(\",\")) == \"TUESDAY,THURSDAY\")' >/dev/null"
req "editar dias: ter/qui -> ter/qui/sex" 200 PUT "/api/route/student-stop/$SS_SIB_TT/weekdays" "$D1" '{"weekdays":["TUESDAY","THURSDAY","FRIDAY"]}'
assert "dias editados" "[ \"\$(echo \"\$BODY\" | jq -r '.weekdays | join(\",\")')\" = \"TUESDAY,THURSDAY,FRIDAY\" ]"
req "editar dias batendo com o seg/qua" 409 PUT "/api/route/student-stop/$SS_SIB_TT/weekdays" "$D1" '{"weekdays":["MONDAY"]}'
req "editar dias por D2" 403 PUT "/api/route/student-stop/$SS_SIB_TT/weekdays" "$D2" '{"weekdays":["SATURDAY"]}'
req "pendencias depois da edicao" 200 GET "/api/route/$ROUTE/pending" "$D1"
assert "agora faltam so sab/dom" "echo \"\$BODY\" | jq -e '.[0].studentId==\"$STUDENT3\" and (.[0].missingAlighting|join(\",\"))==\"SATURDAY,SUNDAY\"' >/dev/null"
req "excluir desembarques do irmao (limpeza)" 204 DELETE "/api/route/student-stop/$SS_SIB_TT" "$D1"
req "excluir desembarque seg/qua do irmao" 204 DELETE "/api/route/student-stop/$SS_SIB_MW" "$D1"

echo "   mover e excluir"
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
req "excluir student stop do irmao" 204 DELETE "/api/route/student-stop/$SS_SIB" "$D1"
req "excluir ponto vazio (esquina, ordem 3)" 204 DELETE "/api/route/stop/$HOME_STOP" "$D1"
assert "pontos de baixo subiram: padaria(1) escola(2) nova(3)" "[ \"\$(order)\" = \"$THIRD_STOP:1,$SCHOOL_STOP:2,\$(psql_audit -c \"SELECT id FROM route_stops WHERE route_id='$ROUTE' AND order_index=3\"):3\" ] && [ \"\$(psql_audit -c \"SELECT count(*) FROM route_stops WHERE route_id='$ROUTE'\")\" = 3 ]"
req "excluir ponto por D2" 403 DELETE "/api/route/stop/$SCHOOL_STOP" "$D2"
req "escola na rota sem alunos" 200 POST "/api/route/stop/$NS_STOP/add-school/$SCHOOL" "$D1"; NS_SCHOOL_STOP=$(echo "$BODY" | jq -r .id)
req "excluir escola sem alunos usando" 204 DELETE "/api/route/school-stop/$NS_SCHOOL_STOP" "$D1"
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
req "D2 ve o usuario R1" 200 GET "/api/users/$R1_ID" "$D2"
assert "usuario traz photoKey" "[ \"\$(echo \"\$BODY\" | jq -r .photoKey)\" = \"$PHOTO\" ]"
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
req "GET usuario" 200 GET "/api/users/$R1_ID" "$R1"
assert "GET usuario nao expoe passwordHash" "! echo \"\$BODY\" | grep -qi 'passwordHash\|\\\$2a\\\$'"
req "GET usuario inexistente" 404 GET "/api/users/00000000-0000-0000-0000-000000000000" "$R1"
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
echo "TOTAL: $((PASS+FAIL)) cenarios | PASS: $PASS | FAIL: $FAIL"
