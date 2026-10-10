import { Component, HostListener, Input, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { catchError, forkJoin, of } from 'rxjs';
import { Header } from '../../../shared/header/header';
import { Footer } from '../../../shared/footer/footer';
import { ChatWebSocketService } from '../../../services/chat.service';

@Component({
  selector: 'app-chat-list',
  standalone: true,
  imports: [Header, Footer],
  templateUrl: './chat-list.html',
  styleUrl: './chat-list.css',
})
export class ChatList implements OnInit {
  @Input() from = 'responsible-home';
  origem = 'responsible-home';

  conversas: Array<{ id: string; nome: string; ultimaMensagem: string; hora: string; foto: string }> = [];
  private pagina = 0;
  private temMais = true;
  private carregando = false;

  constructor(
    private router: Router,
    private route: ActivatedRoute,
    private chatService: ChatWebSocketService,
  ) {}

  ngOnInit() {
    this.origem = this.route.snapshot.queryParamMap.get('from') || this.from;
    this.carregarConversas();
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
          nome: chat.otherUserName,
          ultimaMensagem: 'Converse com o responsável ou motorista',
          hora: chat.createdAt ? new Date(chat.createdAt).toLocaleTimeString('pt-BR', {
            hour: '2-digit',
            minute: '2-digit',
          }) : '',
          foto: '/testee.jpg',
        }));
        this.conversas = [...this.conversas, ...novasConversas];
        this.pagina = response.number + 1;
        this.temMais = !response.last;
        this.carregando = false;

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

              const preview = ultimaMensagem.content?.trim()
                || (ultimaMensagem.attachmentType === 'IMAGE' ? 'Imagem' : 'Anexo');
              const conversaAtual = this.conversas.find((item) => item.id === conversa.id);
              if (conversaAtual) {
                conversaAtual.ultimaMensagem = preview;
                conversaAtual.hora = new Date(ultimaMensagem.sentAt).toLocaleTimeString('pt-BR', {
                  hour: '2-digit',
                  minute: '2-digit',
                });
              }
            });
          });
        }
      },
      error: (error) => {
        this.carregando = false;
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

  abrirConversa(conversa: { id: string; nome: string; foto: string }) {
    this.router.navigate(['/chat-details', conversa.id], {
      queryParams: { from: this.origem },
      state: { nomeContato: conversa.nome, fotoContato: conversa.foto },
    });
  }

  voltar() {
    this.router.navigate([`/${this.origem}`]);
  }
}

export { ChatList as ChatListComponent };
