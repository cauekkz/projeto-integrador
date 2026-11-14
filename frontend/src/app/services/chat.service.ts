import { Injectable } from '@angular/core';
import { HttpClient, HttpContext } from '@angular/common/http';
import { catchError, firstValueFrom, map, Observable, of, Subject, switchMap } from 'rxjs';
import { ChatMessage, ChatReadEvent, ChatSummary, DriverLinkCode } from '../models/chat-message';
import { Page } from './dto/page.dto';
import { SKIP_ERROR_POPUP } from '../interceptor/http-context';
import { AuthService } from './auth.service';
import { API_URL, urlDoStorage, wsUrl } from './api';

@Injectable({
  providedIn: 'root',
})
export class ChatWebSocketService {
  private apiUrl = API_URL;

  private socket?: WebSocket;
  // chat que a tela quer aberto: se sair antes do token chegar, nao abre o socket
  private chatAlvo?: string;
  private messageSubject = new Subject<ChatMessage>();

  public messages$ = this.messageSubject.asObservable();
  // {"type":"read"} chega pelo mesmo socket: nao e mensagem
  private readSubject = new Subject<ChatReadEvent>();
  public reads$ = this.readSubject.asObservable();
  // a conexao caiu e voltou: a tela busca as mensagens que chegaram enquanto estava fora
  private reconectouSubject = new Subject<string>();
  public reconectou$ = this.reconectouSubject.asObservable();

  private tentativas = 0;
  private esperaReconexao?: ReturnType<typeof setTimeout>;
  private batimento?: ReturnType<typeof setInterval>;
  private vigiando = false;

  constructor(
    private http: HttpClient,
    private authService: AuthService,
  ) {}

  // responsavel resgata o codigo do motorista; backend cria ou recupera o chat e devolve o id dele
  // o erro e tratado na tela (driver-code), por isso sem popup global
  createChatMessage(code: string) {
    return this.http.post<string>(
      `${this.apiUrl}/invite-code/redeem`,
      { inviteCode: code },
      { observe: 'response', context: new HttpContext().set(SKIP_ERROR_POPUP, true) },
    );
  }

  // codigo de conversa do motorista logado (identificado pelo jwt no backend)
  getDriverLinkCode() {
    return this.http.get<DriverLinkCode>(`${this.apiUrl}/driver/link-code`, {
      context: new HttpContext().set(SKIP_ERROR_POPUP, true),
    });
  }

  getChats(page = 0, size = 10) {
    return this.http.get<Page<ChatSummary>>(`${this.apiUrl}/chats?page=${page}&size=${size}`);
  }

  // id da conversa com esse usuario (GET /api/chats e paginado: procura pagina por pagina); null se nao tem
  findChatWith(userId: string, page = 0): Observable<string | null> {
    return this.getChats(page, 50).pipe(
      switchMap((pagina) => {
        const chat = (pagina.content ?? []).find((item) => item.otherUserId === userId);
        if (chat) return of(chat.id);
        if (pagina.last || page + 1 >= pagina.totalPages) return of(null);
        return this.findChatWith(userId, page + 1);
      }),
      catchError(() => of(null)),
    );
  }

  getLatestMessage(chatId: string): Observable<ChatMessage | null> {
    return this.getMessages(chatId, 0, 1).pipe(
      switchMap((page) => {
        if (page.totalPages <= 1) {
          return of(page.content[0] ?? null);
        }

        return this.getMessages(chatId, page.totalPages - 1, 1).pipe(
          map((latestPage) => latestPage.content[0] ?? null),
        );
      }),
    );
  }

  // o WebSocket so confere o token na abertura: pega um valido (renovando se venceu) antes de conectar.
  // No celular a conexao cai sozinha (tela apagada, troca de rede, tunel): enquanto a conversa estiver aberta,
  // ela volta sozinha (com espera crescente) e avisa em reconectou$ pra tela buscar o que chegou no meio-tempo
  connect(chatId: string): void {
    if (this.chatAlvo === chatId && this.socket && this.socket.readyState <= WebSocket.OPEN) {
      return;
    }
    if (this.chatAlvo !== chatId) {
      this.fecharSocket();
    }
    this.chatAlvo = chatId;
    this.vigiarAparelho();
    this.pedirToken(chatId);
  }

  private pedirToken(chatId: string): void {
    this.authService.getValidToken().subscribe({
      next: (token) => {
        if (this.chatAlvo !== chatId) {
          return;
        }
        if (!token) {
          console.error('Token ausente para conectar ao chat.');
          return;
        }
        this.abrirSocket(chatId, token);
      },
      error: (error) => {
        console.error('Não foi possível renovar a sessão para abrir o chat:', error);
        this.agendarReconexao(chatId);
      },
    });
  }

