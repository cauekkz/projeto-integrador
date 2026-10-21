# Execução da rota (trip): resumo pra ler com calma

## 1. A ideia em uma frase

A **rota** é a configuração fixa (pontos, alunos, dias). A **trip** é a rota rodando **num dia**.
Cada aluno que vai naquele dia vira um **boarding** (status dele na viagem).
O tempo real (GPS, linha no mapa, "faltam 7 min") fica no **Redis** e chega pelo **WebSocket**.

```
Banco (Postgres)              Redis                         WebSocket /trip/{tripId}
trips, boardings,     ->      trip:{id}:live (estado)  ->   motorista e pais recebem
student_absences              canal "trip-{id}" (aviso)     cada um com a sua visão
```

## 2. Tabelas novas ou alteradas (V22)

| Tabela | O que guarda | Regra |
|---|---|---|
| `trips` (+ `driver_id`) | a viagem do dia: rota, motorista, van, data, status, hora de início e fim | só 1 trip não cancelada por rota por dia |
| `boardings` | um por aluno na trip: PENDING, BOARDED, DELIVERED, ABSENT | 1 por aluno por trip |
| `routes.vehicle_id` | van padrão da rota (opcional) | |
| `student_absences` | "meu filho não vai na rota X no dia Y" | 1 por aluno + rota + dia; vale só aquele dia |

## 3. O fluxo de um dia, na ordem

### Antes do motorista iniciar
- **Não existe trip.** Então `GET /api/student/{id}/today-trips` devolve **lista vazia**. Sim, ele só tem
  resultado depois que o motorista aperta "iniciar".
- O pai **já pode avisar falta** (`POST /api/student/{id}/absences`). Fica guardada em `student_absences`.
  Quando a trip começar, esse aluno já entra como `ABSENT`.

### Motorista aperta "iniciar rota"
`POST /api/trip/start/{routeId}` com `{latitude, longitude, vehicleId?}`

1. Confere se a rota é dele e se não tem aluno com pendência (sem embarque ou desembarque).
2. Já tem trip hoje? Dá **409**. Com `?restart=true`, cancela a antiga e começa outra.
   Se a de hoje já foi finalizada, não deixa recomeçar.
3. Escolhe a van: a que veio no corpo ou a van padrão da rota. Tem que estar vinculada ao motorista.
4. Pega os alunos que têm ponto nessa rota **no dia da semana de hoje**.
5. Cria a trip e um boarding por aluno (`PENDING`, ou `ABSENT` se o pai avisou falta).
6. Grava a posição no Redis e **chama a API de rotas uma vez**. Com isso tem a linha, a distância e o
   tempo até cada ponto.
7. Avisa o WebSocket.

### Durante a viagem
- **O app do motorista** abre o WebSocket `/trip/{tripId}?token=...` e manda o GPS:
  `{"type":"position","latitude":..,"longitude":..}`
  - Toda posição é gravada e repassada na hora para quem está conectado.
  - A API de rotas só é chamada **se o último cálculo tiver mais de 60 s** (`trip.eta-refresh-seconds`).
    No meio desse intervalo, o tempo só vai descontando.
- **Embarcou:** `PUT /api/trip/{tripId}/student/{id}/board`. O caminho não muda, então **não chama a API**.
  Só atualiza e avisa.
- **Desceu:** `PUT .../alight`. Mesma coisa.
- **Motorista pula o aluno:** `PUT .../absent`. O ponto pode sair do caminho, então **chama a API**.
- **O pai avisa falta com a trip rodando:** o aluno vira `ABSENT` na hora, **chama a API**, e o motorista
  recebe a mensagem `ABSENCE` no chat. Se o pai desfizer, o aluno volta para `PENDING`.

### Fim
`POST /api/trip/{tripId}/finish`: status `FINISHED`, grava a hora e avisa todo mundo.

### O app fechou no meio
`GET /api/trip/route/{routeId}/today` devolve a trip de hoje. O motorista escolhe **continuar**
(reconecta o WebSocket) ou **recomeçar** (`start?restart=true`).

## 4. Quando a API de rotas (OpenRouteService) é chamada

| Momento | Chama? | Por quê |
|---|---|---|
| Iniciar | sim | precisa da primeira linha e dos tempos |
| GPS pelo WebSocket | **no máximo 1 vez por minuto** | a van andou, o tempo muda |
| Embarque ou desembarque | não | o caminho é o mesmo |
| Aluno ausente (motorista ou pai) | sim | um ponto pode ter saído do caminho |
| `recalculate` | sim | botão manual (ver seção 6) |
| Finalizar | não | |

Numa rota de 1 h, isso dá umas 60 chamadas. O limite grátis é de 2000 por dia.

## 5. O que cada um vê no WebSocket

- **Motorista:** tudo, ou seja, a posição, a linha inteira, todos os pontos com o tempo de cada um e todos os alunos.
- **Pai:** só os **filhos dele** e o status de cada um. A **van, a linha e o tempo** aparecem só quando
  **o próximo ponto da van é do filho dele**: indo buscá-lo em casa, ou levando-o para a escola.
  Nesse caso, a linha vai **só até esse ponto**. Fora disso, ele vê só o status ("embarcou", "entregue"...).
