# Rota e pontos: como tudo é criado, do começo

Este guia mostra **cada entidade**, **o DTO** que cria ela e **a ordem**: do cadastro até a rota pronta pra rodar.
A execução da viagem está em `docs/FLUXO_TRIP.md`; o contrato, em `docs/FLUXO_CONTRATO.md`.

---

## 1. As entidades e quem aponta pra quem

```
addresses                 cada lugar existe UMA vez (mesmo CEP + número = mesmo endereço). Nunca é editado.
  ▲ responsibles.address_id         pai: 1 endereço, de COBRANÇA (não entra na rota)
  ▲ schools.address_id              escola: 1 endereço
  ▲ student_addresses               aluno: VÁRIOS endereços (onde é buscado/deixado), cada um com apelido
  ▲ route_stops.address_id          ponto da rota: o MESMO endereço de um aluno ou de uma escola

routes                    a rota ("Ida manhã", "Volta tarde"); a van padrão fica em routes.vehicle_id
  └ route_stops           os pontos, em ordem (orderIndex 1, 2, 3...); pode haver mais de um no mesmo endereço
      ├ student_stops     quem embarca/desce ali: aluno + BOARDING/ALIGHTING + HOME/SCHOOL/OTHER + dias da semana
      └ school_stops      qual escola fica ali
```

**Um ponto = uma parada da van**, num endereço e numa posição. Num ponto cabem:
- **vários alunos** (vários `student_stops`), quando o motorista põe mais de um aluno naquele ponto. Ex.: todos que descem na escola;
- **mais de uma escola** (vários `school_stops`), quando as escolas ficam no **mesmo endereço** (mesmo prédio).

**O mesmo endereço pode ter mais de um ponto na rota**, porque a van pode voltar lá. Ex.: deixa a Ana na escola, busca o Paulo e volta na escola para deixar ele. Quem decide se o aluno vai num ponto que já existe ou num ponto novo é **o motorista**, a cada aluno que ele põe na rota (passo 6).

---

## 2. Endereço: a regra de "já existe"

Todo endereço novo (do pai, do aluno ou da escola) passa por `AddressService.findOrCreate`:

| Caso | O que acontece |
|---|---|
| já existe um endereço com **o mesmo CEP e o mesmo número** | **só liga nele**: não cria outro e não chama a API de mapas |
| CEP termina em `000` (pode ser o CEP único de uma cidade pequena) | também compara **a rua e a cidade**, pra não juntar casas de ruas diferentes |
| não existe | cria e busca a coordenada **uma vez** na API de mapas |

- O jeito de escrever a rua não importa: "Avenida Paulista" e "AV. PAULISTA", com o mesmo CEP e número, são o mesmo endereço.
- A **coordenada que vier do app é ignorada**: quem define é a busca (não dá pra mandar uma coordenada falsa).
- **Apartamentos** do mesmo prédio viram o mesmo endereço (não existe campo de complemento). Pra van isso é bom: ela para no mesmo lugar.

**Dica pro front:** preencha a rua, o bairro e a cidade **pelo CEP** (ViaCEP) e mostre os endereços que a família já tem pra escolher, antes de deixar digitar um novo.

---

## 3. Passo a passo, com os DTOs

### Passo 1: o pai se cadastra
`POST /api/responsible/auth/signup`
```json
{ "name": "...", "email": "...", "cpf": "...", "phone": "...", "password": "...", "confirmPassword": "...",
  "address": { "street": "Av. Paulista", "number": 1000, "zipCode": "01310-100", "city": "São Paulo",
               "neighborhood": "Bela Vista", "state": "SP" } }
```
- Cria o `user`, o `responsible` e o endereço de **cobrança**, ou liga num endereço que já existe (`responsibles.address_id`).
- Depois vem a confirmação do e-mail e o login.

### Passo 2: o pai cadastra o filho, com um ou vários endereços
`POST /api/student/create-student`. O DTO é o `StudentRequestDTO`:

| Campo | Pra quê |
|---|---|
| `name`, `birthDate`, `relationType`, `notes?` | dados do filho (`relationType`: `FINANCIAL`, `LEGAL` ou `WITHDRAWAL`) |
| **um endereço:** `address` (novo) **ou** `addressId` (que já existe), e `addressLabel?` | vira o "Casa" |
| **ou vários:** `addresses: [{label?, address \| addressId}]` | o primeiro sem apelido vira "Casa"; os outros, "Endereço 2", "Endereço 3"... |

```json
{ "name": "Ana", "birthDate": "2016-05-10", "relationType": "FINANCIAL",
  "addresses": [ { "addressId": "<endereço de cobrança do pai ou de um irmão>" },
                 { "label": "Natação", "address": { "street": "Rua Augusta", "number": 500, "zipCode": "01304-000",
                                                     "city": "São Paulo", "state": "SP" } } ] }
```
- O `addressId` só pode ser **um endereço do pai** (o de cobrança) **ou de um filho dele**. Senão, **403**.
- O endereço é **obrigatório** (pelo menos um): sem ele, **400**.
- O mesmo endereço duas vezes no cadastro dá **400**.
- Cria o `student`, a ligação `student_responsible` (o pai como admin) e uma `student_addresses` por endereço.

