import { Injectable } from '@angular/core';
import { HttpClient, HttpContext } from '@angular/common/http';
import { firstValueFrom, map, Observable, of, Subject, switchMap } from 'rxjs';
import { ChatMessage, ChatPage, ChatSummary, DriverLinkCode } from '../models/chat-message';
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
    return this.http.get<ChatPage<ChatSummary>>(`${this.apiUrl}/chats?page=${page}&size=${size}`);
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

  // o WebSocket so confere o token na abertura: pega um valido (renovando se venceu) antes de conectar
  connect(chatId: string): void {
    if (this.socket?.readyState === WebSocket.OPEN) {
      return;
    }

    this.chatAlvo = chatId;
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
      error: (error) => console.error('Não foi possível renovar a sessão para abrir o chat:', error),
    });
  }

  private abrirSocket(chatId: string, token: string): void {
    const url = `${wsUrl('/chat/' + chatId)}?token=${encodeURIComponent(token)}`;
    this.socket = new WebSocket(url);

    this.socket.onopen = () => {};

    this.socket.onmessage = (event: MessageEvent) => {
      try {
        const message: ChatMessage = JSON.parse(event.data);
        this.messageSubject.next(message);
      } catch (error) {
        console.error('Erro ao processar mensagem do WebSocket:', error);
      }
    };

    this.socket.onerror = (error) => {
      console.error('Erro no WebSocket do chat:', error);
    };

    this.socket.onclose = () => {
      this.socket = undefined;
    };
  }

  disconnect(): void {
    this.chatAlvo = undefined;
    this.socket?.close();
    this.socket = undefined;
  }

  getMessages(chatId: string, page = 0, size = 30) {
    return this.http.get<ChatPage<ChatMessage>>(
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
