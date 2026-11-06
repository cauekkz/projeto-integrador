import { PopUpService } from '../../../shared/pop-up';

import {
  ChangeDetectorRef,
  Component,
  ElementRef,
  OnDestroy,
  OnInit,
  ViewChild,
  inject,
} from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { DomSanitizer, SafeResourceUrl } from '@angular/platform-browser';
import { of, Subscription } from 'rxjs';
import { ChatMessage } from '../../../models/chat-message';
import { ChatWebSocketService } from '../../../services/chat.service';
import { AuthService } from '../../../services/auth.service';
import { StorageService } from '../../../services/storage.service';
import { NotificationService } from '../../../services/notification.service';
import { DecisaoContrato } from '../contract-proposal-card/contract-proposal-card';
import { ContractProposalCard } from '../contract-proposal-card/contract-proposal-card';

@Component({
  selector: 'app-chat-details',
  standalone: true,
  imports: [CommonModule, FormsModule, ContractProposalCard],
  templateUrl: './chat-details.html',
  styleUrl: './chat-details.css',
})
export class ChatDetails implements OnInit, OnDestroy {
  private notificacoes = inject(NotificationService);
  chatId = '';
  mensagem = '';
  nomeContato = 'Contato';
  // id do outro (perfil dele ao tocar no nome)
  idContato: string | null = null;
  fotoContato = '/testee.jpg';
  origem = 'responsible-home';
  usuarioLogadoId = '';
  enviando = false;
  erroEnvio = '';
  erroCarregar = '';

  // Controla a exibição do menu de anexos
  showAttachmentMenu = false;

  // só o motorista monta proposta de contrato (a rota e o backend também barram o responsável)
  podeCriarContrato = false;
  // responsável aceita ou recusa a proposta pelo card
  podeDecidirContrato = false;

  // contractId -> status decidido pelas mensagens APPROVAL/REJECTION que chegaram no chat
  decisoesContrato = new Map<string, DecisaoContrato>();

  @ViewChild('messagesContainer')
  private messagesContainer?: ElementRef<HTMLElement>;

  private paginaMensagens = 0;
  private temMensagensAnteriores = false;

  carregandoMensagens = true;
  private subscription?: Subscription;
  private leituraSubscription?: Subscription;

  mensagens: Array<{
    id: string;
    texto: string;
    minha: boolean;
    hora: string;
    lida: boolean;
    attachmentUrl: string | null;
    attachmentType: string | null;
    anexoUrl: string | null;
    // como o anexo aparece no balao (o backend so diferencia IMAGE; video e PDF chegam como OTHER)
    tipoAnexo: TipoAnexo | null;
    sentAt: string;
    // REGULAR_CHAT, PROPOSAL, APPROVAL, REJECTION...; o contractId vem no payload das mensagens de contrato
    tipo: string;
    contractId: string | null;
  }> = [];

  private linksAnexo = new Map<string, string>();
  private anexosCarregando = new Set<string>();
  // o iframe do PDF precisa de SafeResourceUrl; guardado pra nao recarregar o PDF a cada deteccao de mudanca
  private previasPdf = new Map<string, SafeResourceUrl>();
  private sanitizer = inject(DomSanitizer);
  private popUpService = inject(PopUpService);

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
    const roles = this.authService.getRolesFromToken();
    this.podeCriarContrato = roles.includes('ROLE_DRIVER');
    this.podeDecidirContrato = roles.includes('ROLE_RESPONSIBLE');

    this.origem = home
      ? home.replace('/', '')
      : this.route.snapshot.queryParamMap.get('from') || 'responsible-home';

    this.chatId = this.route.snapshot.paramMap.get('id') || '';

    const nav = this.router.getCurrentNavigation();

    const state = (
      nav?.extras.state ?? history.state
    ) as {
      nomeContato?: string;
      fotoContato?: string;
      idContato?: string;
    } | undefined;

    this.nomeContato = state?.nomeContato || this.nomeContato;
    this.fotoContato = state?.fotoContato || this.fotoContato;
    this.idContato = state?.idContato || null;

    if (!this.chatId) {
      return;
    }
    // abriu a conversa: a notificacao de mensagem dela fica lida
    this.notificacoes.marcarConversaVista(this.chatId);

    // entrou sem passar pela lista (ou a foto ainda nao tinha carregado la): busca nome e foto do contato
    // sem o id do contato o toque no nome nao abre o perfil: busca tambem nesse caso
    if (!state?.nomeContato || this.fotoContato === '/testee.jpg' || !this.idContato) {
      this.carregarNomeContato();
    }

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

  // Abre ou fecha o menu de anexos
  toggleAttachmentMenu(): void {
    this.showAttachmentMenu = !this.showAttachmentMenu;
  }

  // Fecha o menu e abre o fluxo de criação de contrato deste chat
  criarContrato(): void {
    this.showAttachmentMenu = false;

    if (!this.podeCriarContrato || !this.chatId) {
      return;
    }

    this.router.navigate(['/driver-contract', this.chatId]);
  }