- **Quem não tem filho na trip:** é desconectado (código 1003).

O "faltam X min" que o pai vê é o tempo do último cálculo da API **menos o tempo que já passou**.

## 6. O `recalculate`: pra que serve, e a minha opinião

`POST /api/trip/{tripId}/recalculate` (corpo opcional com a posição) **força** um cálculo novo agora,
sem esperar o intervalo de 60 s.

Ele **não é polling**: é um botão que o motorista aperta se quiser. Foi útil enquanto não tinha WebSocket.
**Agora ele é quase redundante**, porque o GPS pelo WebSocket já recalcula sozinho a cada minuto. Sobram
dois usos:
- o motorista desviou e quer o tempo certo **na hora**;
- o WebSocket caiu e ele quer atualizar pelo HTTP.

**Recomendação:** pode remover, que não faz falta. Se você mandar, eu tiro (controller, service, teste e README).

## 7. Endpoints (resumo)

**Motorista (`/api/trip`):**

| Rota | Faz |
|---|---|
| `POST /start/{routeId}` (`?restart=true`) | inicia, ou recomeça |
| `GET /route/{routeId}/today` | trip de hoje (pra continuar) |
| `GET /{tripId}` | trip completa |
| `POST /{tripId}/recalculate` | força o cálculo (ver seção 6) |
| `PUT /{tripId}/student/{id}/board` | embarcou |
| `PUT /{tripId}/student/{id}/alight` | desceu |
| `PUT /{tripId}/student/{id}/absent` | pulou o aluno |
| `POST /{tripId}/finish` | finaliza |
| `PUT /api/route/{routeId}/set-vehicle/{vehicleId}` | van padrão da rota |

**Pai (`/api/student/{id}`):**

| Rota | Faz |
|---|---|
| `POST /absences` `{routeId, date}` | avisa falta |
| `GET /absences` | faltas de hoje em diante |
| `DELETE /absences/{absenceId}` | desfaz a falta |
| `GET /today-trips` | trips de hoje do filho; dá o `tripId` pra abrir o WebSocket |

**WebSocket:** `ws://.../trip/{tripId}?token=JWT`. O motorista manda a posição, e todos recebem `trip-update`.

## 8. Os services, método por método

### TripService: as regras (banco)
- `start`: o fluxo da seção 3.
- `todayTrip` e `getTrip`: consulta, só para o motorista dono.
- `recalculate`: grava a posição, se vier, e chama a API.
- `board`, `alight` e `markAbsent`: mudam o boarding, atualizam o Redis e avisam o WebSocket.
- `finish`: encerra.
- `studentTripsToday`: as trips de hoje do filho, para o pai. **Fica vazio até o motorista iniciar.**
- `applyAbsence`: chamado pelo StudentAbsenceService quando o pai avisa ou desfaz uma falta com a trip rodando.
- Auxiliares: `routeOwner` (a rota é do motorista?), `ownTrip` e `activeOwnTrip` (a trip é dele, está
  rodando?), `boardingOf`, `resolveVehicle` (qual van) e `toResponse` (monta o JSON).

### TripLiveService: o tempo real (Redis)
- `access`: quem pode conectar no WebSocket e quais filhos o pai vê.
- `buildStops`: os pontos de hoje, em ordem, pulando os sem aluno no dia. Cada um vem com `done` (já não precisa parar).
- `pendingAtStop`: ainda precisa parar ali por causa desse aluno?
- `updatePosition`: GPS do WebSocket. Grava, recalcula se já passou 1 min e avisa.
- `setPosition`: só grava a posição.
- `refresh(trip, callApi)`: remonta o estado. Com `callApi`, chama a API e calcula o tempo até cada ponto
  (somando os trechos) e onde cada ponto fica na linha. Sem `callApi`, mantém a linha antiga.
- `state`: lê o estado do Redis.
- `publish`: manda o aviso "trip-{id}" no Redis. Funciona com mais de um servidor.
- `view`: monta o que cada pessoa recebe (seção 5).

### StudentAbsenceService: faltas do pai
- `create`: valida (é o responsável? a data passou? o filho está na rota? já avisou?), grava, tira o filho
  da trip se ela estiver rodando e manda a mensagem no chat do motorista.
- `upcoming`: lista as faltas de hoje em diante.
- `cancel`: desfaz, volta o filho para a trip e avisa o motorista.

### TripWebSocketHandler: o canal
- Ao conectar: checa o acesso e manda o estado atual.
- Ao receber mensagem: só aceita posição, e só do motorista.
- Aviso do Redis: manda a visão de cada um para todos os conectados naquela trip.

## 9. O que ainda falta (decisões suas)

- **O pai não fica sabendo sozinho que a trip começou.** Ele abre o app, chama `today-trips` uma vez e
  conecta. Pra avisar ("a van saiu"), precisa de **notificação push**, que você disse que é depois.
- Aluno com contrato inativo entrar na trip: você ia decidir depois.
- `recalculate`: manter ou remover (seção 6).
- A API real de rotas só foi testada com o simulador. Falta testar com a chave real.
