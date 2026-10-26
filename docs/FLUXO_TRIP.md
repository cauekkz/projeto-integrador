# Execução da rota e notificações: guia completo

Seções:
1. As peças
2. O dia inteiro, chamada por chamada
3. Perguntas frequentes
4. Rotas HTTP (entrada, saída e erros)
5. WebSocket da trip
6. Notificações (rotas, WebSocket e quem recebe o quê)
7. Quando a API de rotas é chamada
8. Telas do front
9. Página de teste (sem front)
10. O que ainda não existe

---

## 1. As peças

| Nome | O que é | Onde fica |
|---|---|---|
| **rota** | a configuração fixa: pontos em ordem, alunos e dias da semana | Postgres (`routes`, `route_stops`, `student_stops`, `school_stops`) |
| **trip** | a rota rodando **num dia**. Nasce quando o motorista aperta "Iniciar" | Postgres (`trips`) |
| **boarding** | a situação de **um aluno numa trip**: `PENDING` (aguardando), `BOARDED` (embarcou), `DELIVERED` (entregue), `ABSENT` (ausente). É um só por aluno, com a hora que embarcou e a hora que desceu | Postgres (`boardings`) |
| **falta avisada** | "meu filho não vai na rota X no dia Y". Vale só aquele dia | Postgres (`student_absences`) |
| **notificação** | aviso pra um usuário ("a van saiu", "Ana embarcou", "proposta de contrato"). Fica gravada, com lida ou não lida | Postgres (`notifications`) |
| **estado ao vivo** | posição da van, linha do mapa e quanto falta pra cada ponto. Muda a cada poucos segundos, então **não vai pro banco** | Redis, chave `trip:{tripId}:live` (some em 16 h) |
| **canais do Redis** | o "megafone": `trip-{tripId}` (a trip mudou) e `notification-{userId}` (notificação nova pra esse usuário) | Redis pub/sub |
| **WebSocket `/trip/{tripId}`** | conexão aberta durante a viagem. O motorista **manda** o GPS e todos **recebem** a trip | backend |
| **WebSocket `/notifications`** | conexão aberta enquanto o app está aberto. Cada um recebe **as suas** notificações | backend |

**Como uma mudança chega no celular** (vale pra GPS, embarque, falta e notificação):
```
algo muda (PUT, GPS, start...)
  -> o backend grava (Postgres e/ou Redis)
  -> publica no canal do Redis (trip-{id} ou notification-{userId})
  -> todo servidor do backend inscrito nesse canal recebe
  -> cada servidor manda pelos WebSockets conectados nele
```
Gravar no Redis não avisa ninguém sozinho: o código **publica** logo depois. Esse é o `publish` que aparece no código.

**Quem é quem no WebSocket:** o `?token=` é o JWT do login, e o backend tira dele o id do usuário. Não tem id de usuário na URL.

---

## 2. O dia inteiro, chamada por chamada

### 2.0 Antes (uma vez só)

| # | Quem | Chamada | Pra quê |
|---|---|---|---|
| 1 | motorista | `GET /api/vehicle/my-vehicles` → `PUT /api/route/{routeId}/set-vehicle/{vehicleId}` | define a van padrão da rota (opcional) |
| 2 | pai e motorista | ao abrir o app: abre `ws /notifications?token=JWT` | recebe as notificações na hora |

### 2.1 Manhã, antes da van sair

| # | Quem | Chamada | Resposta | Efeito |
|---|---|---|---|---|
| 3 | pai | `GET /api/student/{id}/today-trips` | `[]` | ainda não tem trip: mostra "A van ainda não saiu" |
| 4 | pai (opcional) | `POST /api/student/{id}/absences` `{routeId, date}` | 201 | grava a falta. O motorista recebe a mensagem `ABSENCE` no chat e a notificação `ABSENCE_REPORTED` |
| 5 | motorista | `GET /api/trip/route/{routeId}/today` | 404 | não tem trip hoje: mostra "Iniciar" |

### 2.2 O motorista inicia

