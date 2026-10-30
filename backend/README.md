# VanRoute — Backend: rotas da API (o que mudou na branch `routeLogic`)

Guia para quem consome a API, principalmente o front. Para cada endpoint criado ou alterado, mostra **o que mandar**, **o que volta** e **as regras**. O relatório completo da auditoria (bugs, testes, pendências) está em [`docs/RELATORIO_AUDITORIA_VANROUTE.md`](../docs/RELATORIO_AUDITORIA_VANROUTE.md).

Outros guias:
- **[`docs/FLUXO_FRONT.md`](../docs/FLUXO_FRONT.md)**: como montar o front (telas, a ordem das chamadas, os WebSockets e a renovação do token);
- [`docs/FLUXO_ROTA.md`](../docs/FLUXO_ROTA.md): endereços, rota e pontos, do cadastro até a rota pronta (cada entidade, DTO e verificação);
- [`docs/FLUXO_TRIP.md`](../docs/FLUXO_TRIP.md): execução da rota, faltas e notificações, com todas as entradas e saídas;
- [`docs/FLUXO_CONTRATO.md`](../docs/FLUXO_CONTRATO.md): contrato passo a passo.

Base: `http://localhost:9090`. Todos os endpoints abaixo exigem `Authorization: Bearer <token>`, exceto os de cadastro e login.

**Logo depois do login**, o front chama **`GET /api/users/me`**:
```json
{ "id": "uuid", "name": "...", "email": "...", "phone": "...", "cpf": "...",
  "roles": ["ROLE_DRIVER"], "photoKey": "profile/... ou null", "linkCode": "ABCD2345K ou null" }
```
- O `id` é o que o front compara com o `senderId` das mensagens, para saber quais são suas. Ele também está no `sub` do token JWT, então o front pode lê-lo direto do token (`jwtDecode(token).sub`).
- O `linkCode` só vem para motorista. É o código que ele passa para os pais, pessoalmente ou de boca, e que eles usam em `POST /api/invite-code/redeem`.

**Erros** sempre vêm assim:
```json
{ "timestamp": "...", "message": "texto do erro", "details": "uri=/api/..." }
```

| Status | Quando |
|---|---|
| 400 | dado inválido ou regra violada |
| 401 | sem token ou token inválido/expirado |
| 403 | sem permissão |
| 404 | não existe |
| 409 | conflito (duplicado ou estado errado) |

---

## 1. Configuração da rota

### O que mudou (endereços e montagem da rota)
| Antes | Agora |
|---|---|
| o motorista **digitava o endereço** de cada ponto | ele **escolhe** um endereço do aluno ou a escola; endereço digitado é recusado (400) |
| cada ponto criava **outro registro** de endereço e buscava a coordenada de novo na API de mapas | o ponto usa **o mesmo registro** do aluno ou da escola: zero cópia, zero chamada |
| a coordenada do ponto podia não bater com a casa (erro de digitação) e quebrar a regra de 1 km | é a coordenada exata da casa ou da escola |
| o motorista podia pôr a criança num endereço que o pai nunca informou | só nos endereços que o pai cadastrou |
| o aluno tinha 1 endereço, salvo e nunca usado | o aluno tem **vários** endereços com apelido ("Casa", "Avó", "Natação"), e é deles que nascem os pontos |
| digitar o mesmo endereço de novo criava outro registro | o **mesmo CEP + número** reaproveita o endereço que já existe |
| montar a rota em 2 passos (criar o ponto e depois ligar o aluno), na ordem certa por conta do motorista | **1 passo:** "adicionar aluno na rota". O backend cria ou reaproveita o ponto e põe na ordem certa. A escola entra quando o aluno embarca ou desce nela (`SCHOOL`) |
| `schools.address_id` era único | duas escolas no mesmo prédio dividem o endereço |
| a escola entrava na rota à parte | a escola entra quando o aluno embarca ou desce nela (`locationType: SCHOOL`): cria se não existe, reaproveita se existe. `HOME`/`OTHER` não cria escola |
| a posição do ponto era sempre "no fim" | automática (embarque antes de onde o aluno desce, desembarque no fim) ou escolhida com `orderIndex` |
| `POST /{routeId}/stops`, `POST /stop/{routeStopId}/add-student/{studentId}` e `POST /stop/{routeStopId}/add-school/{schoolId}` | **removidas**: tudo entra pelo `POST /{routeId}/add-student/{studentId}`. O ponto nasce do endereço do aluno (`HOME`/`OTHER`) ou é a escola do contrato (`SCHOOL`) |
| cada alteração devolvia uma coisa (o ponto, o student stop, `204`) | **toda alteração da rota** (pôr aluno, trocar ordem, mover, dias, excluir) devolve `{ stops, pending }`: a rota inteira e o que ainda falta |

