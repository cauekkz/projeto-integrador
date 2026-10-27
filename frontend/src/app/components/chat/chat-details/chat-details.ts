import { ChangeDetectorRef, Component, ElementRef, OnDestroy, OnInit, ViewChild } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { of, Subscription } from 'rxjs';
import { ChatMessage } from '../../../models/chat-message';
import { ChatWebSocketService } from '../../../services/chat.service';
import { AuthService } from '../../../services/auth.service';
import { StorageService } from '../../../services/storage.service';

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
  enviando = false;
  erroEnvio = '';
  erroCarregar = '';
  @ViewChild('messagesContainer') private messagesContainer?: ElementRef<HTMLElement>;
  private paginaMensagens = 0;
  private temMensagensAnteriores = false;
  carregandoMensagens = true; // comeca true pra nao piscar o estado vazio antes do historico chegar
  private subscription?: Subscription;

  mensagens: Array<{
    id: string;
    texto: string;
    minha: boolean;
    hora: string;
    lida: boolean;
    attachmentUrl: string | null;
    attachmentType: string | null;
    // link temporario pra abrir o anexo (o attachmentUrl guarda o fileKey)
    anexoUrl: string | null;
    sentAt: string;
  }> = [];

  private linksAnexo = new Map<string, string>();
  private anexosCarregando = new Set<string>();

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private chatService: ChatWebSocketService,
    private authService: AuthService,
    private storageService: StorageService,
    private cdr: ChangeDetectorRef,
  ) {}

  ngOnInit() {
    const home = this.authService.getHomeRoute();
    this.origem = home ? home.replace('/', '') : this.route.snapshot.queryParamMap.get('from') || 'responsible-home';
    this.chatId = this.route.snapshot.paramMap.get('id') || '';

    // history.state continua la depois do F5, getCurrentNavigation() nao
    const nav = this.router.getCurrentNavigation();
    const state = (nav?.extras.state ?? history.state) as { nomeContato?: string; fotoContato?: string } | undefined;
    this.nomeContato = state?.nomeContato || this.nomeContato;
    this.fotoContato = state?.fotoContato || this.fotoContato;

    if (!this.chatId) {
      return;
    }

    if (!state?.nomeContato) {
      this.carregarNomeContato();
    }

    // o senderId das mensagens e o id do usuario: compara com o /me pra saber quais sao "minhas"
    this.authService.getMe().subscribe({
      next: (me) => {
        this.usuarioLogadoId = me?.id ?? '';
        this.iniciarConversa();
      },
      error: (error) => {
        console.error('Erro ao identificar o usuário logado:', error);
        this.iniciarConversa();
      },
    });
  }

  private iniciarConversa() {
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
      this.rolarParaFim();
    });
  }

  // quem abre pelo driver-code (ou da F5 sem state) nao tem o nome: pega da lista do proprio usuario
  private carregarNomeContato() {
    this.chatService.getChats(0, 50).subscribe({
      next: (page) => {
        const chat = (page.content ?? []).find((item) => item.id === this.chatId);
        if (chat?.otherUserName) {
          this.nomeContato = chat.otherUserName;
          this.cdr.markForCheck();
        }
      },
      error: (error) => console.error('Erro ao carregar dados do contato:', error),
    });
  }

  private rolarParaFim() {
    requestAnimationFrame(() => {
      const container = this.messagesContainer?.nativeElement;
      if (container) {
        container.scrollTop = container.scrollHeight;
      }
    });
  }

  ngOnDestroy() {
    this.chatService.disconnect();
    this.subscription?.unsubscribe();
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
            this.erroCarregar = '';
            this.rolarParaFim();
          },
          error: (error) => {
            this.carregandoMensagens = false;
            this.erroCarregar = 'Não foi possível carregar o histórico da conversa.';
            this.cdr.markForCheck();
            console.error('Erro ao carregar mensagens recentes:', error);
          },
        });
      },
      error: (error) => {
        this.carregandoMensagens = false;
        // validateChatAccess joga IllegalArgumentException -> o handler global responde 500
        this.erroCarregar = error?.status >= 500
          ? 'Você não tem acesso a esta conversa ou ela não existe.'
          : 'Não foi possível carregar o histórico da conversa.';
        this.cdr.markForCheck();
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
    this.cdr.markForCheck();
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
      anexoUrl: this.linkDoAnexo(message.attachmentUrl),
      sentAt: message.sentAt,
    };
  }

  // anexo antigo ja era um link; os novos sao fileKey e pedem um link de leitura (vale 15 min)
  private linkDoAnexo(attachmentUrl: string | null): string | null {
    if (!attachmentUrl) return null;
    if (/^https?:\/\//.test(attachmentUrl)) return attachmentUrl;

    const link = this.linksAnexo.get(attachmentUrl);
    if (link) return link;

    if (!this.anexosCarregando.has(attachmentUrl)) {
      this.anexosCarregando.add(attachmentUrl);
      this.storageService.getDownloadUrl(attachmentUrl).subscribe({
        next: (url) => {
          this.linksAnexo.set(attachmentUrl, url);
          this.mensagens = this.mensagens.map((item) =>
            item.attachmentUrl === attachmentUrl ? { ...item, anexoUrl: url } : item,
          );
          this.cdr.markForCheck();
        },
        error: (error) => console.error('Erro ao carregar anexo:', error),
        complete: () => this.anexosCarregando.delete(attachmentUrl),
      });
    }
    return null;
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

    if (!text || !this.chatId || this.enviando) {
      return;
    }

    this.enviando = true;
    this.erroEnvio = '';
    this.chatService.sendMessage(this.chatId, text).subscribe({
      next: (message) => {
        this.enviando = false;
        this.adicionarMensagens([message]);
        this.mensagem = '';
        this.rolarParaFim();
      },
      error: (error) => {
        // mantem o texto digitado pra pessoa tentar de novo
        this.enviando = false;
        this.erroEnvio = 'Não foi possível enviar a mensagem. Tente novamente.';
        this.cdr.markForCheck();
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
      const fileKey = await this.chatService.uploadAttachment(file);
      const attachmentType = file.type.startsWith('image/') ? 'IMAGE' : 'OTHER';
      this.chatService.sendAttachmentMessage(this.chatId, fileKey, attachmentType).subscribe({
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