| # | Quem | Chamada | Resposta | Efeito |
|---|---|---|---|---|
| 6 | motorista | `POST /api/trip/start/{routeId}` `{latitude, longitude, vehicleId?}` (GPS do celular) | 201 com a trip (`id` = **tripId**) | cria a trip e um boarding por aluno do dia (quem tem falta avisada entra `ABSENT`). Chama a API de rotas (linha e tempos), grava o estado no Redis e publica em `trip-{id}` |
| 6a | → pais | (automático) notificação `TRIP_STARTED` "A van saiu" | | pra cada pai com filho que vai hoje |
| 6b | → pais do 1º ponto | (automático) notificação `VAN_ON_THE_WAY` "A van está indo buscar Ana" | | só pra quem tem filho no próximo ponto |
| 7 | motorista | abre `ws /trip/{tripId}?token=JWT` | recebe `trip-update` (versão do motorista) | |
| 8 | motorista | a cada 2–5 s, pelo WebSocket: `{"type":"position","latitude":..,"longitude":..}` | todos recebem `trip-update` | grava a posição. Chama a API de rotas de novo se o último cálculo tem mais de **5 min**, ou na hora se a van **saiu da linha** (mais de 150 m) |

### 2.3 O pai acompanha

| # | Quem | Chamada | Resposta |
|---|---|---|---|
| 9 | pai | chega a notificação `TRIP_STARTED` (com `payload.tripId`), ou ele abre o app e chama `GET /api/student/{id}/today-trips` | `[{tripId, routeName, tripStatus, boardingStatus}]` |
| 10 | pai | abre `ws /trip/{tripId}?token=JWT` | `trip-update` (versão do pai). O campo `mode` diz o que desenhar (seção 5): `FOLLOWING` (van + linha até o ponto do filho + tempo), `ON_BOARD` (filho na van, mas ela ainda busca outros: só a van) ou `STATUS` (só o status) |

### 2.4 Durante a viagem

| # | Quem | Chamada | Efeito (WebSocket da trip) | Notificação |
|---|---|---|---|---|
| 11 | motorista | `PUT /api/trip/{tripId}/student/{id}/board` | aluno `BOARDED` e trip `IN_PROGRESS`. Todos recebem | `STUDENT_BOARDED` "Ana embarcou" (pros pais da Ana) |
| 11a | | (automático, se o próximo ponto mudou) | | `VAN_ON_THE_WAY` pros pais do novo próximo ponto |
| 12 | motorista | `PUT .../student/{id}/absent` (o aluno não apareceu) | aluno `ABSENT` e trajeto recalculado | `STUDENT_ABSENT` "Bia não embarcou", e `VAN_ON_THE_WAY` se o próximo ponto mudou |
| 13 | pai | `POST /api/student/{id}/absences` com a data de hoje (**só enquanto o filho ainda não embarcou**) | o filho vira `ABSENT` na hora e o trajeto é recalculado | `ABSENCE_REPORTED` pro motorista (e mensagem no chat) |
| 14 | pai | `DELETE /api/student/{id}/absences/{absenceId}` ("vai sim") | **só antes de a viagem do dia começar**; depois disso, **409** | `ABSENCE_REPORTED` "Falta desfeita" pro motorista |
| 15a | — | o filho foi entregue, mas a rota segue pros outros | o pai recebe `mode: DONE` (acompanhamento encerrado; a hora da entrega vem em `students[].deliveredAt`) | `STUDENT_DELIVERED` |
| 15 | motorista | `PUT .../student/{id}/alight` (na escola) | aluno `DELIVERED` | `STUDENT_DELIVERED` "Ana chegou na escola" (ou "em casa") |

### 2.5 Fim

| # | Quem | Chamada | Efeito |
|---|---|---|---|
| 16 | motorista | `POST /api/trip/{tripId}/finish` | trip `FINISHED` e hora de fim. Todos recebem, e o front fecha o WebSocket da trip |

### 2.6 Se o app fechar no meio

| Caso | O que fazer |
|---|---|
| motorista reabre | `GET /api/trip/route/{routeId}/today` → 200 com status ativo → **"Continuar"** = abrir `ws /trip/{id}` de novo e voltar a mandar o GPS. Nada é recriado |
| motorista quer zerar | `POST /api/trip/start/{routeId}?restart=true` → a trip antiga vira `CANCELED`, nasce outra com **id novo**, e todos os alunos voltam a `PENDING`. Os pais recebem `TRIP_STARTED` "A rota foi reiniciada" (com o tripId novo) |
| pai com o WebSocket da trip aberto recebe `status: CANCELED` | chama `today-trips` de novo e abre a trip nova |
| WebSocket caiu (rede) | reconecta no mesmo endereço; ao conectar, já recebe o estado atual |

---

## 3. Perguntas frequentes

