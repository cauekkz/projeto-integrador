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
  └ route_stops           os pontos, em ordem (orderIndex 1, 2, 3...); 1 ponto por endereço na rota
      ├ student_stops     quem embarca/desce ali: aluno + BOARDING/ALIGHTING + HOME/SCHOOL/OTHER + dias da semana
      └ school_stops      qual escola fica ali
```

**Um ponto = um lugar onde a van para.** Num ponto cabem:
- **vários alunos** (vários `student_stops`). Ex.: dois irmãos na mesma casa, ou todos os alunos que descem na escola;
- **mais de uma escola** (vários `school_stops`), quando as escolas ficam no **mesmo endereço** (mesmo prédio).

Se a escola fica **só perto**, com outro endereço, ela tem **o ponto dela**.

"1 ponto por endereço na rota" quer dizer só isto: **não pode haver dois pontos no mesmo endereço na mesma rota**.
Pra pôr mais um aluno ou escola naquele lugar, você liga ao ponto que já existe.

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

**Irmão:** é outro filho, com a lista dele. Se mora na mesma casa, manda o `addressId` da casa do irmão. Mesmo que digite de novo, o backend reconhece pelo CEP + número e liga no mesmo endereço.

**Depois do cadastro:**

| Ação | Rota |
|---|---|
| ver os endereços | `GET /api/student/{id}/addresses` → `[{ id, label, address }]` (o `id` é o `studentAddressId`) |
| pôr outro | `POST /api/student/{id}/addresses` `{label, address}` ou `{label, addressId}` (**409** se o filho já tem esse endereço) |
| tirar | `DELETE /api/student/{id}/addresses/{studentAddressId}`: apaga **só a ligação** (o endereço continua pro irmão). O filho sai dos pontos de casa com esse endereço, e o motorista é avisado. **409** se for o último endereço, ou se tiver uma viagem rodando com ele |

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

`POST /api/route/{routeId}/add-student/{studentId}`, com o DTO `AddStudentToRouteRequest`:
```json
{ "action": "BOARDING",  "locationType": "HOME",   "studentAddressId": "<Casa da Ana>", "weekdays": ["MONDAY"] }
{ "action": "ALIGHTING", "locationType": "SCHOOL" }
{ "action": "BOARDING",  "locationType": "HOME",   "studentAddressId": "<Casa da Bia>", "orderIndex": 1 }
```
| `locationType` | Onde |
|---|---|
| `HOME` / `OTHER` | o endereço do aluno do `studentAddressId` (de `GET /api/route/stop-options`) |
| `SCHOOL` | o ponto da **escola do contrato** (não manda endereço) |

O que o backend faz, em uma transação só:
1. **ponto:** `HOME`/`OTHER` = o ponto daquele endereço do aluno; `SCHOOL` = o ponto da escola do contrato (route stop + **school stop**). Se a rota já tem, usa o mesmo (irmão da mesma casa, ou aluno da mesma escola = mesmo ponto); senão, cria. **A escola só entra na rota por um `SCHOOL`**: `HOME`/`OTHER` nunca cria escola;
2. **ordem:** um **embarque** novo entra **antes** de onde o aluno desce (antes dos desembarques que ele já tem e, se não é na escola, antes da escola do contrato, se ela já está na rota); os de baixo descem uma posição. Um **desembarque** novo entra no fim. Ele **não sabe se a rota é ida ou volta**: olha só a ação do aluno;
3. **aluno:** cria **um** student stop, só com a ação pedida (embarque e desembarque são duas chamadas), com as verificações abaixo. Se qualquer coisa falhar, nada fica gravado.

A posição é automática, mas o motorista pode escolher mandando `orderIndex` (a partir de 1). Só vale para ponto **criado agora**: ponto que já existe não se mexe. A única regra rígida de posição é a **ordem embarque → desembarque** (400 se a posição escolhida quebrar). Depois ele reordena quando quiser (passo 7).

**Limitações (por enquanto):** um ponto por endereço na rota (não dá para passar duas vezes no mesmo lugar) e uma escola por rota (não dá para parar na mesma escola duas vezes). O jeito hoje é dividir em duas rotas.

Resposta: `RouteEditResponse` = `{ stops, pending }`, a rota inteira já alterada e o que falta configurar. **Toda alteração da rota** (passo 7 inclusive) devolve isso.

**Exemplo, ida com dois alunos** (o motorista só adiciona os alunos, em qualquer ordem):
```
Ana embarca na Casa         → casa Ana(1)                          pendência: Ana sem desembarque
Ana desce na escola         → a escola entra no fim: casa Ana(1), escola(2)
Bia embarca na Casa         → entra ANTES da escola: casa Ana(1), casa Bia(2), escola(3)
Bia desce na escola         → usa o ponto 3 (a mesma escola, sem school stop novo)
```
**Volta:** "Ana desce em casa" cria a casa (1); "Ana embarca na escola" cria a escola **antes**: escola (1), casa (2).

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
| precisa de contrato **ativo** entre o motorista e o aluno | 403 |
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