### Conceitos
- **Rota (`route`)** é a *configuração* de uma viagem, por exemplo "Ida manhã" ou "Volta tarde". Ida e volta são rotas diferentes. Só o motorista que criou a rota pode ver e alterar.
- **Ponto (`route stop`)** é onde a van para: um endereço e uma ordem (`orderIndex`). A ordem diz só quem vem antes de quem. Um ponto pode ter vários alunos e escolas.
- **School stop** liga uma escola a um ponto. A escola pode ficar no meio da rota.
- **Student stop** liga um aluno a um ponto, com:
  - `action`: `BOARDING` (embarque) ou `ALIGHTING` (desembarque);
  - `locationType`: `HOME`, `SCHOOL` ou `OTHER`;
  - `weekdays`: os dias em que o aluno usa aquele ponto. Sem dias = todos os dias.
- A **execução** (trip/boarding) segue os pontos pela ordem e, em cada um, pega os alunos daquele dia. Um ponto sem aluno no dia é pulado. Falta num dia é coisa do boarding; a configuração não muda. Veja a seção 1c e `docs/FLUXO_TRIP.md`.

### Passo a passo (motorista)
```
1. POST /api/route/create-route                        cria a rota ("Ida manha"; a volta e outra rota)
2. GET  /api/route/stop-options                        alunos dele (com os enderecos)
3. POST /api/route/{routeId}/add-student/{studentId}   "adicionar aluno na rota" (1 passo, de novo pro desembarque)
4. GET  /api/route/{routeId}/pending                   lista vazia = rota pronta pra viagem
5. ajustes: swap-order, move, weekdays e os DELETEs
```
Esse é o **único** jeito de criar ponto: não existe ponto sem aluno. Duas escolas no mesmo prédio caem no mesmo ponto quando o aluno de cada uma entra na rota.

### Adicionar aluno na rota (1 passo)
`POST /api/route/{routeId}/add-student/{studentId}`
```json
{ "action": "BOARDING", "locationType": "HOME", "studentAddressId": "<Casa da Ana>", "weekdays": ["MONDAY", "TUESDAY"] }
{ "action": "ALIGHTING", "locationType": "SCHOOL" }
```
- **`HOME`/`OTHER`:** manda o `studentAddressId` (um endereço desse aluno, de `stop-options`).
- **`SCHOOL`:** não manda endereço; o ponto é o da **escola do contrato**.
- Sem `weekdays` = todos os dias.
- `orderIndex` (opcional, a partir de 1): a posição do ponto **se ele for criado agora**. Sem ele, a posição é automática (abaixo). Ponto que já existe não se mexe (para isso, `swap-order`). Posição que quebra a ordem embarque → desembarque dá `400`.

O backend:
1. acha o ponto: **`HOME`/`OTHER`** = o ponto daquele endereço do aluno; **`SCHOOL`** = o ponto da escola do contrato (route stop + school stop). Se a rota já tem, **usa o mesmo** (irmãos na mesma casa e alunos da mesma escola caem no mesmo ponto); senão, cria. `HOME`/`OTHER` **nunca** cria a escola;
2. põe o ponto novo **na ordem certa**: um **embarque** entra **antes** de onde o aluno desce (antes dos desembarques dele e, se não é na escola, antes da escola do contrato, se ela já está na rota), e os de baixo descem uma posição. Um **desembarque** entra no fim. O backend **não sabe se a rota é ida ou volta**: olha só a ação desse aluno;
3. cria **um** student stop, só com a ação que você mandou (embarque e desembarque são duas chamadas), com as regras da seção "Regras do aluno na rota". Se algo falhar, **nada fica gravado**.

O que o backend garante sempre é a **ordem** (embarque antes do desembarque); o lugar de cada ponto é decisão do motorista, que pode reordenar depois (`swap-order`, `move`).

→ `200`: `{ "stops": [...], "pending": [...] }` (veja "Resposta das alterações").
- **400:** `SCHOOL` com `studentAddressId`, `HOME`/`OTHER` sem ele, endereço de outro aluno, ou quebra de alguma regra (ordem, ponto de escola...).
- **403:** sem contrato ativo com o aluno, ou a rota é de outro motorista.
- **409:** a mesma ação no mesmo dia, ou o aluno já está nesse ponto.

**Exemplo, rota de volta:**
1. Ana desce em casa: cria a casa (1). A rota fica com a pendência "Ana sem embarque".
2. Ana embarca na escola: cria a escola **antes** de onde ela desce: escola (1), casa (2).
3. O irmão desce em casa: usa o ponto 2 (é o mesmo endereço).
4. O irmão embarca na escola: usa o ponto 1 (a mesma escola).