**Outro filho:** tem a **própria lista**, independente. Pode ter endereços totalmente diferentes (ex.: um fica na casa do pai e o outro na da mãe). Se algum endereço for igual, o pai pode escolher (`addressId`) em vez de digitar; e, se digitar, o backend reconhece pelo CEP + número e usa o mesmo registro de endereço. As ligações continuam separadas: tirar um endereço de um filho não mexe no outro.

**Depois do cadastro:**

| Ação | Rota |
|---|---|
| ver os endereços | `GET /api/student/{id}/addresses` → `[{ id, label, address }]` (o `id` é o `studentAddressId`) |
| pôr outro | `POST /api/student/{id}/addresses` `{label, address}` ou `{label, addressId}` (**409** se o filho já tem esse endereço) |
| tirar | `DELETE /api/student/{id}/addresses/{studentAddressId}`: apaga **só a ligação** (o endereço continua pra quem mais usa). O filho sai dos pontos de casa com esse endereço, e o motorista é avisado. **409** se for o último endereço, ou se tiver uma viagem rodando com ele |

### Passo 3: o motorista cadastra a escola
`POST /api/school/add-school` `{name, phone, email, addressRequestDTO: {...}}`
- Se a escola (nome + rua) já existe, só **vincula** o motorista a ela.
- O endereço segue a mesma regra de "já existe".

### Passo 4: o contrato fica ativo
É pelo chat (`docs/FLUXO_CONTRATO.md`). Sem contrato **ativo**, o motorista não vê os endereços do aluno nem consegue pôr o aluno na rota.

### Passo 5: o motorista cria a rota
`POST /api/route/create-route`, com o DTO `CreateRouteRequestDTO`: `{ "name": "Ida manhã" }`
→ **201** `{ id, name, driverName }`. **A ida e a volta são rotas diferentes.**

Van padrão (opcional): `PUT /api/route/{routeId}/set-vehicle/{vehicleId}`.

### Passo 6: o motorista põe o aluno na rota
Antes, `GET /api/route/stop-options` mostra o que ele pode escolher (DTO `RouteStopOptionsDTO`):
```json
{ "students": [ { "studentId": "...", "studentName": "Ana",
                  "addresses": [ { "id": "<studentAddressId>", "label": "Casa", "address": { ... } },
                                 { "id": "...", "label": "Natação", "address": { ... } } ] } ] }
```
Só aparecem os alunos com contrato **ativo** com ele. A escola não aparece: é sempre a do contrato.

`POST /api/route/{routeId}/add-student/{studentId}`, com o DTO `AddStudentToRouteRequest`. Cada chamada põe **uma** ação do aluno (embarque e desembarque são duas chamadas) e diz **em qual ponto**, obrigatoriamente de um destes dois jeitos:

| Campo | Ponto |
|---|---|
| `"newStop": true` | **cria um ponto novo**, mesmo que já exista outro no mesmo endereço |
| `"routeStopId": "..."` | usa **um ponto que já existe** nessa rota. O front acha pelo `GET /api/route/{routeId}/stops`, filtrando pelo endereço |

Sem nenhum dos dois, ou com os dois: **400**.

| `locationType` | Onde |
|---|---|
| `HOME` / `OTHER` | um endereço do aluno. No ponto novo, manda o `studentAddressId` (de `GET /api/route/stop-options`). No ponto que já existe, o ponto tem que ser num endereço do aluno |
| `SCHOOL` | a **escola do contrato** (não manda endereço). No ponto novo, ele cria o ponto no endereço da escola, já ligado a ela. No ponto que já existe, o ponto tem que ser no endereço da escola, e ele liga a escola se ainda não estiver ligada |

```json
{ "action": "BOARDING",  "locationType": "HOME",   "newStop": true, "studentAddressId": "<Casa da Ana>", "weekdays": ["MONDAY"] }
{ "action": "ALIGHTING", "locationType": "SCHOOL", "newStop": true }
{ "action": "ALIGHTING", "locationType": "SCHOOL", "routeStopId": "<o ponto da escola que já existe>" }
{ "action": "BOARDING",  "locationType": "OTHER",  "newStop": true, "studentAddressId": "<Padaria>", "orderIndex": 3 }
```

**Posição do ponto novo:**
- `orderIndex` (a partir de 1): o motorista escolhe;
- sem ele, é automática:
  - **embarque:** entra **antes** de onde o aluno desce (antes dos desembarques que ele já tem e, se não é na escola, antes do primeiro ponto da escola do contrato);
  - **desembarque:** entra no fim.

Os pontos de baixo descem uma posição. O backend **não sabe se a rota é ida ou volta**: olha só a ação do aluno. Num ponto que já existe, nada se mexe (mandar `orderIndex` junto dá 400; para mudar a posição, é o `swap-order`).