**Por que o `today-trips` devolve uma lista?** Porque o mesmo filho pode estar em mais de uma rota no mesmo dia: ida de manhã e volta à tarde. Cada uma é uma trip. Irmãos não entram juntos: cada filho tem a própria chamada.

**Onde fica a van padrão?** Em `routes.vehicle_id`, definida por `PUT /api/route/{routeId}/set-vehicle/{vehicleId}`. No `start`, o `vehicleId` é opcional: sem ele, usa a padrão. Sem nenhuma das duas, dá 400.

**De onde vêm a latitude e a longitude?** Do GPS do celular: `navigator.geolocation.getCurrentPosition` pro start e `watchPosition` pra ir mandando. No Expo é o `expo-location`.

**Como o backend sabe que o pai só vê os filhos dele?** Quando o pai conecta, o backend vê **quais alunos da trip são filhos desse usuário** (pela tabela de responsáveis) e guarda isso na conexão. A cada atualização, monta a versão desse pai só com esses filhos. Quem não tem filho na trip é desconectado (código 1003). Só o motorista da trip consegue mandar posição.

**Como ele sabe que o próximo ponto é do filho?** Os pontos de hoje estão em ordem. Um ponto fica `done` quando ninguém mais precisa parar ali. O **próximo** é o primeiro que não está `done`. Se lá tem um filho desse pai que ainda precisa parar ali, o pai fica com `mode: FOLLOWING`.
Se o filho já está **dentro da van** (`BOARDED`), mas o próximo ponto é de outro aluno, ele fica com `mode: ON_BOARD` e vê **só onde a van está**, sem linha, sem pontos e sem tempo. Quando a van seguir pro destino do filho (escola ou casa), volta a ser `FOLLOWING`.

**Como sabe que faltam X minutos? Tem trânsito?** A API de rotas (OpenRouteService) devolve a linha pelas ruas e o tempo de cada trecho, e o tempo até um ponto é a soma dos trechos. **Ela não usa trânsito ao vivo:** usa a velocidade típica de cada rua. Por isso o backend recalcula com a posição real da van: a cada 5 minutos, e na hora se ela sair do caminho (desvio). Entre um cálculo e outro, o tempo vai descontando. Se ela está parada no trânsito, o próximo cálculo corrige o tempo. Trânsito ao vivo de verdade só com API paga (Google).

**Quem chama a API de rotas?** Só o backend (a chave fica escondida). Embarque e desembarque não chamam. O pai recebe o tempo até o ponto do filho no `etaSeconds`.

**Os "minutos que faltam" são notificação?** Não. Eles chegam pelo WebSocket da trip e mudam o tempo todo. As notificações são os **eventos**: saiu, indo buscar, embarcou, chegou, ausente, contrato.

**O pai não está no app. A notificação vai pro nada?** Não se perde: fica gravada, e quando ele abre o app, `GET /api/notifications` mostra tudo. O que não existe ainda é **acordar o celular** com o app fechado (seção 10).

**Cada aluno vem com horários?** Sim: `students[].boardedAt` (embarcou) e `deliveredAt` (foi entregue). Valem `null` enquanto não aconteceram.

**O token expira no meio da viagem?** O JWT dura 15 minutos, mas o app **renova sem pedir a senha** (seção 4.10): quando uma chamada dá **401**, ele troca o `refreshToken` por um par novo e repete a chamada.
Só a abertura do WebSocket confere o token, então uma conexão já aberta continua; pra reconectar, use o JWT novo.

**Guardar o refreshToken no Redis por 30 dias é certo?** É o jeito comum de fazer:
- no Redis fica só o **hash** do token, que não serve pra entrar;
- a chave **some sozinha** quando vence;
- cada uso gera outro token e **o antigo deixa de valer**;
- se um token **já usado** aparece de novo, é sinal de cópia: o backend derruba **todos** os tokens daquele usuário. O ladrão perde o acesso, e o dono entra de novo com a senha;
- o logout apaga o token.

O Redis do projeto grava em disco (volume `redis_data`). Se ele for zerado, o pior que acontece é todo mundo ter que fazer login de novo.

**O motorista embarca o aluno de qualquer lugar?** Não: a van (a última posição do GPS) tem que estar a até **1 km** do ponto onde o aluno embarca ou desce (`TRIP_MAX_STOP_DISTANCE_METERS`, 0 desliga). Longe disso, dá **409** com a distância ("A van está a 5.6 km do ponto do aluno: chegue a menos de 1.0 km").