### Criar rota
`POST /api/route/create-route`
```json
{ "name": "Ida Manhã" }
```
→ `201`:
```json
{ "id": "uuid", "name": "Ida Manhã", "driverName": "Nome do motorista" }
```

### Endereços: quem cadastra o quê
| Endereço | Quem cadastra | Pra quê |
|---|---|---|
| do **responsável** (`responsibles.address_id`) | o pai, no cadastro | **cobrança** (pagamento, contrato). Não entra na rota |
| do **aluno** (`student_addresses`, vários, com apelido) | o pai | **onde o aluno é buscado ou deixado**: "Casa", "Casa da avó", "Inglês de terça" |
| da **escola** | o motorista, ao cadastrar a escola | o ponto da escola |

O ponto da rota **não tem endereço digitado**: ele usa o mesmo endereço do aluno ou da escola, sem buscar a coordenada de novo na API de mapas. Os **dias da semana** continuam no student stop.

**Endereço sem repetição:** todo endereço novo passa pelo mesmo lugar (`AddressService.findOrCreate`). O endereço é o mesmo quando tem **o mesmo CEP e o mesmo número**; o jeito de escrever a rua não importa. Se o CEP termina em `000` (pode ser o CEP único de uma cidade pequena), ele compara também a rua e a cidade. Então:
- se **já existe** um endereço com essa chave, **só liga nele** (não cria outro e não chama a API de mapas);
- se não existe, cria e busca a coordenada uma vez.

"Avenida Paulista, 1000, 01310-100" e "AV. PAULISTA, 1000, 01310100" são o mesmo endereço. A coordenada que vier do app é ignorada: quem define é a busca. Como o endereço é compartilhado, ele **nunca é editado**; mudou o endereço, liga num novo.

**Endereços do aluno (responsável):**

| Endpoint | O que faz |
|---|---|
| `GET /api/student/{id}/addresses` | `[{ id, label, address }]`, os ativos. O `id` é o `studentAddressId` |
| `POST /api/student/{id}/addresses` `{label, address}` | endereço novo (busca a coordenada uma vez). `201` |
| `POST /api/student/{id}/addresses` `{label, addressId}` | **reaproveita** um endereço que já existe: de um irmão ou o de cobrança do responsável. Irmãos na mesma casa ficam no mesmo ponto. `403` se o endereço não é dele nem de um filho dele; `409` se o aluno já tem esse endereço |
| `DELETE /api/student/{id}/addresses/{studentAddressId}` | tira o endereço do aluno: apaga **só a ligação** (o endereço continua pra quem mais usa, ex.: o irmão). O aluno **sai dos pontos de casa** com esse endereço (a rota fica com pendência), e o motorista recebe a notificação `ROUTE` "Endereço removido". `409` se for o último endereço do aluno, ou se tiver uma viagem rodando com ele agora |

No cadastro do aluno (`POST /api/student/create-student`):
- **um endereço:** `address` (novo) **ou** `addressId` (reaproveita o do irmão ou o seu), e `addressLabel` (opcional). Vira o "Casa";
- **vários de uma vez:** `"addresses": [{ "label": "Casa", "addressId": "..." }, { "label": "Natação", "address": { ... } }]`. O primeiro sem apelido vira "Casa". O mesmo endereço duas vezes dá `400`.

### Quem pode entrar na rota
`GET /api/route/stop-options` (motorista):
```json
{ "students": [ { "studentId": "...", "studentName": "Ana",
                  "addresses": [ { "id": "<studentAddressId>", "label": "Casa", "address": { ... } } ] } ] }
```
Só aparecem os alunos com contrato **ativo** com ele. O `id` de cada endereço é o `studentAddressId` do `add-student`. A escola não aparece: é sempre a do contrato.

### Resposta das alterações
Tudo que altera a rota (`add-student`, `swap-order`, `move`, `weekdays` e os três `DELETE`) devolve `200` com a rota inteira **já alterada** e as pendências:
```json
{ "stops":   [ { "id": "...", "orderIndex": 1, "address": { ... }, "students": [ ... ], "schools": [ ... ] } ],
  "pending": [ { "studentId": "...", "studentName": "Ana", "missingBoarding": [], "missingAlighting": ["FRIDAY"] } ] }
```
`pending` vazio = rota pronta. Com pendência a **viagem não inicia** (veja "Iniciar"), então o front pode mostrar o aviso na hora ("Ana: falta desembarque na sexta").