**A regra rígida é a ordem:** em cada dia, o embarque do aluno tem que vir **antes** do desembarque. Vale para o ponto novo, para o ponto que já existe, para a posição escolhida e para todo ajuste depois (400 se quebrar).

Se qualquer coisa falhar, **nada fica gravado**. A resposta é `RouteEditResponse` = `{ stops, pending }`: a rota inteira já alterada e o que falta configurar. **Toda alteração da rota** (passo 7 inclusive) devolve isso.

**Exemplo, ida com dois alunos:**
```
Ana embarca na Casa      newStop                → casa Ana(1)                      pendência: Ana sem desembarque
Ana desce na escola      newStop                → casa Ana(1), escola(2)
Bia embarca na Casa      newStop                → entra ANTES da escola: casa Ana(1), casa Bia(2), escola(3)
Bia desce na escola      routeStopId = escola   → o mesmo ponto 3
```
**Voltando na escola:** a van deixa a Ana, busca o Paulo na padaria e volta para deixar ele:
```
Ana embarca na Casa      newStop                       → casa(1)
Ana desce na escola      newStop                       → casa(1), escola(2)
Paulo embarca na Padaria newStop, orderIndex 3         → casa(1), escola(2), padaria(3)
Paulo desce na escola    routeStopId = escola(2)       → 400: ele desceria antes de embarcar
Paulo desce na escola    newStop                       → casa(1), escola(2), padaria(3), escola(4)
```
**Volta:** "Ana desce em casa" (newStop) cria a casa (1); "Ana embarca na escola" (newStop) cria a escola **antes**: escola (1), casa (2).

### O student stop
Resposta dentro de cada ponto (`students` do `RouteStopResponse`): `{ id, studentId, studentName, stopId, locationType, action, weekdays }`.
- `action`: `BOARDING` (embarca) ou `ALIGHTING` (desce).
- `locationType`: `HOME` (casa), `SCHOOL` (escola) ou `OTHER` (outro lugar do aluno, ex.: natação).
- `weekdays`: os dias em que o aluno usa **esse ponto**, em qualquer `locationType` (inclusive `SCHOOL`). Sem dias = todos os dias.

**Exemplo:** a Ana embarca na "Casa" de segunda a quinta e na "Natação" na sexta, e desce na escola todo dia:
```
ponto 1 (Casa da Ana)      → student_stop: Ana, BOARDING,  HOME,   seg-qui
ponto 2 (Natação da Ana)   → student_stop: Ana, BOARDING,  OTHER,  sex
ponto 3 (Escola X)         → student_stop: Ana, ALIGHTING, SCHOOL, todos   (+ school_stop: Escola X)
```

**Verificações ao pôr o aluno na rota (e ao mover com `move`):**

| Regra | Erro |
|---|---|
| precisa de contrato **`ACTIVE`** entre o motorista e o aluno (agendado, que ainda não começou, não serve) | 403 |
| o motorista precisa **estar na rota** hoje (quem saiu da rota perde o acesso a ela e às viagens dela) | 403 |
| `HOME`/`OTHER`: o ponto tem que ser **um endereço cadastrado do aluno** | 400 |
| `HOME`/`OTHER` não pode ficar no ponto de uma escola; `SCHOOL` só no ponto da escola do contrato | 400 |
| a mesma ação (ex.: dois embarques) no mesmo dia, na mesma rota | 409 |
| o embarque tem que vir **antes** do desembarque na ordem da rota | 400 |
| o aluno já está nesse ponto | 409 |

### Passo 7: ajustes e conferência

| Ação | Rota |
|---|---|
| trocar dois pontos de lugar | `PUT /api/route/stop/{id}/swap-order` `{orderIndex}` (400 se inverter o embarque e o desembarque de alguém) |
| mudar o aluno de ponto | `PUT /api/route/student-stop/{id}/move` `{routeStopId}` (mesmas verificações do passo 6) |
| mudar os dias | `PUT /api/route/student-stop/{id}/weekdays` `{weekdays}` |
| tirar o aluno do ponto | `DELETE /api/route/student-stop/{id}` |
| tirar a escola do ponto | `DELETE /api/route/school-stop/{id}` (409 se algum aluno dela ainda embarca ou desce ali) |
| apagar o ponto | `DELETE /api/route/stop/{id}` (409 se ainda tem aluno ou escola; os de baixo sobem) |
| ver tudo | `GET /api/route/{routeId}/stops` |
| **conferir se está pronta** | `GET /api/route/{routeId}/pending`: lista os dias em que falta embarque ou desembarque de algum aluno. **Vazia = pronta pra iniciar a viagem** |

---

## 4. O que pode mudar depois, e o que acontece
- **O pai tira um endereço que está na rota:** o aluno sai daquele ponto, a rota fica com pendência e o motorista recebe a notificação "Endereço removido" pra escolher o novo ponto.
- **O pai muda de casa:** põe o endereço novo e tira o velho. O motorista é avisado e troca o ponto.
- **O contrato deixa de estar ativo:** o aluno some das opções do motorista e não pode ser posto em pontos novos.
