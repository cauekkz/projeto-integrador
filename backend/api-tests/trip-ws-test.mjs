// Teste do WebSocket da trip (Node >= 22). Usa uma trip recem iniciada com 2 alunos na mesma rota:
// ALUNO (filho do R1 e do R2) e IRMAO (filho do R1), embarcando no mesmo ponto e descendo na escola.
// Saida: linhas "PASS|nome" ou "FAIL|nome|detalhe", lidas pelo run-api-tests.sh
import { readFileSync } from 'node:fs';

const { BASE, ROUTE, BOARD_LAT, BOARD_LNG, SCHOOL_LAT, SCHOOL_LNG, TRIP, TOKEN_DRIVER, TOKEN_R1, TOKEN_R2, TOKEN_OUTSIDER, STUDENT, STUDENT3, BOARD_STOP, SCHOOL_STOP, DIRLOG, REAL_MAPS } = process.env;
// com a API real as chamadas nao passam pelo log do simulador: a contagem fica de fora
const countCalls = REAL_MAPS !== '1';
const wsBase = BASE.replace(/^http/, 'ws');
const report = (ok, name, detail = '') => console.log(ok ? `PASS|${name}` : `FAIL|${name}|${String(detail).slice(0, 2000)}`);
const skip = (name) => console.log(`SKIP|${name}`);
// posicoes da van a partir das coordenadas reais dos pontos (o embarque so vale a ate 1 km do ponto)
const fix = (n) => Number(n.toFixed(6));
const BOARD = [Number(BOARD_LAT), Number(BOARD_LNG)];
const SCHOOL = [Number(SCHOOL_LAT), Number(SCHOOL_LNG)];
const NEAR1 = [fix(BOARD[0] + 0.0015), BOARD[1]];
const NEAR2 = [fix(BOARD[0] + 0.001), BOARD[1]];
const FAR = [fix(BOARD[0] + 0.05), BOARD[1]]; // ~5,5 km
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const dirCalls = () => readFileSync(DIRLOG, 'utf8').split('\n').filter(Boolean).length;

function open(token) {
  return new Promise((resolve, reject) => {
    const ws = new WebSocket(`${wsBase}/trip/${TRIP}?token=${token}`);
    const client = { ws, messages: [], closeCode: null };
    ws.onmessage = (e) => client.messages.push(JSON.parse(e.data));
    ws.onclose = (e) => { client.closeCode = e.code; };
    ws.onopen = () => resolve(client);
    ws.onerror = () => reject(new Error('erro ao conectar'));
  });
}

// espera a primeira mensagem nova que satisfaz a condicao
async function waitFor(client, predicate, timeoutMs = 4000) {
  const start = client.seen ?? 0;
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    for (let i = start; i < client.messages.length; i++) {
      if (predicate(client.messages[i])) {
        client.seen = i + 1;
        return client.messages[i];
      }
    }
    await sleep(50);
  }
  return null;
}

async function http(method, path, token, body) {
  const headers = { Authorization: `Bearer ${token}` };
  if (body) headers['Content-Type'] = 'application/json';
  const res = await fetch(`${BASE}${path}`, { method, headers, body: body && JSON.stringify(body) });
  return res.status;
}

const driver = await open(TOKEN_DRIVER);
const r1 = await open(TOKEN_R1);
const r2 = await open(TOKEN_R2);
const outsider = await open(TOKEN_OUTSIDER);
// notificacoes do R2 (WebSocket por usuario, o token diz quem e)
const notif = await new Promise((resolve, reject) => {
  const ws = new WebSocket(`${wsBase}/notifications?token=${TOKEN_R2}`);
  const client = { ws, messages: [] };
  ws.onmessage = (e) => client.messages.push(JSON.parse(e.data));
  ws.onopen = () => resolve(client);
  ws.onerror = () => reject(new Error('erro ao conectar notificacoes'));
});

