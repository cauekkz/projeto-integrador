import { Injectable, computed, inject, signal } from '@angular/core';
import { HttpClient, HttpContext } from '@angular/common/http';
import { SKIP_ERROR_POPUP } from '../interceptor/http-context';
import { AuthService } from './auth.service';

// mensagens do WebSocket /notifications: o total ao conectar e cada notificacao nova
type NotificationSocketMessage =
  | { type: 'unread-count'; count: number }
  | { type: 'notification'; notification: { read: boolean } };

// contador de notificacoes nao lidas, compartilhado por todos os sinos do app
@Injectable({
  providedIn: 'root',
})
export class NotificationService {
  private apiUrl = 'http://localhost:9090/api/notifications';
  private wsUrl = 'ws://localhost:9090/notifications';

  private http = inject(HttpClient);
  private authService = inject(AuthService);

  private socket?: WebSocket;
  private reconexao?: ReturnType<typeof setTimeout>;

  readonly naoLidas = signal(0);
  // o badge nao cabe mais que 3 caracteres
  readonly rotulo = computed(() => (this.naoLidas() > 99 ? '99+' : String(this.naoLidas())));

  // cada tela com sino chama; so a primeira abre o socket, as outras so atualizam o numero
  iniciar() {
    this.atualizar();
    if (this.socket || this.reconexao) return;
    this.conectar();
  }

  atualizar() {
    this.http
      .get<{ count: number }>(`${this.apiUrl}/unread-count`, {
        context: new HttpContext().set(SKIP_ERROR_POPUP, true),
      })
      .subscribe({
        next: (res) => this.naoLidas.set(res.count ?? 0),
        error: (err) => console.error('Erro ao carregar notificações:', err),
      });
  }

  // o WebSocket so confere o token na abertura: pega um valido antes de conectar
  private conectar() {
    this.authService.getValidToken().subscribe({
      next: (token) => {
        if (!token || this.socket) return;
        this.socket = new WebSocket(`${this.wsUrl}?token=${encodeURIComponent(token)}`);

        this.socket.onmessage = (event: MessageEvent) => {
          try {
            const msg: NotificationSocketMessage = JSON.parse(event.data);
            if (msg.type === 'unread-count') this.naoLidas.set(msg.count ?? 0);
            if (msg.type === 'notification' && !msg.notification?.read) this.naoLidas.update((n) => n + 1);
          } catch (error) {
            console.error('Erro ao processar notificação:', error);
          }
        };

        // caiu (backend reiniciou, rede): tenta de novo enquanto o usuario estiver logado
        this.socket.onclose = () => {
          this.socket = undefined;
          if (!this.authService.getToken()) {
            this.naoLidas.set(0);
            return;
          }
          this.reconexao = setTimeout(() => {
            this.reconexao = undefined;
            this.conectar();
          }, 5000);
        };
      },
      error: (error) => console.error('Não foi possível abrir as notificações:', error),
    });
  }
}