**Quando aparece o "Finalizar rota"?** O backend aceita finalizar a qualquer momento. Quando **todos os alunos foram atendidos** (`targetStopId` vem `null` com a trip ativa), o app mostra o aviso "Todos os alunos foram atendidos" com o botão em destaque.

**Se a API de rotas cair ou demorar?** A chamada desiste em 15 s, e a trip continua normal, só sem a linha e sem o tempo. Ela tenta de novo no próximo GPS.

---

## 4. Rotas HTTP

Todas com `Authorization: Bearer <JWT>`. Erros vêm como `{"timestamp", "message", "details"}`.

### 4.1 `POST /api/trip/start/{routeId}` (motorista) · `?restart=true` opcional
Entrada:
```json
{ "latitude": -23.5614, "longitude": -46.6559, "vehicleId": "uuid-opcional" }
```
Saída **201** (o mesmo formato do `GET /api/trip/{tripId}` e do `today`):
```json
{
  "id": "3e72e65c-...",
  "routeId": "...", "routeName": "Ida Manha",
  "date": "2026-10-25", "status": "STARTED", "startTime": "07:02:11", "endTime": null,
  "vehicleId": "...", "vehiclePlate": "ABC1D23",
  "stops": [
    {
      "routeStopId": "...", "orderIndex": 1, "done": false,
      "address": { "id": "...", "street": "Rua Augusta", "number": 500, "neighborhood": "Consolacao",
                   "city": "Sao Paulo", "state": "SP", "zipCode": "01304-000", "latitude": -23.55, "longitude": -46.65 },
      "schoolIds": [],
      "students": [ { "studentId": "...", "studentName": "Ana", "action": "BOARDING", "locationType": "HOME", "status": "PENDING" } ]
    }
  ],
  "path": { "distance": 4210.5, "duration": 890.2, "coordinates": [[-46.6559, -23.5614], [-46.6551, -23.5609]] }
}
```
- `id` é o **tripId**, usado pra abrir o WebSocket.
- `stops` traz só os pontos com aluno hoje, em ordem.
- `schoolIds` só vem preenchido no ponto da escola.
- `path`: `distance` em metros e `duration` em segundos até o fim. `coordinates` vem como `[longitude, latitude]`. O `path` inteiro vem `null` se a API falhou.

Erros:
- **403**: a rota não é dele;
- **400**: tem aluno com pendência na rota, sem van e sem van padrão, a van não é dele, nenhum aluno hoje, ou latitude/longitude faltando;
- **409**: já existe trip hoje (use `restart=true`), ou a trip de hoje já foi finalizada.

### 4.2 `GET /api/trip/route/{routeId}/today` (motorista)
**200** com a trip (o JSON acima), ou **404** se não existe. Trip cancelada não conta.

### 4.3 `GET /api/trip/{tripId}` (motorista dono)
O mesmo JSON. **403**: é de outro motorista. **404**: não existe.

### 4.4 `PUT /api/trip/{tripId}/student/{studentId}/board` e `/alight` (motorista)
Sem corpo. Saída:
```json
{ "studentId": "...", "studentName": "Ana", "action": null, "locationType": null, "status": "BOARDED" }
```
- `board`: só se estiver `PENDING`, senão **409**. O primeiro embarque muda a trip pra `IN_PROGRESS`.
- `alight`: só se estiver `BOARDED`, senão **409**.
- **404**: o aluno não está na trip. **409**: a trip não está em andamento.
- **409** também quando a van está **longe do ponto** do aluno (mais de 1 km, `TRIP_MAX_STOP_DISTANCE_METERS`) ou quando ainda não chegou nenhuma posição do GPS. A mensagem diz a distância.

### 4.5 `PUT /api/trip/{tripId}/student/{studentId}/absent` (motorista)
Sem corpo. Só se o aluno está `PENDING`. Devolve a trip inteira (o JSON do 4.1), com o trajeto recalculado.

### 4.6 `POST /api/trip/{tripId}/finish` (motorista)
Sem corpo. Devolve a trip com `"status": "FINISHED"` e `endTime`. Depois disso, qualquer ação na trip dá **409**.

### 4.7 `PUT /api/route/{routeId}/set-vehicle/{vehicleId}` (motorista)
Define a van padrão da rota.

