import { Injectable } from '@angular/core';
import { Subject } from 'rxjs';
import { ChatMessage } from '../models/chat-message';
import { HttpClient } from '@angular/common/http';

@Injectable({
  providedIn: 'root',
})
export class ChatWebSocketService {
  private apiUrl = 'http://localhost:9090/api';

  private socket?: WebSocket;

  private messageSubject = new Subject<ChatMessage>();

  // pelo oq eu entendi essa desgraça aqui avisa q chegou uma msg e q ela é "observavel"
  public messages$ = this.messageSubject.asObservable();
  constructor(private http: HttpClient) {}

  connect(chatId: string): void {
    // conecta com o websocket
    if (this.socket?.readyState === WebSocket.OPEN) {
      // aq ele ve se essa conexão ja existe do ws
      return;
    }

    this.socket = new WebSocket(`ws://localhost:9090/ws/chat/${chatId}`);

    this.socket.onopen = () => {
      // aqui conecta normal
      console.log('WebSocket conectado');
    };

    this.socket.onmessage = (event: MessageEvent) => {
      // carrega as msg
      try {
        const message: ChatMessage = JSON.parse(event.data);

        // isso faz as msg carregar e passar pra proxima
        this.messageSubject.next(message);
      } catch (error) {
        console.error('Erro ao processar mensagem WebSocket:', error);
      }
    };

    this.socket.onerror = (error) => {
      console.error('Erro no WebSocket:', error);
    };

    this.socket.onclose = (event) => {
      console.log('WebSocket fechado:', event.code, event.reason);

      this.socket = undefined;
    };
  }

  // talvez n use isso, pq o redis é mediador e o REST envia pra ele e o ws recebe com esse metodo
  // n faz sentido chamar
  /*send(message: unknown): void {

    // verifica se a conexão ta aberta
    if (this.socket?.readyState !== WebSocket.OPEN) {
      console.error('WebSocket não está conectado');
      return;
    }


    // stringify da msg
    this.socket.send(JSON.stringify(message));
  }*/

  disconnect(): void {
    this.socket?.close();
    this.socket = undefined;
  }

  // parte do rest

  // pega as msg
  getMessages(chatId: string, page = 0, size = 30) {
    return this.http.get<any>(`${this.apiUrl}/chats/${chatId}/messages?page=${page}&size=${size}`);
  }

  // isso q n entendi, mas tem um bgl de enviar msg pelo REST tbm
  sendMessage(chatId: string, content: string) {
    return this.http.post<ChatMessage>(`${this.apiUrl}/chats/${chatId}/messages`, {
      content,
    });
  }
}