// 1. ao conectar, cada um ja recebe o estado (sem ficar perguntando)
let d = await waitFor(driver, (m) => m.type === 'trip-update');
report(d && d.following && d.mode === 'DRIVER' && d.stops.length === 2 && d.students.length === 2 && d.path?.length > 1,
  'ws trip: motorista recebe a trip inteira ao conectar', JSON.stringify(d));
let p1 = await waitFor(r1, (m) => m.type === 'trip-update');
report(p1 && p1.following && p1.mode === 'FOLLOWING' && p1.targetStopId === BOARD_STOP && p1.etaSeconds > 0 && p1.path?.length > 1 && p1.stops === null,
  'ws trip: pai acompanha a van indo pro ponto do filho (trajeto + tempo, sem os outros pontos)', JSON.stringify(p1));
report(p1 && p1.students.map((s) => s.studentId).sort().join() === [STUDENT, STUDENT3].sort().join(),
  'ws trip: pai ve so os filhos dele', JSON.stringify(p1?.students));
let p2 = await waitFor(r2, (m) => m.type === 'trip-update');
report(p2 && p2.students.length === 1 && p2.students[0].studentId === STUDENT,
  'ws trip: outro responsavel ve so o aluno dele', JSON.stringify(p2?.students));
await sleep(500);
report(outsider.closeCode === 1003, 'ws trip: quem nao tem filho na trip e desconectado (1003)', `close ${outsider.closeCode}`);

// 2. GPS do motorista pelo WebSocket chega na hora pro pai, sem chamar a API de rotas
let calls = dirCalls();
driver.ws.send(JSON.stringify({ type: 'position', latitude: NEAR1[0], longitude: NEAR1[1] }));
p1 = await waitFor(r1, (m) => m.latitude === NEAR1[0] && m.longitude === NEAR1[1]);
report(!!p1, 'ws trip: GPS do motorista chega pro pai em tempo real', JSON.stringify(r1.messages.at(-1)));
if (countCalls) report(dirCalls() === calls, 'ws trip: GPS dentro do intervalo nao chama a API de rotas', `${dirCalls() - calls} chamadas`);
else skip('ws trip: GPS dentro do intervalo nao chama a API de rotas');

// 3. passado o intervalo, o proximo GPS recalcula trajeto e tempos (1 chamada)
await sleep(3300);
calls = dirCalls();
driver.ws.send(JSON.stringify({ type: 'position', latitude: NEAR2[0], longitude: NEAR2[1] }));
const moved = await waitFor(r1, (m) => m.latitude === NEAR2[0]);
if (countCalls) report(dirCalls() - calls === 1, 'ws trip: depois do intervalo o GPS recalcula o tempo (1 chamada)', `${dirCalls() - calls} chamadas`);
// API real: o trajeto recalculado comeca na rua mais proxima da posicao nova
else report(moved && Math.abs(moved.path?.[0]?.[0] - NEAR2[1]) < 0.005 && Math.abs(moved.path?.[0]?.[1] - NEAR2[0]) < 0.005 && moved.etaSeconds > 0,
  'ws trip: depois do intervalo o GPS recalcula trajeto e tempo pela API real', JSON.stringify(moved?.path?.[0]));

// 3b. desviou da linha (1 km pro lado): recalcula na hora, sem esperar o intervalo
// (o backend dos testes roda com TRIP_OFF_ROUTE_MIN_SECONDS=0)
const OFF = [NEAR2[0], fix(NEAR2[1] + 0.01)];
calls = dirCalls();
driver.ws.send(JSON.stringify({ type: 'position', latitude: OFF[0], longitude: OFF[1] }));
await waitFor(r1, (m) => m.longitude === OFF[1]);
if (countCalls) report(dirCalls() - calls === 1, 'ws trip: van saiu da linha: recalcula antes do intervalo (1 chamada)', `${dirCalls() - calls} chamadas`);
else skip('ws trip: van saiu da linha: recalcula antes do intervalo (1 chamada)');