### 4.8 `GET /api/student/{studentId}/today-trips` (pai)
```json
[ { "tripId": "3e72e65c-...", "routeId": "...", "routeName": "Ida Manha", "tripStatus": "STARTED", "boardingStatus": "PENDING" } ]
```
Lista vazia: o motorista ainda não iniciou. **403**: não é responsável desse aluno.

### 4.9 Faltas (pai)

| Rota | Entrada | Saída |
|---|---|---|
| `POST /api/student/{studentId}/absences` | `{"routeId": "...", "date": "2026-10-25"}` | **201** `{"id", "studentId", "routeId", "routeName", "date"}` |
| `GET /api/student/{studentId}/absences` | nada | lista no mesmo formato, de hoje em diante |
| `DELETE /api/student/{studentId}/absences/{absenceId}` | nada | **204**, ou **409** se a viagem do dia já começou |

Regras:
- **avisar falta:** pode antes da viagem e durante ela, **enquanto o filho não embarcou**;
- **desfazer:** só **antes de a viagem do dia começar**.

O `POST` dá erro nestes casos:
- **400**: a data já passou, ou o aluno não está nessa rota;
- **409**: falta já avisada; o aluno já embarcou, já foi entregue ou já está ausente; ou a rota do dia já terminou;
- **403**: não é responsável do aluno.

### 4.10 Login e renovação do token (todos)

| Rota | Entrada | Saída |
|---|---|---|
| `POST /api/auth/login` | `{"cpf", "passwordHash"}` | `{"token", "expiresIn": 900000, "refreshToken", "refreshExpiresIn": 2592000000}` |
| `POST /api/auth/refresh` | `{"refreshToken"}` | um **par novo** (mesmo formato). O `refreshToken` usado deixa de valer. **401** se venceu, já foi usado ou teve logout |
| `POST /api/auth/logout` | `{"refreshToken"}` | **204** (apaga o refreshToken) |

O que o front faz:
1. Guarda o `token` e o `refreshToken` no celular (no Expo, `expo-secure-store`).
2. Manda o `token` em toda chamada (`Authorization: Bearer`) e no `?token=` dos WebSockets.
3. Chamada deu **401**: chama o `/refresh` uma vez, guarda o par novo e repete a chamada. Se o WebSocket cair, reconecta com o token novo.
4. O `/refresh` também deu 401 (30 dias sem abrir o app, ou logout): vai pra tela de login.
5. Sair do app: `POST /api/auth/logout` e apaga os dois tokens.

---

## 5. WebSocket da trip: `ws://HOST/trip/{tripId}?token=JWT`

**O motorista manda** (só ele):
```json
{ "type": "position", "latitude": -23.559, "longitude": -46.661 }
```
Se outra pessoa mandar, recebe `{"type":"error","message":"Só o motorista envia a posição"}`.

**Todos recebem** `trip-update` ao conectar e a cada mudança. O campo `mode` diz o que desenhar:

| `mode` | Quem | Quando | Vem preenchido |
|---|---|---|---|
| `DRIVER` | motorista | sempre | tudo |
| `FOLLOWING` | pai | a van está indo pro ponto do filho (buscar em casa, ou levar pra escola/casa) | van, `path` até o ponto, `targetStopId`, `etaSeconds` |
| `ON_BOARD` | pai | o filho está na van, mas ela ainda vai buscar outros | **só** `latitude`, `longitude` e `positionAt` (a van na rua) |
| `DONE` | pai | todos os filhos dele já foram entregues (ou faltaram) e a rota segue pros outros | só `status` e `students` (com `deliveredAt`) |
| `STATUS` | pai | o resto: ainda não é a vez dele, ou trip finalizada/cancelada | só `status` e `students` |

**Versão do motorista:**
```json
{
  "type": "trip-update", "tripId": "...", "routeName": "Ida Manha", "status": "IN_PROGRESS",
  "following": true, "mode": "DRIVER",
  "latitude": -23.559, "longitude": -46.661, "positionAt": 1792950000000,
  "targetStopId": "...", "etaSeconds": 412.0,
  "path": [[-46.661, -23.559], [-46.660, -23.558]],
  "students": [ { "studentId": "...", "studentName": "Ana", "action": "BOARDING", "locationType": "HOME", "status": "BOARDED" } ],
  "stops": [ { "routeStopId": "...", "orderIndex": 1, "latitude": -23.55, "longitude": -46.65,
               "students": [], "schoolIds": [], "done": true, "pathIndex": null, "etaSeconds": null } ]
}
```
- `positionAt`: quando a posição chegou (ms desde 1970).
- `targetStopId` e `etaSeconds`: o próximo ponto e os segundos até ele.
- `path`: a linha até o fim. No mapa, inverta pra `[latitude, longitude]` (Leaflet).
- `stops`: todos os pontos, com o `etaSeconds` de cada um.