  private abrirSocket(chatId: string, token: string): void {
    const url = `${wsUrl('/chat/' + chatId)}?token=${encodeURIComponent(token)}`;
    const socket = new WebSocket(url);
    this.socket = socket;

    socket.onopen = () => {
      if (this.tentativas > 0) {
        this.reconectouSubject.next(chatId);
      }
      this.tentativas = 0;
      // mantem a conexao viva: tunel e proxy derrubam socket parado (o backend ignora o texto)
      clearInterval(this.batimento);
      this.batimento = setInterval(() => {
        if (socket.readyState === WebSocket.OPEN) socket.send('ping');
      }, 25000);
    };

    socket.onmessage = (event: MessageEvent) => {
      try {
        const dados = JSON.parse(event.data);
        if (dados?.type === 'read') {
          this.readSubject.next(dados as ChatReadEvent);
          return;
        }
        this.messageSubject.next(dados as ChatMessage);
      } catch (error) {
        console.error('Erro ao processar mensagem do WebSocket:', error);
      }
    };

    socket.onerror = (error) => {
      console.error('Erro no WebSocket do chat:', error);
    };

    socket.onclose = () => {
      // um socket velho fechando depois nao apaga o novo
      if (this.socket !== socket) {
        return;
      }
      this.socket = undefined;
      clearInterval(this.batimento);
      this.agendarReconexao(chatId);
    };
  }

  // 1s, 2s, 4s... ate 15s; so enquanto a conversa estiver aberta
  private agendarReconexao(chatId: string): void {
    if (this.chatAlvo !== chatId) {
      return;
    }
    clearTimeout(this.esperaReconexao);
    const espera = Math.min(15000, 1000 * 2 ** this.tentativas);
    this.tentativas++;
    this.esperaReconexao = setTimeout(() => {
      if (this.chatAlvo === chatId && !this.socket) {
        this.pedirToken(chatId);
      }
    }, espera);
  }

  // voltou pro app (tela acesa) ou a internet voltou: reconecta na hora, sem esperar a contagem
  private vigiarAparelho(): void {
    if (this.vigiando || typeof document === 'undefined') {
      return;
    }
    this.vigiando = true;
    const acordar = () => {
      const chatId = this.chatAlvo;
      if (!chatId || document.visibilityState !== 'visible') {
        return;
      }
      if (!this.socket || this.socket.readyState > WebSocket.OPEN) {
        clearTimeout(this.esperaReconexao);
        this.socket = undefined;
        this.tentativas = Math.max(this.tentativas, 1);
        this.pedirToken(chatId);
      }
    };
    document.addEventListener('visibilitychange', acordar);
    window.addEventListener('online', acordar);
  }

  private fecharSocket(): void {
    clearTimeout(this.esperaReconexao);
    clearInterval(this.batimento);
    this.tentativas = 0;
    const socket = this.socket;
    this.socket = undefined;
    socket?.close();
  }

  disconnect(): void {
    this.chatAlvo = undefined;
    this.fecharSocket();
  }

  // marca as mensagens do outro como lidas (zera o unreadCount); o outro recebe o evento read no socket. 204
  // sem popup: falhar aqui nao atrapalha a conversa
  markAsRead(chatId: string) {
    return this.http.put<void>(`${this.apiUrl}/chats/${chatId}/read`, null, {
      context: new HttpContext().set(SKIP_ERROR_POPUP, true),
    });
  }

  getMessages(chatId: string, page = 0, size = 30) {
    return this.http.get<Page<ChatMessage>>(
      `${this.apiUrl}/chats/${chatId}/messages?page=${page}&size=${size}`,
    );
  }

  sendMessage(chatId: string, content: string) {
    return this.http.post<ChatMessage>(`${this.apiUrl}/chats/${chatId}/messages`, {
      content,
    });
  }

  // devolve o fileKey: e ele que vai na mensagem (o link de leitura sai do /storage/download-url)
  async uploadAttachment(file: File): Promise<string> {
    const contentType = file.type || 'application/octet-stream';
    const params = new URLSearchParams({
      fileName: file.name,
      contentType,
    });
    const upload = await firstValueFrom(
      this.http.get<{ uploadUrl: string; fileKey: string }>(
        `${this.apiUrl}/storage/presigned-url?${params}`,
      ),
    );

    if (!upload?.uploadUrl || !upload.fileKey) {
      throw new Error('O servidor não retornou os dados necessários para enviar o anexo.');
    }

    const response = await fetch(urlDoStorage(upload.uploadUrl), {
      method: 'PUT',
      headers: { 'Content-Type': contentType },
      body: file,
    });

    if (!response.ok) {
      throw new Error(`Falha ao enviar o anexo para o armazenamento (${response.status}).`);
    }

    return upload.fileKey;
  }

  sendAttachmentMessage(chatId: string, attachmentUrl: string, attachmentType: 'IMAGE' | 'OTHER') {
    return this.http.post<ChatMessage>(`${this.apiUrl}/chats/${chatId}/messages`, {
      content: '',
      attachmentType,
      attachmentUrl,
    });
  }
}
