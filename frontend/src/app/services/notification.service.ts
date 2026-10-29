import { Injectable, computed, inject, signal } from '@angular/core';
import { HttpClient, HttpContext } from '@angular/common/http';
import { Router } from '@angular/router';
import { firstValueFrom, forkJoin, of, Subject } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { SKIP_ERROR_POPUP } from '../interceptor/http-context';
import { AuthService } from './auth.service';
import { ChatWebSocketService } from './chat.service';
import { ChatPage } from '../models/chat-message';
import { API_URL, wsUrl } from './api';

// NotificationResponseDTO do backend (GET /api/notifications)
export interface Notificacao {
  id: string;
  type: string;
  title: string;
  message: string;
  payload: Record<string, unknown> | null;
  createdAt: string;
  read: boolean;
}

// ultima mensagem recebida de cada conversa, mostrada como notificacao
export interface NotificacaoMensagem {
  chatId: string;
  nome: string;
  previa: string;
  enviadaEm: string;
  lida: boolean;
}

// mensagens do WebSocket /notifications: o total ao conectar e cada notificacao nova
type NotificationSocketMessage =
  | { type: 'unread-count'; count: number }
  | { type: 'notification'; notification: Notificacao };

// PROVISORIO: o backend ainda nao gera notificacao de mensagem de chat. Ate gerar, a ultima mensagem de cada
// conversa vira notificacao aqui no front, e "lida" = a conversa foi aberta depois dela (guardado neste navegador)
const PREFIXO_CHAT_VISTO = 'vanroute.chatVisto.';

// contador de notificacoes nao lidas, compartilhado por todos os sinos do app
@Injectable({
  providedIn: 'root',
})
export class NotificationService {
  private apiUrl = `${API_URL}/notifications`;
  private wsUrl = wsUrl('/notifications');
  private semPopup = () => ({ context: new HttpContext().set(SKIP_ERROR_POPUP, true) });

  private http = inject(HttpClient);
  private router = inject(Router);
  private authService = inject(AuthService);
  private chatService = inject(ChatWebSocketService);

  private socket?: WebSocket;
  private reconexao?: ReturnType<typeof setTimeout>;

  private readonly doBackend = signal(0);
  private readonly mensagensNaoLidas = signal(0);
  // o sino soma as do backend com as mensagens novas
  readonly naoLidas = computed(() => this.doBackend() + this.mensagensNaoLidas());
  // o badge nao cabe mais que 3 caracteres
  readonly rotulo = computed(() => (this.naoLidas() > 99 ? '99+' : String(this.naoLidas())));
  // notificacao que chegou pelo WebSocket (a tela de notificacoes poe no topo da lista)
  readonly chegou = new Subject<Notificacao>();

  // cada tela com sino chama; so a primeira abre o socket, as outras so atualizam o numero
  iniciar() {
    this.atualizar();
    this.carregarMensagens();
    if (this.socket || this.reconexao) return;
    this.conectar();
  }

  abrirTela() {
    this.router.navigate(['/notifications']);
  }

  atualizar() {
    this.http.get<{ count: number }>(`${this.apiUrl}/unread-count`, this.semPopup()).subscribe({
      next: (res) => this.doBackend.set(res.count ?? 0),
      error: (err) => console.error('Erro ao carregar notificações:', err),
    });
  }

  listar(pagina = 0) {
    return this.http.get<ChatPage<Notificacao>>(`${this.apiUrl}?page=${pagina}&size=20`, this.semPopup());
  }

  marcarLida(notificacao: Notificacao) {
    if (notificacao.read) return;
    notificacao.read = true;
    this.doBackend.update((n) => Math.max(0, n - 1));
    this.http.put<void>(`${this.apiUrl}/${notificacao.id}/read`, null, this.semPopup()).subscribe({
      error: (err) => {
        console.error('Erro ao marcar notificação como lida:', err);
        this.atualizar();
      },
    });
  }

  marcarTodasLidas() {
    this.doBackend.set(0);
    this.http.put<void>(`${this.apiUrl}/read-all`, null, this.semPopup()).subscribe({
      error: (err) => {
        console.error('Erro ao marcar notificações como lidas:', err);
        this.atualizar();
      },
    });
    this.marcarTodasConversasVistas();
  }

  // ---------- mensagens (provisorio, ver PREFIXO_CHAT_VISTO) ----------

  private ultimasMensagens: NotificacaoMensagem[] = [];

  async carregarMensagens(): Promise<NotificacaoMensagem[]> {
    const meuId = this.authService.getUserIdFromToken();
    if (!meuId) return [];
    try {
      const chats = (await firstValueFrom(this.chatService.getChats(0, 20))).content ?? [];
      const ultimas = await firstValueFrom(
        chats.length
          ? forkJoin(chats.map((chat) => this.chatService.getLatestMessage(chat.id).pipe(catchError(() => of(null)))))
          : of([]),
      );

      this.ultimasMensagens = chats
        .map((chat, i) => ({ chat, msg: ultimas[i] }))
        // so mensagem normal que o outro mandou (proposta/aceite ja viram notificacao do backend)
        .filter(({ msg }) => msg && msg.senderId !== meuId && msg.messageType === 'REGULAR_CHAT')
        .map(({ chat, msg }) => ({
          chatId: chat.id,
          nome: chat.otherUserName,
          previa: msg!.content?.trim() || (msg!.attachmentType === 'IMAGE' ? 'Enviou uma imagem' : 'Enviou um anexo'),
          enviadaEm: msg!.sentAt,
          lida: new Date(msg!.sentAt).getTime() <= this.vistoEm(chat.id),
        }));
      this.contarMensagens();
      return this.ultimasMensagens;
    } catch (error) {
      console.error('Erro ao carregar mensagens para as notificações:', error);
      return this.ultimasMensagens;
    }
  }

  // chamado ao abrir/sair de uma conversa
  marcarConversaVista(chatId: string) {
    try {
      localStorage.setItem(PREFIXO_CHAT_VISTO + chatId, String(Date.now()));
    } catch {
      // sem storage (aba privada): a mensagem continua aparecendo como nova
    }
    this.ultimasMensagens = this.ultimasMensagens.map((m) => (m.chatId === chatId ? { ...m, lida: true } : m));
    this.contarMensagens();
  }

  private marcarTodasConversasVistas() {
    this.ultimasMensagens.forEach((m) => this.marcarConversaVista(m.chatId));
  }

  private vistoEm(chatId: string): number {
    try {
      return Number(localStorage.getItem(PREFIXO_CHAT_VISTO + chatId)) || 0;
    } catch {
      return 0;
    }
  }

  private contarMensagens() {
    this.mensagensNaoLidas.set(this.ultimasMensagens.filter((m) => !m.lida).length);
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
            if (msg.type === 'unread-count') this.doBackend.set(msg.count ?? 0);
            if (msg.type === 'notification' && msg.notification) {
              if (!msg.notification.read) this.doBackend.update((n) => n + 1);
              this.chegou.next(msg.notification);
            }
          } catch (error) {
            console.error('Erro ao processar notificação:', error);
          }
        };

        // caiu (backend reiniciou, rede): tenta de novo enquanto o usuario estiver logado
        this.socket.onclose = () => {
          this.socket = undefined;
          if (!this.authService.getToken()) {
            this.doBackend.set(0);
            this.mensagensNaoLidas.set(0);
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