**Versão do pai, quando o próximo ponto é do filho:**
```json
{ "type": "trip-update", "tripId": "...", "routeName": "Ida Manha", "status": "IN_PROGRESS",
  "following": true, "mode": "FOLLOWING", "latitude": -23.559, "longitude": -46.661, "positionAt": 1792950000000,
  "targetStopId": "<ponto do filho>", "etaSeconds": 412.0,
  "path": [[-46.661, -23.559], [-46.660, -23.558]],
  "students": [ { "studentId": "<filho>", "studentName": "Ana", "action": "BOARDING", "status": "PENDING" } ],
  "stops": null }
```
- `path` vai só até o ponto do filho.
- `students` traz só os filhos dele.

**Versão do pai, com o filho na van mas a van buscando outros (`ON_BOARD`):** `following: false`. Vêm `latitude`, `longitude` e `positionAt`;
`targetStopId`, `etaSeconds`, `path` e `stops` vêm `null`. O app mostra só o ícone da van no mapa.

**Versão do pai no resto (`STATUS`):** `following: false`. Os campos `latitude`, `longitude`, `positionAt`, `targetStopId`,
`etaSeconds`, `path` e `stops` vêm `null`. Só vêm o `status` e os `students` (os filhos dele).

---

## 6. Notificações

### 6.1 Quem recebe o quê

| Tipo | Quando | Quem recebe | Título (exemplo) | `payload` |
|---|---|---|---|---|
| `CONTRACT_PROPOSED` | o motorista manda a proposta | o responsável | "Nova proposta de contrato" | `chatId`, `contractId` |
| `CONTRACT_ACCEPTED` | o pai aceita | o motorista | "Contrato aceito" | `chatId`, `contractId` |
| `CONTRACT_REJECTED` | o pai recusa | o motorista | "Contrato recusado" | `chatId`, `contractId` |
| `TRIP_STARTED` | o motorista inicia (ou reinicia) | pais com filho que vai hoje (falta avisada não recebe) | "A van saiu" / "A rota foi reiniciada" | `tripId`, `routeId`, `studentIds` |
| `VAN_ON_THE_WAY` | o próximo ponto da van passa a ser do filho | os pais dos filhos desse ponto | "A van está indo buscar Ana" / "A van está indo buscar Ana na escola" (volta) / "Ana está a caminho da escola" / ".. a caminho de casa" | `tripId`, `routeStopId`, `studentIds`, `etaSeconds` |
| `STUDENT_BOARDED` | o motorista marca o embarque | os pais do aluno | "Ana embarcou" | `tripId`, `studentIds` |
| `STUDENT_DELIVERED` | o motorista marca o desembarque | os pais do aluno | "Ana chegou na escola" / "chegou em casa" | `tripId`, `studentIds` |
| `STUDENT_ABSENT` | o motorista marca como ausente | os pais do aluno | "Ana não embarcou" | `tripId`, `studentIds` |
| `ABSENCE_REPORTED` | o pai avisa ou desfaz uma falta | os motoristas da rota | "Falta avisada" / "Falta desfeita" | `chatId`, `studentId`, `routeId`, `date`, `canceled` |

- "Pais" são **todos os responsáveis** do aluno, não só o principal.
- **Irmãos viram uma notificação só:** "A van está indo buscar Ana e Caio".
- O `payload` serve pro app abrir a tela certa: com `tripId`, abre o acompanhamento; com `contractId`, o contrato.

### 6.2 Rotas

| Rota | Saída |
|---|---|
| `GET /api/notifications` (`?unread=true` só não lidas, `?page=0&size=20`) | página: `{"content": [...], "totalElements", "totalPages", ...}`, mais novas primeiro |
| `GET /api/notifications/unread-count` | `{"count": 3}` |
| `PUT /api/notifications/{id}/read` | **204**. **404** se a notificação não existe ou é de outro usuário |
| `PUT /api/notifications/read-all` | **204** |