**Formato de um ponto** (também é o formato do GET):
```json
{
  "id": "uuid do route stop",
  "orderIndex": 1,
  "address": { "id": "...", "street": "...", "zipCode": "...", "city": "...", "neighborhood": "...",
               "number": 10, "state": "SP", "latitude": -23.5, "longitude": -46.6 },
  "students": [ { "id": "uuid do student stop", "studentId": "...", "studentName": "Ana", "stopId": "...",
                  "locationType": "HOME", "action": "BOARDING", "weekdays": ["MONDAY", "WEDNESDAY"] } ],
  "schools":  [ { "id": "uuid do school stop", "schoolId": "...", "stopId": "..." } ]
}
```
Os `id` de `students` e `schools` são os usados para mover e excluir.

### Regras do aluno na rota
O `studentId` vem de `GET /api/contract/my-contracts?status=ACTIVE`, a lista de alunos com quem o motorista tem contrato ativo. `weekdays` vale em qualquer `locationType` (inclusive `SCHOOL`: "desce na escola só seg/qua") e, sem ele, vale todos os dias (segunda a domingo).

Regras (no `add-student` e também no `move`, que muda o aluno de ponto):
- **400:** `HOME`/`OTHER` num endereço que **não é um endereço cadastrado do aluno**, ou num ponto de escola.
- **403:** não existe contrato **ATIVO** entre o motorista e o aluno.
- **400:** `SCHOOL` fora do ponto da **escola do contrato** (no `add-student` ele já escolhe o ponto certo).
- **400:** o embarque ficaria **depois** do desembarque na ordem da rota.
- **409:** o aluno já tem a mesma ação nessa rota **num dos mesmos dias**. Pode haver, por exemplo, embarque na esquina A seg/qua e na esquina B ter/qui; não pode haver dois embarques na segunda.
- **409:** o aluno já está nesse ponto.

O caso comum é **um** embarque e **um** desembarque, sem dias.

### Limitações conhecidas (por enquanto)
- **Um ponto por endereço na rota.** Se a van precisa passar **duas vezes** no mesmo endereço na mesma rota, não dá: o segundo uso cai no mesmo ponto.
- **Uma escola por rota.** Se a van precisa parar na mesma escola **duas vezes** (deixar alguém lá antes de buscar outros alunos e voltar depois), não dá para ter um segundo ponto dessa escola.

Nos dois casos, o caminho hoje é dividir em duas rotas.