  // Fecha o menu e abre o seletor de arquivos
  enviarMidia(input: HTMLInputElement): void {
    this.showAttachmentMenu = false;
    input.click();
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

      // chegou do outro com a conversa aberta: ja foi lida
      if (message.senderId !== this.usuarioLogadoId) {
        this.marcarComoLida();
      }
    });

    // o outro leu: todas as minhas mensagens viram ✓✓
    this.leituraSubscription = this.chatService.reads$.subscribe((evento) => {
      if (evento.chatId !== this.chatId || evento.userId === this.usuarioLogadoId) {
        return;
      }
      this.mensagens = this.mensagens.map((item) => (item.minha && !item.lida ? { ...item, lida: true } : item));
      this.cdr.markForCheck();
    });
  }

  // PUT /chats/{id}/read: zera o unreadCount e avisa o outro (evento read no socket dele)
  private marcarComoLida() {
    if (!this.chatId) return;
    this.chatService.markAsRead(this.chatId).subscribe({ error: () => {} });
  }

  // responsavel abre o perfil do motorista; motorista abre o do responsavel
  abrirPerfilContato() {
    if (!this.idContato) return;
    const souMotorista = this.authService.getRolesFromToken().includes('ROLE_DRIVER');
    this.router.navigate([souMotorista ? '/responsible-profile' : '/driver-profile', this.idContato]);
  }

  private carregarNomeContato() {
    this.chatService.getChats(0, 50).subscribe({
      next: (page) => {
        const chat = (page.content ?? []).find(
          (item) => item.id === this.chatId
        );

        if (chat?.otherUserId) this.idContato = chat.otherUserId;
        if (chat?.otherUserName) {
          this.nomeContato = chat.otherUserName;
          this.cdr.markForCheck();
        }
        if (chat?.otherUserPhotoKey) {
          this.storageService.getDownloadUrl(chat.otherUserPhotoKey).subscribe({
            next: (url) => {
              this.fotoContato = url;
              this.cdr.markForCheck();
            },
            error: () => {},
          });
        }
      },
      error: (error) =>
        console.error('Erro ao carregar dados do contato:', error),
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
    // o que chegou com a conversa aberta tambem ja foi visto
    if (this.chatId) this.notificacoes.marcarConversaVista(this.chatId);
    this.chatService.disconnect();
    this.subscription?.unsubscribe();
    this.leituraSubscription?.unsubscribe();
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
            this.marcarComoLida();
          },
          error: (error) => {
            this.carregandoMensagens = false;
            this.erroCarregar =
              'Não foi possível carregar o histórico da conversa.';

            this.cdr.markForCheck();

            console.error('Erro ao carregar mensagens recentes:', error);
          },
        });
      },
      error: (error) => {
        this.carregandoMensagens = false;

        // chat de outra pessoa: o backend responde 403 (o pop-up global ja avisou) e volta pra lista
        if (error?.status === 403) {
          this.voltar();
          return;
        }

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

    if (
      container.scrollTop > 60 ||
      this.carregandoMensagens ||
      !this.temMensagensAnteriores
    ) {
      return;
    }

    const alturaAnterior = container.scrollHeight;
    const posicaoAnterior = container.scrollTop;

    this.carregandoMensagens = true;

    this.chatService
      .getMessages(this.chatId, this.paginaMensagens - 1)
      .subscribe({
        next: (page) => {
          this.paginaMensagens = page.number;
          this.temMensagensAnteriores = page.number > 0;

          this.adicionarMensagens(page.content ?? []);

          this.carregandoMensagens = false;

          requestAnimationFrame(() => {
            container.scrollTop =
              posicaoAnterior + container.scrollHeight - alturaAnterior;
          });
        },
        error: (error) => {
          this.carregandoMensagens = false;

          console.error('Erro ao carregar mensagens anteriores:', error);
        },
      });
  }

  private adicionarMensagens(novasMensagens: ChatMessage[]) {
    const mensagensPorId = new Map(
      this.mensagens.map((message) => [message.id, message])
    );

    for (const message of novasMensagens) {
      mensagensPorId.set(message.id, this.mapMessage(message));
    }

    this.mensagens = Array.from(mensagensPorId.values()).sort(
      (a, b) =>
        new Date(a.sentAt).getTime() - new Date(b.sentAt).getTime()
    );

    this.decisoesContrato = new Map();
    for (const message of this.mensagens) {
      if (!message.contractId) continue;
      if (message.tipo === 'APPROVAL') this.decisoesContrato.set(message.contractId, 'ACTIVE');
      // o backend usa REJECTION tanto pra recusa quanto pra retirada do motorista ("Proposta Retirada")
      if (message.tipo === 'REJECTION') {
        this.decisoesContrato.set(message.contractId, message.texto === 'Proposta Retirada' ? 'WITHDRAWN' : 'CANCELED');
      }
    }

    this.cdr.markForCheck();
  }

  private mapMessage(message: ChatMessage) {
    return {
      id: message.id,
      texto: message.content || '',
      minha: message.senderId === this.usuarioLogadoId,
      hora: this.formatHour(message.sentAt),
      // ✓✓ so nas minhas, quando o outro ja leu
      lida: message.senderId === this.usuarioLogadoId && !!message.readAt,
      attachmentUrl: message.attachmentUrl,
      attachmentType: message.attachmentType,
      anexoUrl: this.linkDoAnexo(message.attachmentUrl),
      tipoAnexo: tipoDoAnexo(message.attachmentType, message.attachmentUrl),
      sentAt: message.sentAt,
      tipo: message.messageType || 'REGULAR_CHAT',
      contractId: this.contractIdDoPayload(message.payload),
    };
  }

  // o payload é um texto com JSON dentro: {"contractId": "..."}
  private contractIdDoPayload(payload: string | null): string | null {
    if (!payload) return null;

    try {
      const dados = JSON.parse(payload);
      return typeof dados?.contractId === 'string' ? dados.contractId : null;
    } catch {
      return null;
    }
  }

  private linkDoAnexo(attachmentUrl: string | null): string | null {
    if (!attachmentUrl) return null;

    if (/^https?:\/\//.test(attachmentUrl)) {
      return attachmentUrl;
    }

    const link = this.linksAnexo.get(attachmentUrl);

    if (link) return link;

    if (!this.anexosCarregando.has(attachmentUrl)) {
      this.anexosCarregando.add(attachmentUrl);

      this.storageService.getDownloadUrl(attachmentUrl).subscribe({
        next: (url) => {
          this.linksAnexo.set(attachmentUrl, url);

          this.mensagens = this.mensagens.map((item) =>
            item.attachmentUrl === attachmentUrl
              ? { ...item, anexoUrl: url }
              : item
          );

          this.cdr.markForCheck();
        },
        error: (error) =>
          console.error('Erro ao carregar anexo:', error),
        complete: () => this.anexosCarregando.delete(attachmentUrl),
      });
    }

    return null;
  }

  // link do storage vem do proprio backend (URL assinada); sem a barra/miniaturas do leitor de PDF
  previaPdf(url: string): SafeResourceUrl {
    let previa = this.previasPdf.get(url);
    if (!previa) {
      previa = this.sanitizer.bypassSecurityTrustResourceUrl(`${url}#toolbar=0&navpanes=0&view=FitH`);
      this.previasPdf.set(url, previa);
    }
    return previa;
  }

  private formatHour(value: string) {
    return new Date(value).toLocaleTimeString('pt-BR', {
      hour: '2-digit',
      minute: '2-digit',
    });
  }

  voltar() {
    this.router.navigate(['/chat'], {
      queryParams: { from: this.origem },
    });
  }

  enviar(campo?: HTMLInputElement) {
    const text = this.mensagem.trim();

    // mantem o teclado aberto depois de enviar; ele so fecha quando a pessoa toca fora
    campo?.focus();

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
        this.enviando = false;
        this.erroEnvio =
          'Não foi possível enviar a mensagem. Tente novamente.';

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

    // mesmo limite do backend: confere antes de subir pra nao gastar o upload
    if (file.size > ANEXO_TAMANHO_MAXIMO) {
      this.popUpService.show('O arquivo pode ter no máximo 10 MB', 'error', 'Dados inválidos');
      return;
    }

    try {
      const fileKey = await this.chatService.uploadAttachment(file);

      const attachmentType = file.type.startsWith('image/')
        ? 'IMAGE'
        : 'OTHER';

      this.chatService
        .sendAttachmentMessage(this.chatId, fileKey, attachmentType)
        .subscribe({
          next: (message) => {
            this.adicionarMensagens([message]);
          },
          error: (error) => {
            console.error(
              'O arquivo foi enviado, mas não foi possível publicá-lo no chat:',
              error
            );
          },
        });
    } catch (error) {
      console.error('Erro ao enviar arquivo:', error);
    }
  }
}

// o backend recusa anexo maior que 10 MB (400 "O arquivo pode ter no máximo 10 MB")
const ANEXO_TAMANHO_MAXIMO = 10 * 1024 * 1024;

type TipoAnexo = 'imagem' | 'video' | 'pdf' | 'arquivo';

const EXTENSOES_VIDEO = ['mp4', 'webm', 'mov', 'm4v', 'ogv', '3gp'];

// a chave do arquivo guarda a extensao original (StorageService do backend: uuid + .ext)
function tipoDoAnexo(attachmentType: string | null, attachmentUrl: string | null): TipoAnexo | null {
  if (!attachmentUrl) return null;
  if (attachmentType === 'IMAGE') return 'imagem';

  const extensao = attachmentUrl.split(/[?#]/)[0].split('.').pop()?.toLowerCase() ?? '';
  if (EXTENSOES_VIDEO.includes(extensao)) return 'video';
  if (extensao === 'pdf' || attachmentType === 'CONTRACT_DOCUMENT') return 'pdf';
  return 'arquivo';
}