Cada item:
```json
{ "id": "...", "type": "STUDENT_BOARDED", "title": "Ana embarcou",
  "message": "Embarcou na van às 07:12 (rota Ida Manha)",
  "payload": { "tripId": "...", "studentIds": ["..."] },
  "createdAt": "2026-10-25T07:12:03.481", "read": false }
```

### 6.3 WebSocket: `ws://HOST/notifications?token=JWT`
Abra ao entrar no app e deixe aberto. Ao conectar, chega o contador de não lidas:
```json
{ "type": "unread-count", "count": 3 }
```
A cada notificação nova:
```json
{ "type": "notification", "notification": { "id": "...", "type": "TRIP_STARTED", "title": "A van saiu", "...": "mesmo formato do 6.2" } }
```
O app não manda nada por esse WebSocket.

---

## 7. Quando a API de rotas é chamada

| Momento | Chama? |
|---|---|
| Iniciar | sim |
| GPS pelo WebSocket, na linha | a cada **5 min** (`TRIP_ETA_REFRESH_SECONDS`, padrão 300) |
| GPS pelo WebSocket, **fora da linha** (desvio de mais de 150 m) | na hora, com no mínimo 30 s entre chamadas (`TRIP_OFF_ROUTE_METERS`, `TRIP_OFF_ROUTE_MIN_SECONDS`) |
| Embarque ou desembarque | não |
| Aluno ausente (motorista ou pai) | sim |
| Finalizar | não |

Numa viagem de 1 h, sem desvios, dá umas **12–15 chamadas**: o início, uma a cada 5 min e uma por aluno ausente.
**O limite da chave atual é 200 chamadas de rota por dia** (o cabeçalho `x-ratelimit-limit` da resposta mostra), umas **13 viagens de 1 h por dia** pro app todo. Pra mais que isso, precisa de um plano pago ou de outra chave.
A busca de endereços tem uma cota separada. A chamada desiste em 15 s.

---

## 8. Telas do front

**Ao abrir o app (todos):** abre o `ws /notifications` e mostra o contador. Tocar numa notificação abre a tela do `payload`.

**Motorista, tela da rota:**
1. `GET /api/trip/route/{routeId}/today`:
   - 404: botão "Iniciar".
   - Ativa: "Continuar" e "Recomeçar".
   - FINISHED: "Rota de hoje finalizada".
2. Iniciar: pega o GPS, chama o `start` e abre o `ws /trip/{id}`.
3. Com o WebSocket aberto:
   - o `watchPosition` manda a `position`;
   - a lista de pontos vem do `trip-update` (`stops`), com os botões embarcou/desceu/ausente (PUT);
   - embarcou/desceu só funcionam perto do ponto (1 km); o erro 409 traz a distância pra mostrar na tela;
   - pra navegar, dá pra abrir o Google Maps ou o Waze com os pontos em ordem.
4. Todos atendidos (`targetStopId` `null`): aviso "Todos os alunos foram atendidos" e botão "Finalizar rota" em destaque.
5. Finalizar: chama o `finish` e fecha o WebSocket da trip.

**Pai, tela do filho:**
1. `GET /api/student/{id}/today-trips`, ou o `tripId` que veio na notificação `TRIP_STARTED`.
   - Vazio: "A van ainda não saiu".
   - Com trip: abre o `ws /trip/{tripId}`.
2. Pelo `mode`:
   - `FOLLOWING`: mapa com a van, a linha até o destino (desenho de casa ou de escola) e "chega em X min";
   - `ON_BOARD`: só a van no mapa ("seu filho está na van");
   - `STATUS`: só o status do filho.
3. `status` CANCELED: chama `today-trips` de novo. FINISHED: fecha o WebSocket.
4. Botão "não vai hoje": `POST .../absences`.

---

## 9. Página de teste (sem front)

