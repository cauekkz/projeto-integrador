import { Component, ElementRef, OnDestroy, OnInit, ViewChild } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { jwtDecode } from 'jwt-decode';
import { of, Subscription } from 'rxjs';
import { ChatMessage } from '../../../models/chat-message';
import { ChatWebSocketService } from '../../../services/chat.service';

@Component({
  selector: 'app-chat-details',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './chat-details.html',
  styleUrl: './chat-details.css',
})
export class ChatDetails implements OnInit, OnDestroy {
  chatId = '';
  mensagem = '';
  nomeContato = 'Contato';
  fotoContato = '/testee.jpg';
  origem = 'responsible-home';
  usuarioLogadoId = '';
  @ViewChild('messagesContainer') private messagesContainer?: ElementRef<HTMLElement>;
  private paginaMensagens = 0;
  private temMensagensAnteriores = false;
  private carregandoMensagens = false;
  private subscription?: Subscription;

  mensagens: Array<{
    id: string;
    texto: string;
    minha: boolean;
    hora: string;
    lida: boolean;
    attachmentUrl: string | null;
    attachmentType: string | null;
    sentAt: string;
  }> = [];

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private chatService: ChatWebSocketService,
  ) {}

  ngOnInit() {
    this.origem = this.route.snapshot.queryParamMap.get('from') || 'responsible-home';
    this.chatId = this.route.snapshot.paramMap.get('id') || '';
    this.usuarioLogadoId = this.getUserIdFromToken();
    const nav = this.router.getCurrentNavigation();
    const state = nav?.extras.state as { nomeContato?: string; fotoContato?: string } | undefined;
    this.nomeContato = state?.nomeContato || this.nomeContato;
    this.fotoContato = state?.fotoContato || this.fotoContato;

    if (this.chatId) {
      this.carregarMensagens();
      this.chatService.connect(this.chatId);
      this.subscription = this.chatService.messages$.subscribe((message) => {
        if (!message?.id) {
          return;
        }

        if (this.mensagens.some((item) => item.id === message.id)) {
          return;
        }

        this.adicionarMensagens([message]);
      });
    }
  }

  ngOnDestroy() {
    this.chatService.disconnect();
    this.subscription?.unsubscribe();
  }

  private getUserIdFromToken(): string {
    const token = localStorage.getItem('token');

    if (!token) {
      return '';
    }

    try {
      const decoded: any = jwtDecode(token);
      return decoded.id ?? decoded.sub ?? '';
    } catch {
      return '';
    }
  }

  private carregarMensagens() {
    this.carregandoMensagens = true;
    this.chatService.getMessages(this.chatId, 0).subscribe({
      next: (firstPage) => {
        const lastPageNumber = Math.max(firstPage.totalPages - 1, 0);
        const pageRequest = lastPageNumber === 0
          ? of(firstPage)
          : this.chatService.getMessages(this.chatId, lastPageNumber);

        pageRequest.subscribe({
          next: (latestPage) => {
            this.paginaMensagens = latestPage.number;
            this.temMensagensAnteriores = latestPage.number > 0;
            this.adicionarMensagens(latestPage.content ?? []);
            this.carregandoMensagens = false;
            requestAnimationFrame(() => {
              const container = this.messagesContainer?.nativeElement;
              if (container) {
                container.scrollTop = container.scrollHeight;
              }
            });
          },
          error: (error) => {
            this.carregandoMensagens = false;
            console.error('Erro ao carregar mensagens recentes:', error);
          },
        });
      },
      error: (error) => {
        this.carregandoMensagens = false;
        console.error('Erro ao carregar mensagens:', error);
      },
    });
  }

  carregarMensagensAnteriores(event: Event) {
    const container = event.target as HTMLElement;
    if (container.scrollTop > 60 || this.carregandoMensagens || !this.temMensagensAnteriores) {
      return;
    }

    const alturaAnterior = container.scrollHeight;
    const posicaoAnterior = container.scrollTop;
    this.carregandoMensagens = true;

    this.chatService.getMessages(this.chatId, this.paginaMensagens - 1).subscribe({
      next: (page) => {
        this.paginaMensagens = page.number;
        this.temMensagensAnteriores = page.number > 0;
        this.adicionarMensagens(page.content ?? []);
        this.carregandoMensagens = false;
        requestAnimationFrame(() => {
          container.scrollTop = posicaoAnterior + container.scrollHeight - alturaAnterior;
        });
      },
      error: (error) => {
        this.carregandoMensagens = false;
        console.error('Erro ao carregar mensagens anteriores:', error);
      },
    });
  }

  private adicionarMensagens(novasMensagens: ChatMessage[]) {
    const mensagensPorId = new Map(this.mensagens.map((message) => [message.id, message]));
    for (const message of novasMensagens) {
      mensagensPorId.set(message.id, this.mapMessage(message));
    }
    this.mensagens = Array.from(mensagensPorId.values())
      .sort((a, b) => new Date(a.sentAt).getTime() - new Date(b.sentAt).getTime());
  }

  private mapMessage(message: ChatMessage) {
    return {
      id: message.id,
      texto: message.content || '',
      minha: message.senderId === this.usuarioLogadoId,
      hora: this.formatHour(message.sentAt),
      lida: message.senderId === this.usuarioLogadoId,
      attachmentUrl: message.attachmentUrl,
      attachmentType: message.attachmentType,
      sentAt: message.sentAt,
    };
  }

  private formatHour(value: string) {
    return new Date(value).toLocaleTimeString('pt-BR', {
      hour: '2-digit',
      minute: '2-digit',
    });
  }

  voltar() {
    this.router.navigate(['/chat'], { queryParams: { from: this.origem } });
  }

  enviar() {
    const text = this.mensagem.trim();

    if (!text || !this.chatId) {
      return;
    }

    this.chatService.sendMessage(this.chatId, text).subscribe({
      next: (message) => {
        this.adicionarMensagens([message]);
        this.mensagem = '';
      },
      error: (error) => {
        console.error('Erro ao enviar mensagem:', error);
      },
    });
  }

  async selecionarImagem(event: Event) {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    input.value = '';

    if (!file || !this.chatId) {
      return;
    }

    try {
      const attachmentUrl = await this.chatService.uploadAttachment(file);
      const attachmentType = file.type.startsWith('image/') ? 'IMAGE' : 'OTHER';
      this.chatService.sendAttachmentMessage(this.chatId, attachmentUrl, attachmentType).subscribe({
        next: (message) => {
          this.adicionarMensagens([message]);
        },
        error: (error) => {
          console.error('O arquivo foi enviado, mas não foi possível publicá-lo no chat:', error);
        },
      });
    } catch (error) {
      console.error('Erro ao enviar arquivo:', error);
    }
  }
}