// 4. pai nao manda posicao
r1.ws.send(JSON.stringify({ type: 'position', latitude: 1, longitude: 1 }));
const err = await waitFor(r1, (m) => m.type === 'error');
report(err && /motorista/.test(err.message), 'ws trip: pai nao consegue mandar posicao', JSON.stringify(err));

// 5. embarque so perto do ponto: longe (5 km) da 409, chegando no ponto funciona
driver.ws.send(JSON.stringify({ type: 'position', latitude: FAR[0], longitude: FAR[1] }));
await waitFor(driver, (m) => m.latitude === FAR[0]);
report(await http('PUT', `/api/trip/${TRIP}/student/${STUDENT}/board`, TOKEN_DRIVER) === 409, 'ws trip: van longe do ponto (5 km) nao embarca o aluno');
driver.ws.send(JSON.stringify({ type: 'position', latitude: BOARD[0], longitude: BOARD[1] }));
await waitFor(driver, (m) => m.latitude === BOARD[0] && m.longitude === BOARD[1]);
report(await http('PUT', `/api/trip/${TRIP}/student/${STUDENT}/board`, TOKEN_DRIVER) === 200, 'ws trip: van no ponto: motorista embarca o aluno');
const boardedNotif = await waitFor(notif, (m) => m.type === 'notification' && m.notification.type === 'STUDENT_BOARDED');
report(boardedNotif && boardedNotif.notification.payload.studentIds[0] === STUDENT && !!boardedNotif.notification.createdAt
  && notif.messages[0]?.type === 'unread-count',
  'ws notificacoes: pai recebe "embarcou" na hora (e o contador ao conectar)', JSON.stringify(notif.messages.slice(0, 3)));
// o pai avisa no meio da viagem que o irmao nao vai (ele ainda nao tinha embarcado)
const today = new Intl.DateTimeFormat('en-CA', { timeZone: 'America/Sao_Paulo' }).format(new Date());
report(await http('POST', `/api/student/${STUDENT3}/absences`, TOKEN_R1, { routeId: ROUTE, date: today }) === 201,
  'ws trip: pai avisa no meio da viagem que o irmao nao vai');
p2 = await waitFor(r2, (m) => m.targetStopId === SCHOOL_STOP && m.following);
report(!!p2, 'ws trip: pai acompanha a ida pra escola do filho', JSON.stringify(r2.messages.at(-1)));
p1 = await waitFor(r1, (m) => m.students?.some((s) => s.studentId === STUDENT3 && s.status === 'ABSENT'));
report(!!p1, 'ws trip: pai ve na hora que o filho ficou ausente', JSON.stringify(r1.messages.at(-1)?.students));

// 6. a van chega na escola e entrega: o pai para de ver a van
driver.ws.send(JSON.stringify({ type: 'position', latitude: SCHOOL[0], longitude: SCHOOL[1] }));
await waitFor(driver, (m) => m.latitude === SCHOOL[0] && m.longitude === SCHOOL[1]);
report(await http('PUT', `/api/trip/${TRIP}/student/${STUDENT}/alight`, TOKEN_DRIVER) === 200, 'ws trip: aluno entregue na escola');
p2 = await waitFor(r2, (m) => m.following === false && m.students[0].status === 'DELIVERED');
report(p2 && p2.mode === 'DONE' && !!p2.students[0].deliveredAt && p2.latitude === null && p2.path === null, 'ws trip: filho entregue e a rota segue: pai em DONE com a hora da entrega (sem van e sem trajeto)', JSON.stringify(p2));

// 7. fim da trip chega pra todo mundo
report(await http('POST', `/api/trip/${TRIP}/finish`, TOKEN_DRIVER) === 200, 'ws trip: motorista finaliza');
d = await waitFor(driver, (m) => m.status === 'FINISHED');
p1 = await waitFor(r1, (m) => m.status === 'FINISHED');
report(!!d && !!p1, 'ws trip: todos recebem a trip finalizada');

for (const c of [driver, r1, r2, notif]) c.ws.close();
await sleep(200);
process.exit(0);
