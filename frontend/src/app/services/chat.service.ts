import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { firstValueFrom, map, Observable, of, Subject, switchMap } from 'rxjs';
import { ChatMessage, ChatPage, ChatSummary } from '../models/chat-message';

@Injectable({
  providedIn: 'root',
})
export class ChatWebSocketService {
  private apiUrl = 'http://localhost:9090/api';
  private wsUrl = 'ws://localhost:9090';

  private socket?: WebSocket;
  private messageSubject = new Subject<ChatMessage>();

  public messages$ = this.messageSubject.asObservable();

  constructor(private http: HttpClient) {}

  createChatMessage(code: string) {
    return this.http.post<string>(
      `${this.apiUrl}/invite-code/redeem`,
      { inviteCode: code },
      { observe: 'response' },
    );
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

  connect(chatId: string): void {
    const token = localStorage.getItem('token');

    if (!token) {
      console.error('Token ausente para conectar ao chat.');
      return;
    }

    if (this.socket?.readyState === WebSocket.OPEN) {
      return;
    }

    const url = `${this.wsUrl}/chat/${chatId}?token=${encodeURIComponent(token)}`;
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

    const response = await fetch(upload.uploadUrl, {
      method: 'PUT',
      headers: { 'Content-Type': contentType },
      body: file,
    });

    if (!response.ok) {
      throw new Error(`Falha ao enviar o anexo para o armazenamento (${response.status}).`);
    }

    const imageUrl = new URL(upload.uploadUrl);
    imageUrl.search = '';
    imageUrl.hash = '';
    return imageUrl.toString();
  }

  sendAttachmentMessage(chatId: string, attachmentUrl: string, attachmentType: 'IMAGE' | 'OTHER') {
    return this.http.post<ChatMessage>(`${this.apiUrl}/chats/${chatId}/messages`, {
      content: '',
      attachmentType,
      attachmentUrl,
    });
  }
}
