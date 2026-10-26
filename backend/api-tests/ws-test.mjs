// Teste de entrega pelo WebSocket do chat (Node >= 22, WebSocket nativo).
// Uso: BASE=... CHAT=... TOKEN_SENDER=... TOKEN_RECEIVER=... TOKEN_OUTSIDER=... node ws-test.mjs
// Saida: linhas "PASS|nome" ou "FAIL|nome|detalhe", lidas pelo run-api-tests.sh
const { BASE, CHAT, TOKEN_SENDER, TOKEN_RECEIVER, TOKEN_OUTSIDER } = process.env;
const wsBase = BASE.replace(/^http/, 'ws');
const report = (ok, name, detail = '') => console.log(ok ? `PASS|${name}` : `FAIL|${name}|${detail}`);

function open(token) {
  return new Promise((resolve, reject) => {
    const ws = new WebSocket(`${wsBase}/chat/${CHAT}?token=${token}`);
    const received = [];
    ws.onmessage = (e) => received.push(JSON.parse(e.data));
    ws.onopen = () => resolve({ ws, received });
    ws.onerror = () => reject(new Error('erro ao conectar'));
  });
}

function waitClose(ws, ms) {
  return new Promise((resolve) => {
    const t = setTimeout(() => resolve(null), ms);
    ws.onclose = (e) => { clearTimeout(t); resolve(e.code); };
  });
}

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

const receiver = await open(TOKEN_RECEIVER);
const sender = await open(TOKEN_SENDER);
await sleep(300); // garante que a sessao foi registrada no handler

const content = `ws-${Date.now()}`;
const res = await fetch(`${BASE}/api/chats/${CHAT}/messages`, {
  method: 'POST',
  headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${TOKEN_SENDER}` },
  body: JSON.stringify({ content, messageType: 'REGULAR_CHAT' }),
});
report(res.status === 201, 'ws: POST de mensagem durante conexao', `status ${res.status}`);

await sleep(1500);
const gotReceiver = receiver.received.some((m) => m.content === content);
const gotSender = sender.received.some((m) => m.content === content);
report(gotReceiver, 'ws: destinatario recebe a mensagem em tempo real (Redis pub/sub -> socket)', JSON.stringify(receiver.received));
report(gotSender, 'ws: remetente tambem recebe o eco', JSON.stringify(sender.received));

// usuario autenticado que nao participa do chat: handshake aceito, mas o handler fecha a conexao
const outsider = new WebSocket(`${wsBase}/chat/${CHAT}?token=${TOKEN_OUTSIDER}`);
const code = await waitClose(outsider, 3000);
report(code === 1003, 'ws: usuario fora do chat e desconectado (1003)', `close code ${code}`);

receiver.ws.close();
sender.ws.close();
await sleep(200);
process.exit(0);
