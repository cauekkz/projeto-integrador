import { Skeleton } from '../../../../shared/skeleton/skeleton';
import { ChangeDetectorRef, Component, HostListener, Input, OnDestroy, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { catchError, forkJoin, of } from 'rxjs';
import { Header } from '../../../../shared/header/header';
import { Footer } from '../../../../shared/footer/footer';
import { ChatWebSocketService } from '../../../../services/chat.service';
import { AuthService } from '../../../../services/auth.service';
import { StorageService } from '../../../../services/storage.service';
import { wsUrl } from '../../../../services/api';
import { ChatMessage } from '../../../../models/chat-message';

@Component({
  selector: 'app-chat-list',
  standalone: true,
  imports: [Skeleton, Header, Footer],
  templateUrl: './chat-list.html',
  styleUrl: './chat-list.css',
})
export class ChatList implements OnInit, OnDestroy {
  @Input() from = 'responsible-home';
  origem = 'responsible-home';

  // naoLidas: o backend conta as mensagens do outro que eu nao li (unreadCount); a tela mostra o numero, como no WhatsApp
  conversas: Array<{ id: string; idContato: string; nome: string; ultimaMensagem: string; hora: string; foto: string; naoLidas: number }> = [];

  // enquanto a lista esta aberta, escuta cada conversa: mensagem nova do outro atualiza a previa, a hora e o numero
  // e sobe a conversa pro topo (abrir o socket da conversa nao marca nada como lido)
  private sockets = new Map<string, WebSocket>();
  private meuId: string | null = null;
  private saiu = false;
  private pagina = 0;
  private temMais = true;
  private carregando = false;
  carregandoInicial = true;
  erroAoCarregar = false;

  constructor(
    private router: Router,
    private route: ActivatedRoute,
    private chatService: ChatWebSocketService,
    private authService: AuthService,
    private storageService: StorageService,
    private cdr: ChangeDetectorRef,
  ) {}

  ngOnInit() {
    // a origem vem da role do token, nao do ?from (que aceitava qualquer rota)
    const home = this.authService.getHomeRoute();
    this.origem = home ? home.replace('/', '') : this.route.snapshot.queryParamMap.get('from') || this.from;
    this.meuId = this.authService.getUserIdFromToken();
    this.carregarConversas();
  }

  ngOnDestroy() {
    this.saiu = true;
    this.sockets.forEach((socket) => socket.close());
    this.sockets.clear();
  }

  private escutar(chatIds: string[]) {
    const novos = chatIds.filter((id) => !this.sockets.has(id));
    if (!novos.length) return;
    this.authService.getValidToken().subscribe((token) => {
      if (!token || this.saiu) return;
      novos.forEach((chatId) => {
        if (this.sockets.has(chatId)) return;
        const socket = new WebSocket(`${wsUrl('/chat/' + chatId)}?token=${encodeURIComponent(token)}`);
        socket.onmessage = (event: MessageEvent) => {
          try {
            const dados = JSON.parse(event.data);
            if (dados?.type === 'read' || !dados?.id) return;
            this.chegouMensagem(chatId, dados as ChatMessage);
          } catch {
            // mensagem que nao e do chat: ignora
          }
        };
        // caiu: tira do mapa pra poder escutar de novo na proxima vez que a lista carregar
        socket.onclose = () => {
          if (this.sockets.get(chatId) === socket) this.sockets.delete(chatId);
        };
        this.sockets.set(chatId, socket);
      });
    });
  }

  private chegouMensagem(chatId: string, mensagem: ChatMessage) {
    const conversa = this.conversas.find((c) => c.id === chatId);
    if (!conversa) return;
    conversa.ultimaMensagem = this.previa(mensagem);
    conversa.hora = this.horaDe(mensagem.sentAt);
    if (mensagem.senderId !== this.meuId) conversa.naoLidas++;
    // a conversa com mensagem nova vai pro topo
    this.conversas = [conversa, ...this.conversas.filter((c) => c.id !== chatId)];
    this.cdr.markForCheck();
  }

  private previa(mensagem: ChatMessage): string {
    return mensagem.content?.trim() || (mensagem.attachmentType === 'IMAGE' ? 'Imagem' : 'Anexo');
  }

  private horaDe(data: string): string {
    return new Date(data).toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' });
  }

  // ate 99 aparece o numero; passou disso, "99+"
  rotuloNaoLidas(n: number): string {
    return n > 99 ? '99+' : String(n);
  }

  private carregarConversas() {
    if (this.carregando || !this.temMais) {
      return;
    }

    this.carregando = true;
    this.chatService.getChats(this.pagina).subscribe({
      next: (response) => {
        const novasConversas = (response.content ?? []).map((chat) => ({
          id: chat.id,
          idContato: chat.otherUserId,
          nome: chat.otherUserName,
          ultimaMensagem: 'Nenhuma mensagem ainda',
          hora: chat.createdAt ? new Date(chat.createdAt).toLocaleTimeString('pt-BR', {
            hour: '2-digit',
            minute: '2-digit',
          }) : '',
          foto: '/avatar-padrao.svg',
          naoLidas: chat.unreadCount ?? 0,
        }));
        this.conversas = [...this.conversas, ...novasConversas];
        this.pagina = response.number + 1;
        this.temMais = !response.last;
        this.carregando = false;
        this.carregandoInicial = false;
        this.erroAoCarregar = false;
        this.cdr.markForCheck();
        this.carregarFotos(response.content ?? []);
        this.escutar(novasConversas.map((c) => c.id));

        if (novasConversas.length) {
          forkJoin(
            novasConversas.map((conversa) =>
              this.chatService.getLatestMessage(conversa.id).pipe(
                catchError((error) => {
                  console.error(`Erro ao carregar a prévia do chat ${conversa.id}:`, error);
                  return of(null);
                }),
              ),
            ),
          ).subscribe((ultimasMensagens) => {
            novasConversas.forEach((conversa, index) => {
              const ultimaMensagem = ultimasMensagens[index];
              if (!ultimaMensagem) {
                return;
              }

              const conversaAtual = this.conversas.find((item) => item.id === conversa.id);
              if (conversaAtual) {
                conversaAtual.ultimaMensagem = this.previa(ultimaMensagem);
                conversaAtual.hora = this.horaDe(ultimaMensagem.sentAt);
              }
            });
            this.cdr.markForCheck();
          });
        }
      },
      error: (error) => {
        this.carregando = false;
        if (this.carregandoInicial) {
          this.erroAoCarregar = true;
        }
        this.carregandoInicial = false;
        this.cdr.markForCheck();
        console.error('Erro ao carregar chats:', error);
      },
    });
  }

  @HostListener('window:scroll')
  carregarMaisAoRolar() {
    const chegouAoFim = window.innerHeight + window.scrollY >= document.documentElement.scrollHeight - 200;
    if (chegouAoFim) {
      this.carregarConversas();
    }
  }

  // o chat traz so a chave da foto do contato; o link de leitura sai do storage (sem foto fica o placeholder)
  private carregarFotos(chats: Array<{ id: string; otherUserPhotoKey: string | null }>) {
    chats
      .filter((chat) => chat.otherUserPhotoKey)
      .forEach((chat) => {
        this.storageService.getDownloadUrl(chat.otherUserPhotoKey!).subscribe({
          next: (url) => {
            this.conversas = this.conversas.map((c) => (c.id === chat.id ? { ...c, foto: url } : c));
            this.cdr.markForCheck();
          },
          error: () => {},
        });
      });
  }

  abrirConversa(conversa: { id: string; idContato?: string; nome: string; foto: string }) {
    this.router.navigate(['/chat-details', conversa.id], {
      queryParams: { from: this.origem },
      state: { nomeContato: conversa.nome, fotoContato: conversa.foto, idContato: conversa.idContato },
    });
  }

  tentarNovamente() {
    this.carregandoInicial = true;
    this.erroAoCarregar = false;
    this.temMais = true;
    this.carregarConversas();
  }

  irParaVincularMotorista() {
    this.router.navigate(['/driver-code']);
  }

  voltar() {
    this.router.navigate([`/${this.origem}`]);
  }
}

export { ChatList as ChatListComponent };