### Como rodar
```bash
bash backend/api-tests/demo/run-demo.sh
# abre http://localhost:8090 ; Ctrl+C no terminal desliga tudo
# se a 8090 estiver ocupada: DEMO_PORT=8091 bash backend/api-tests/demo/run-demo.sh
# se a cota diária da chave de mapas acabou: DEMO_MAPS=sim bash backend/api-tests/demo/run-demo.sh
```
Esse comando faz tudo sozinho:
1. Sobe o Postgres, o Redis e o MinIO (docker compose) e cria o banco **isolado** `vanroute_audit`. O seu `vanroute_db` não é tocado.
2. Recompila o backend se o código mudou.
3. Sobe um **e-mail falso** (`stubs.py`), pro cadastro dos pais funcionar sem mandar e-mail de verdade.
4. Sobe o backend na 9090:
   - com a sua chave do OpenRouteService no `application-local.properties`, usa **mapas e endereços reais**;
   - sem a chave, ou com `DEMO_MAPS=sim`, usa um simulador. Ele põe cada endereço num ponto diferente perto da Praça da Sé e desenha a linha reta entre os pontos;
   - a chave tem cota diária: **200 rotas** e um número limitado de **buscas de endereço**. Acabou a de endereço, o cadastro responde **503** "Busca de endereços indisponível agora" até o dia seguinte;
   - recalcula o tempo a cada 2 min (no app, 5 min), pra dar pra ver mudando sem gastar a cota da chave.
5. Cria o cenário (`seed.sh`), só com dados fictícios. São dois, um embaixo do outro na página:
   - **Ida (manhã):** 1 motorista com van, 1 escola e 2 pais, cada um com 1 filho e contrato ativo. Rota "Ida Manha": casa do filho 1 → casa do filho 2 → escola;
   - **Volta (tarde):** outro motorista, outra escola e outros 2 pais. Rota "Volta Tarde": escola → casa do filho 3 → casa do filho 4.
6. Abre a página (`serve.py`).

Se der erro `000` no seed, o backend não estava de pé. Rodando pelo `run-demo.sh`, isso não acontece.

### O que fazer na página
1. "Entrar" nos painéis (3 da ida e 3 da volta). Os CPFs já vêm preenchidos, e a senha é `Senha@123`.
2. Motorista: "Iniciar rota". Pais: "Procurar trip de hoje" e "Acompanhar".
   No mapa, a van é um desenho que vira na direção da rua e desliza entre as posições, e os pontos são desenhos de casa e de escola.
3. Teste "embarcou", "ausente" e "desceu"; nos pais, "não vai hoje"; e "Finalizar".
   Longe do ponto, o "embarcou" mostra o erro com a distância. No fim, aparece "Todos os alunos foram atendidos" com o botão de finalizar.
4. Pra mover a van:
   - **clique no mapa do motorista**;
   - ou ligue **"simular GPS"**;
   - ou use **"ir até o próximo ponto"**, que leva a van direto pro ponto.
5. O **log** embaixo mostra cada chamada HTTP e cada mensagem dos WebSockets. Clique numa linha pra ver o JSON enviado e o recebido.

### Como funciona sem um motorista andando de verdade
O backend não sabe se a posição veio de um celular andando ou de uma página: ele só recebe `{"type":"position","latitude":..,"longitude":..}` pelo WebSocket.
- No app de verdade, quem manda isso é o GPS do celular (`watchPosition`), a cada poucos segundos.
- Na página, você manda clicando no mapa, ou o **"simular GPS"** manda sozinho a cada 2 s um ponto mais à frente **na própria linha que o backend calculou**. A van "anda" pelas ruas certas.

Todo o resto é o fluxo real:
- o mesmo backend, as mesmas rotas e os mesmos WebSockets;
- um Postgres e um Redis de verdade;
- a API de rotas de verdade, quando tem a chave.

O que muda em relação ao app real: o banco é o de teste, os cadastros são fictícios (o motorista e a van entram direto no banco, porque o cadastro real pede CNH e CRLV assinados) e o e-mail é falso.

### Arquivos (`backend/api-tests/demo/`)

| Arquivo | O que faz |
|---|---|
| `run-demo.sh` | sobe tudo e desliga tudo no Ctrl+C |
| `seed.sh` | cria o cenário e grava os logins em `api-tests/out/demo.json` (fora do git) |
| `serve.py` | entrega a página e repassa `/api/*` pro backend (o backend não tem CORS, então a página não pode chamar a 9090 direto) |
| `index.html` | a página (HTML, CSS e JS puro, mapa Leaflet + OpenStreetMap). O WebSocket vai direto na 9090 |

---

## 10. O que ainda não existe
- **Push com o app fechado** (Firebase Cloud Messaging ou Expo Push). Hoje, com o app fechado, a notificação fica guardada e aparece ao abrir. O celular não toca.
- **Trânsito ao vivo** no tempo de chegada: só com API paga.
- **Aluno com contrato inativo** entrando na trip: regra a decidir.
- **Pagamento** (o tipo `PAYMENT` existe, mas nada gera notificação dele ainda).