O que o front precisa mudar está em [`docs/FLUXO_FRONT.md`, seção 11](../docs/FLUXO_FRONT.md#11-o-que-o-front-precisa-mudar-endereços-e-rota).

### Ajustes
| Endpoint | Corpo | O que faz |
|---|---|---|
| `PUT /api/route/stop/{routeStopId}/swap-order` | `{ "orderIndex": 1 }` | **Troca** o ponto com o que está na posição pedida. Ex.: 10 ↔ 1, o resto não muda. `400` se não houver ponto na posição |
| `PUT /api/route/student-stop/{studentStopId}/move` | `{ "routeStopId": "..." }` | Muda o aluno para outro ponto **da mesma rota** (`400` se for de outra rota). Se for `SCHOOL`, o destino precisa ser o ponto da escola |
| `PUT /api/route/student-stop/{studentStopId}/weekdays` | `{ "weekdays": ["TUESDAY"] }` | Troca os dias **desse** student stop (vazio = todos). `409` se bater com outro embarque/desembarque do aluno |
| `DELETE /api/route/student-stop/{studentStopId}` | — | Remove o aluno do ponto, sem regra |
| `DELETE /api/route/school-stop/{schoolStopId}` | — | Remove a escola do ponto. `409` se algum aluno dela ainda embarca ou desce ali (`SCHOOL`) |
| `DELETE /api/route/stop/{routeStopId}` | — | Remove o ponto. `409` se ainda tem aluno ou escola. Os pontos de baixo sobem uma posição |

Todos devolvem `{ stops, pending }` (seção "Resposta das alterações").

### Consultas
| Endpoint | Quem | Retorno |
|---|---|---|
| `GET /api/route/{routeId}/stops` | dono da rota | pontos em ordem, com alunos e escolas |
| `GET /api/route/get-all-routes-and-stops` | motorista | `[{ id, name, driverName, stops: [...] }]`, só as rotas dele |
| `GET /api/route/{id}` | dono da rota | `{ id, name, driverName }` |
| `GET /api/route/{routeId}/pending` | dono da rota | `[{ studentId, studentName, missingBoarding: [...], missingAlighting: [...] }]`: dias em que o aluno aparece mas falta embarque ou desembarque. Lista vazia = rota completa |
| `GET /api/student/{studentId}/routes` | responsável do aluno | `[{ id, name }]`: só o nome das rotas em que o filho está |
| `GET /api/school/my-schools` | motorista | `[{ id, name, phone, email, address }]` |

---

## 1b. Van (veículo)

A van é cadastrada pelo **CRLV digital (PDF)**. O backend:
1. valida a assinatura ICP-Brasil;
2. lê os dados do veículo do próprio PDF (sem OCR);
3. vincula a van ao motorista.

O CRLV **não precisa** estar no nome do motorista, e o PDF **não é guardado**.

| Endpoint | Quem | O que faz |
|---|---|---|
| `POST /api/vehicle/add-vehicle` (multipart, campo `documentPdf`) | motorista | `201` com a van. Se a van já existe (procura pelo **RENAVAM**, que nunca muda, e depois pela **placa**), só **vincula** o motorista à van existente, sem alterar os dados dela. A assinatura é validada sempre, para ninguém se vincular a uma van com um PDF falso. `422` se o PDF não for um CRLV digital válido. `409` se o motorista já está vinculado a essa van |
| `GET /api/vehicle/my-vehicles` | motorista | vans **ativas** do motorista (ele pode ter várias) |
| `PUT /api/vehicle/{vehicleId}/unlink` | motorista | o motorista para de usar a van (`end_date` = hoje). O histórico fica, e depois ele pode vincular de novo. `204` |

Resposta:
```json
{ "id": "...", "plate": "ABC1D23", "renavam": "12345678901", "chassis": "9BM...", "model": "M.BENZ/SPRINTER 415",
  "year": 2020, "manufactureYear": 2019, "color": "BRANCA", "fuel": "DIESEL", "category": "ALUGUEL",
  "capacity": 16, "licensingYear": 2026, "status": "ACTIVE", "linkedSince": "2026-10-21" }
```
`capacity` é o **número de passageiros** (campo LOTAÇÃO do CRLV). `year` é o ano do modelo, e `licensingYear` é o exercício (ano do licenciamento).

## 1c. Execução da rota (trip)

A **trip** é a viagem de uma rota num dia. Cada aluno do dia vira um **boarding** (`PENDING` → `BOARDED` → `DELIVERED`, ou `ABSENT`). O "hoje" é sempre no horário de São Paulo.

**Iniciar:** `POST /api/trip/start/{routeId}` com `{ "latitude", "longitude", "vehicleId"? }`, a posição atual do GPS do motorista.
- **Regras:**
  - só o motorista da rota;
  - a rota não pode ter pendências: `400` dizendo quem e o que falta ("Ana sem desembarque [FRIDAY]");
  - a van é a enviada ou a **van padrão da rota** (`PUT /api/route/{routeId}/set-vehicle/{vehicleId}`), e tem que ser uma van ativa do motorista.
- **Alunos do dia:** quem tem student stop nessa rota com o dia de hoje. Quem tem **falta avisada** já entra `ABSENT`.
- **Pontos:** só os que têm aluno hoje, na ordem. Escola sem aluno no dia é pulada.
- **Trajeto:** calculado pela API de rotas (OpenRouteService), da posição do motorista até os pontos que faltam. Fica no Redis.
- **Trip já existente:**
  - já existe trip hoje → `409`. O app chama `GET /api/trip/route/{routeId}/today` para **continuar**, ou inicia de novo com `?restart=true` para **recomeçar do zero** (a antiga vira `CANCELED`);
  - trip já finalizada hoje → `409`.

| Endpoint | Quem | O que faz |
|---|---|---|
| `GET /api/trip/{tripId}` | motorista | trip com os pontos do dia (alunos, status, `done`) e o trajeto |
| `PUT /api/trip/{tripId}/student/{studentId}/board` | motorista | `PENDING` → `BOARDED` (hora do embarque); a trip vira `IN_PROGRESS` |
| `PUT /api/trip/{tripId}/student/{studentId}/alight` | motorista | `BOARDED` → `DELIVERED` (hora do desembarque) |
| `PUT /api/trip/{tripId}/student/{studentId}/absent` | motorista | **pula o aluno** só nessa trip (não apareceu, ninguém respondeu). Só se ainda não embarcou. Recalcula o trajeto |
| `POST /api/trip/{tripId}/finish` | motorista | `FINISHED` com hora de fim |
| `GET /api/student/{studentId}/today-trips` | responsável | trips de hoje do filho, para pegar o `tripId` e abrir o WebSocket |

### Tempo real: WebSocket `ws://<host>/trip/{tripId}?token=<jwt>`
Sem polling: ao conectar, cada um já recebe o estado atual, e depois recebe uma mensagem a cada mudança (GPS, embarque, falta, fim).

- **Motorista** manda o GPS pelo próprio socket, a cada poucos segundos:
  ```json
  { "type": "position", "latitude": -23.55, "longitude": -46.63 }
  ```
  Ele recebe a trip inteira: todos os pontos com os tempos, todos os alunos e o trajeto.
- **Responsável** só recebe (se mandar posição, volta `{"type":"error"}`). Ele vê **só os filhos dele** e o status deles. **A van, o trajeto e o tempo restante só aparecem quando o próximo ponto da van é um ponto do filho dele**:
  - indo buscar na casa: vê a van chegando e o tempo;
  - o filho embarcou, e a van vai buscar os outros: só o status;
  - indo para a escola do filho: vê de novo;
  - entregue: só o status.

  Quem não é motorista nem responsável de um aluno da trip é desconectado (código `1003`).

Mensagem (`type: "trip-update"`):
```json
{ "type": "trip-update", "tripId": "...", "routeName": "Ida", "status": "IN_PROGRESS",
  "following": true, "latitude": -23.55, "longitude": -46.63, "positionAt": 1792585537168,
  "targetStopId": "...", "etaSeconds": 420.0,
  "path": [[-46.63, -23.55], [-46.631, -23.551], "..."],
  "students": [ { "studentId": "...", "studentName": "Ana", "status": "PENDING", "action": "BOARDING", "locationType": "HOME" } ],
  "stops": null }
```
- **`following`:** se o pai está acompanhando agora. Quando é `false`, a posição, o trajeto e o tempo vêm `null`.
- **`targetStopId` e `etaSeconds`:** o ponto para onde a van está indo e os **segundos que faltam** até ele.
- **`path`:** a linha do mapa (`[longitude, latitude]`). Para o pai, ela vai só até o ponto do filho.
- **`stops`:** só para o motorista, com todos os pontos e o `etaSeconds` de cada um.

**Trajeto e tempo:** a linha vem da API de rotas como uma lista de pontos (centenas, em ruas com curvas). O front desenha com Leaflet + OpenStreetMap e move o ícone da van. A API de rotas é chamada:
- ao iniciar;
- quando um aluno sai da trip;
- com o GPS chegando, a cada **5 min** (`TRIP_ETA_REFRESH_SECONDS`), ou na hora se a van sair da linha (mais de 150 m, `TRIP_OFF_ROUTE_METERS`). A cota da chave é de 200 rotas por dia.

Entre uma chamada e outra, o tempo restante só desconta. A chave da API fica só no backend.

**Navegação do motorista:** o front pode abrir o Google Maps ou o Waze com os pontos da trip em ordem (latitude e longitude vêm no `GET /api/trip/{tripId}`), sem chave nenhuma.

**Falta avisada pelo responsável** (vale para uma rota num dia; pode ser com antecedência):

| Endpoint | O que faz |
|---|---|
| `POST /api/student/{studentId}/absences` `{routeId, date}` | o filho não vai nessa rota nesse dia. Se a trip já está rolando, ele sai **na hora** e o trajeto é recalculado. O motorista recebe mensagem automática no chat (`messageType: ABSENCE`) |
| `GET /api/student/{studentId}/absences` | faltas avisadas de hoje em diante |
| `DELETE /api/student/{studentId}/absences/{absenceId}` | "ele vai sim": volta para a trip (se ainda não passou) e o motorista é avisado |

## 2. Contrato

A proposta nasce no chat. Criar, aceitar e recusar ficam no `ChatController`; a consulta fica no `ContractController`.

| Endpoint | Quem | Retorno |
|---|---|---|
| `GET /api/chats/{chatId}/students` | só o **motorista** que participa do chat | `[{ id, name }]`: filhos do pai da conversa de que ele é admin (o botão "ver filhos" usado para escolher o aluno da proposta) |
| `POST /api/chats/{chatId}/contract` | motorista | `201`: `{ message, userDriverContractId, contract }`, onde `contract` já vem completo (formato abaixo) |
| `PUT /api/chats/{chatId}/contracts/{contractId}/accept` | responsável do contrato | `200`; o contrato fica `ACTIVE` e as parcelas são geradas |
| `PUT /api/chats/{chatId}/contracts/{contractId}/reject` | responsável do contrato | `200`; o contrato fica `CANCELED` |
| `GET /api/contract/my-contracts?status=ACTIVE` | motorista ou responsável | `[{ contractId, userDriverContractId, status, periodicity, value, startDate, endDate, studentId, studentName, schoolId, schoolName, driverId, driverName, responsibleId, responsibleName }]`: os contratos em que o usuário é parte; `status` é opcional |
| `GET /api/contract/{contractId}` | só as partes do contrato | detalhes (abaixo); os outros recebem `403` |

Corpo da proposta. `responsibleId` é o `otherUserId` do chat (`GET /api/chats`), `studentId` vem de `GET /api/chats/{chatId}/students` e `schoolId` vem de `GET /api/school/my-schools`:
```json
{ "responsibleId": "...", "studentId": "...", "schoolId": "...",
  "contract": { "periodicity": "MONTHLY", "value": 350.00, "startDate": "2027-02-01", "endDate": "2027-06-30" },
  "document": { "url": "<fileKey do PDF no MinIO>", "type": "TRANSPORT_CONTRACT" } }
```

Detalhes do contrato:
```json
{ "contractId": "...", "userDriverContractId": "...", "status": "PENDING", "periodicity": "MONTHLY",
  "value": 350.00, "startDate": "...", "endDate": "...",
  "studentId": "...", "studentName": "...", "schoolId": "...", "schoolName": "...", "schoolAddress": { ... },
  "driverId": "...", "driverName": "...", "responsibleId": "...", "responsibleName": "...",
  "documentFileKey": "...", "documentType": "TRANSPORT_CONTRACT" }
```
A mensagem PROPOSAL do chat traz só os IDs no `payload`. Para mostrar a proposta, o front chama `GET /api/contract/{contractId}` com o `contractId` do payload. Os dados vêm sempre atualizados do banco.

---

## 3. Chat

`POST /api/chats/{chatId}/messages`
```json
{ "content": "texto", "attachmentType": "IMAGE", "attachmentUrl": "<fileKey>" }
```
- Precisa de texto e/ou anexo.
- **Não** aceita `messageType` nem `payload`: toda mensagem de usuário é `REGULAR_CHAT`. As mensagens `PROPOSAL`, `APPROVAL` e `REJECTION` só o sistema gera, no fluxo de contrato.

Resposta (também é o que chega pelo WebSocket):
```json
{ "id": "...", "senderId": "...", "content": "...", "attachmentUrl": "...", "attachmentType": "IMAGE",
  "sentAt": "2026-10-21T08:30:00", "messageType": "REGULAR_CHAT", "payload": null }
```
Use **`messageType`** para saber se é proposta, aceite ou recusa.

**Tempo real:** conecte em `ws://localhost:9090/chat/{chatId}?token=<jwt>`. Cada mensagem enviada no chat chega como JSON para os dois participantes. Quem não participa do chat é desconectado (código `1003`).

`GET /api/chats` traz, por chat, `otherUserId`, `otherUserName` e `otherUserPhotoKey`.

---

## 4. Arquivos (MinIO)

O arquivo **não passa pelo backend**: o front envia e baixa direto do MinIO, usando links temporários (15 min) que o backend assina. O banco guarda só o **`fileKey`**, o nome do arquivo no bucket. O bucket é privado.

**Enviar (imagem/PDF do chat ou PDF do contrato):**
```
1. GET /api/storage/presigned-url?fileName=foto.jpeg&contentType=image/jpeg
   → { "uploadUrl": "...", "fileKey": "3f2a....jpeg" }
2. PUT <uploadUrl> com o arquivo no corpo e o header Content-Type igual ao do passo 1   (direto no MinIO)
3. usar o fileKey: attachmentUrl da mensagem ou document.url da proposta
```

**Baixar/exibir:**
```
1. GET /api/storage/download-url?fileKey=3f2a....jpeg
   → { "downloadUrl": "...", "fileKey": "..." }        ou 403
2. usar downloadUrl no <img src> ou no link do PDF
```

Quem pode baixar:

| Arquivo | Quem |
|---|---|
| anexo de chat | quem participa do chat |
| PDF de contrato | as partes do contrato |
| foto de perfil | qualquer usuário logado |

**Foto de perfil:**
```
1. GET /api/users/photo-upload-url?fileName=eu.jpeg&contentType=image/jpeg   (só image/*)
   → { "uploadUrl": "...", "fileKey": "profile/{seuId}/....jpeg" }
2. PUT <uploadUrl> com a imagem
3. PUT /api/users/update-photo   { "fileKey": "profile/{seuId}/....jpeg" }   → { "photoKey": "..." }
```
- Só aceita chave da sua própria pasta e que já exista no bucket; a foto antiga é apagada.
- O `photoKey` dos outros vem no `GET /api/chats` (`otherUserPhotoKey`); a sua vem no `/me`. Para exibir, use o `download-url`. (O `GET /api/users/{id}` foi removido.)

---

## 5. Outras mudanças de contrato da API

- **Cadastro de responsável** (`POST /api/responsible/auth/signup`):
  - agora valida CPF, senha forte, confirmação de senha e **endereço obrigatório**;
  - aceita o endereço em `address` ou `addressRequestDTO`;
  - envia o código de verificação por e-mail; o login só funciona depois do `POST /api/auth/verify-email`.
- **Cadastro de aluno** (`POST /api/student/create-student`): não tem mais `weekdays`; os dias ficam no student stop.
- **Escola** (`POST /api/school/add-school`): se a escola (nome + rua) já existe, o motorista é **vinculado** a ela, e não dá mais erro. `409` se já estiver vinculado.
- **Caminho removido:** `POST /api/student-stop/{studentId}/{routeStopId}` virou `POST /api/route/stop/{routeStopId}/add-student/{studentId}`, que depois também saiu: hoje é `POST /api/route/{routeId}/add-student/{studentId}`.
- **JWT:** agora expira (`JWT_EXPIRATION`, padrão 15 min), e token vencido dá `401`. O `sub` passou a ser o **id do usuário** (antes era o CPF). Tokens antigos dão `401`; basta logar de novo.
- **Renovar o token sem senha:** o login agora também devolve `refreshToken` (30 dias, `JWT_REFRESH_EXPIRATION`). Com token vencido (`401`), o front chama `POST /api/auth/refresh` `{"refreshToken"}`, recebe um par novo (o usado deixa de valer) e repete a chamada. `POST /api/auth/logout` `{"refreshToken"}` apaga. No Redis fica só o hash do refreshToken. Passo a passo pro front em `docs/FLUXO_TRIP.md`, seção 4.10.
- **Notificações:** `GET /api/notifications`, `GET /api/notifications/unread-count`, `PUT /api/notifications/{id}/read`, `PUT /api/notifications/read-all` e o WebSocket `ws://<host>/notifications?token=<jwt>` (veja `docs/FLUXO_TRIP.md`, seção 6).
- **No backend**, o `authentication.getName()` devolve o **id** do usuário. Use `AuthenticatedUser.id(authentication)` (um `UUID`, sem ir ao banco). Os services recebem `UUID userId`, e o CPF só é usado no login e na checagem de cadastro duplicado.
- **Código de atualização do usuário** (`send-code-updateUser` / `update-user`): agora é um por usuário (chave `User:SendCodeUser:{id}` no Redis). Antes a chave era única e um usuário podia usar o código do outro.

## 6. Banco (migrations novas)

| Migration | O que faz |
|---|---|
| V18 | antigo `V15__add_status...`, renomeado porque havia dois V15. Quem já tinha aplicado esse V15: `flyway repair` ou recriar o banco local |
| V19 | `student_stops.weekdays`; remove `student_stops.status`, `student_stops.route_id` e `student_addresses.weekdays` (sem uso) |
| V20 | `users.photo_key` |
| V22 | `trips.driver_id`; uma trip não cancelada por rota por dia; `UNIQUE (trip_id, student_id)` em `boardings`; `routes.vehicle_id` (van padrão); tabela `student_absences` (aluno + rota + data) |
| V21 | `vehicles`: `renavam` e `chassis` (únicos), `manufacture_year`, `color`, `fuel`, `category`, `licensing_year`. Em `driver_vehicles`, a UNIQUE (motorista, van) virou índice parcial `WHERE end_date IS NULL`: só um vínculo **ativo** por par, com histórico liberado |

## 7. Rodar e testar

```bash
docker compose up -d db redis minio
cd backend && ./mvnw clean package     # compila e roda os testes unitários
java -jar target/backend-0.0.1-SNAPSHOT.jar
```
Para a trip, o servidor também precisa de `ORS_DIRECTIONS_URL` (nos testes aponta para o simulador do `stubs.py`) e, nos testes, `TRIP_ETA_REFRESH_SECONDS=3`. O teste do WebSocket da trip é o `backend/api-tests/trip-ws-test.mjs`. Para ver a execução da rota e as notificações funcionando sem front: `bash backend/api-tests/demo/run-demo.sh` e abra http://localhost:8090 (motorista e 2 pais na mesma tela). Fluxo completo, entradas e saídas de todas as rotas e dos WebSockets (`/trip/{tripId}` e `/notifications`): `docs/FLUXO_TRIP.md`. Para testar contra a API real do OpenRouteService, suba o backend sem `ORS_GEOCODE_URL`/`ORS_DIRECTIONS_URL` (com a chave em `openrouteservice.api-key` no `application-local.properties`, que fica fora do git) e rode com `REAL_MAPS=1`: as contagens de chamadas, que só existem no simulador, aparecem como SKIP. Os testes de API ponta a ponta (servidor real, dados fictícios) estão em `backend/api-tests/`. Como rodar está no cabeçalho do `run-api-tests.sh` e na seção E do relatório.
