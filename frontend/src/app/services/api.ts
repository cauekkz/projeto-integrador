// o app so conversa com a propria origem; em dev o ng serve repassa pro backend e pro MinIO (proxy.conf.json).
// assim funciona igual no localhost, no celular pela rede (IP do PC) e por tunel (cloudflared).
export const API_URL = '/api';

// websocket do backend passa pelo /ws do proxy (o /chat do backend bateria com a rota /chat do app)
export function wsUrl(caminho: string): string {
  const protocolo = location.protocol === 'https:' ? 'wss:' : 'ws:';
  return `${protocolo}//${location.host}/ws${caminho}`;
}

// os links assinados do MinIO vem com http://localhost:9000; troca pelo /storage do proxy.
// a assinatura continua valendo porque o proxy manda o Host localhost:9000
export function urlDoStorage(url: string): string {
  return url.replace(/^https?:\/\/(localhost|127\.0\.0\.1):9000/, '/storage');
}
