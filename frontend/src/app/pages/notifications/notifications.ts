import { Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Location } from '@angular/common';
import { Router } from '@angular/router';
import { Footer } from '../../shared/footer/footer';
import { AuthService } from '../../services/auth.service';
import { Notificacao, NotificacaoMensagem, NotificationService } from '../../services/notification.service';

type Aba = 'todas' | 'mensagens' | 'avisos';

// item da lista: notificacao do backend ou mensagem de conversa, no mesmo formato
interface ItemNotificacao {
  chave: string;
  origem: 'mensagem' | 'aviso';
  icone: string;
  cor: 'azul' | 'verde' | 'vermelho' | 'amarelo' | 'cinza';
  titulo: string;
  texto: string;
  data: Date;
  lida: boolean;
  backend?: Notificacao;
  chatId?: string;
}

// icone e cor de cada NotificationType do backend
const VISUAL: Record<string, Pick<ItemNotificacao, 'icone' | 'cor'>> = {
  CONTRACT_PROPOSED: { icone: 'bi-file-earmark-text-fill', cor: 'azul' },
  CONTRACT_ACCEPTED: { icone: 'bi-check-circle-fill', cor: 'verde' },
  CONTRACT_REJECTED: { icone: 'bi-x-circle-fill', cor: 'vermelho' },
  TRIP_STARTED: { icone: 'bi-truck-front-fill', cor: 'azul' },
  VAN_ON_THE_WAY: { icone: 'bi-geo-alt-fill', cor: 'azul' },
  STUDENT_BOARDED: { icone: 'bi-person-check-fill', cor: 'verde' },
  STUDENT_DELIVERED: { icone: 'bi-house-check-fill', cor: 'verde' },
  STUDENT_ABSENT: { icone: 'bi-person-x-fill', cor: 'amarelo' },
  ABSENCE_REPORTED: { icone: 'bi-calendar-x-fill', cor: 'amarelo' },
  PAYMENT: { icone: 'bi-cash-coin', cor: 'verde' },
  OCCURRENCE: { icone: 'bi-exclamation-triangle-fill', cor: 'amarelo' },
};

const TIPOS_VIAGEM = ['TRIP_STARTED', 'VAN_ON_THE_WAY', 'STUDENT_BOARDED', 'STUDENT_DELIVERED', 'STUDENT_ABSENT'];

@Component({
  selector: 'app-notifications',
  standalone: true,
  imports: [Footer],
  templateUrl: './notifications.html',
  styleUrl: './notifications.css',
})
export class Notifications implements OnInit {
  protected notificacoes = inject(NotificationService);
  private authService = inject(AuthService);
  private router = inject(Router);
  private location = inject(Location);
  private destroyRef = inject(DestroyRef);

  readonly tipoUsuario = this.authService.getRolesFromToken().includes('ROLE_DRIVER') ? 'motorista' : 'responsavel';

  readonly abaAtiva = signal<Aba>('todas');
  readonly carregando = signal(true);
  readonly erro = signal(false);
  readonly temMais = signal(false);
  readonly carregandoMais = signal(false);

  private readonly avisos = signal<Notificacao[]>([]);
  private readonly mensagens = signal<NotificacaoMensagem[]>([]);
  private pagina = 0;

  readonly itens = computed<ItemNotificacao[]>(() => {
    const avisos = this.avisos().map((n) => this.deBackend(n));
    const mensagens = this.mensagens().map((m) => this.deMensagem(m));
    const todos = [...avisos, ...mensagens].sort((a, b) => b.data.getTime() - a.data.getTime());
    const aba = this.abaAtiva();
    if (aba === 'mensagens') return todos.filter((i) => i.origem === 'mensagem');
    if (aba === 'avisos') return todos.filter((i) => i.origem === 'aviso');
    return todos;
  });

  readonly temNaoLidas = computed(
    () => this.avisos().some((n) => !n.read) || this.mensagens().some((m) => !m.lida),
  );

  ngOnInit() {
    this.notificacoes.iniciar();
    this.carregar();

    // notificacao nova com a tela aberta entra no topo
    this.notificacoes.chegou.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((n) => {
      this.avisos.update((lista) => [n, ...lista.filter((a) => a.id !== n.id)]);
    });
  }

  carregar() {
    this.carregando.set(true);
    this.erro.set(false);
    this.pagina = 0;

    this.notificacoes.carregarMensagens().then((mensagens) => this.mensagens.set(mensagens));
    this.notificacoes.listar(0).subscribe({
      next: (pagina) => {
        this.avisos.set(pagina.content ?? []);
        this.temMais.set(!pagina.last);
        this.carregando.set(false);
      },
      error: (err) => {
        console.error('Erro ao carregar notificações:', err);
        this.erro.set(true);
        this.carregando.set(false);
      },
    });
  }

  carregarMais() {
    if (this.carregandoMais()) return;
    this.carregandoMais.set(true);
    this.notificacoes.listar(this.pagina + 1).subscribe({
      next: (pagina) => {
        this.pagina++;
        this.avisos.update((lista) => [...lista, ...(pagina.content ?? [])]);
        this.temMais.set(!pagina.last);
        this.carregandoMais.set(false);
      },
      error: () => this.carregandoMais.set(false),
    });
  }

  trocarAba(aba: Aba) {
    this.abaAtiva.set(aba);
  }

  marcarTodas() {
    this.notificacoes.marcarTodasLidas();
    this.avisos.update((lista) => lista.map((n) => ({ ...n, read: true })));
    this.mensagens.update((lista) => lista.map((m) => ({ ...m, lida: true })));
  }

  abrir(item: ItemNotificacao) {
    if (item.backend) {
      this.notificacoes.marcarLida(item.backend);
      this.avisos.update((lista) => lista.map((n) => (n.id === item.backend!.id ? { ...n, read: true } : n)));
    }

    const destino = this.destino(item);
    if (destino) this.router.navigate(destino.rota, { queryParams: destino.query });
  }

  voltar() {
    this.location.back();
  }

  // pra onde cada notificacao leva
  private destino(item: ItemNotificacao): { rota: string[]; query?: Record<string, string> } | null {
    const from = this.tipoUsuario === 'motorista' ? 'driver-home' : 'responsible-home';
    if (item.chatId) return { rota: ['/chat-details', item.chatId], query: { from } };

    const n = item.backend;
    if (!n) return null;
    const payload = n.payload ?? {};
    const contractId = typeof payload['contractId'] === 'string' ? payload['contractId'] : null;
    const chatId = typeof payload['chatId'] === 'string' ? payload['chatId'] : null;

    if (contractId) return { rota: ['/contract', contractId], query: chatId ? { chatId } : undefined };
    if (TIPOS_VIAGEM.includes(n.type) && this.tipoUsuario === 'responsavel') return { rota: ['/responsible-route'] };
    if (chatId) return { rota: ['/chat-details', chatId], query: { from } };
    return null;
  }

  private deBackend(n: Notificacao): ItemNotificacao {
    return {
      chave: 'n-' + n.id,
      origem: 'aviso',
      ...(VISUAL[n.type] ?? { icone: 'bi-bell-fill', cor: 'cinza' }),
      titulo: n.title,
      texto: n.message,
      data: new Date(n.createdAt),
      lida: n.read,
      backend: n,
    };
  }

  private deMensagem(m: NotificacaoMensagem): ItemNotificacao {
    return {
      chave: 'm-' + m.chatId,
      origem: 'mensagem',
      icone: 'bi-chat-dots-fill',
      cor: 'azul',
      titulo: `Nova mensagem de ${m.nome}`,
      texto: m.previa,
      data: new Date(m.enviadaEm),
      lida: m.lida,
      chatId: m.chatId,
    };
  }

  tempo(data: Date): string {
    const minutos = Math.floor((Date.now() - data.getTime()) / 60000);
    if (Number.isNaN(minutos)) return '';
    if (minutos < 1) return 'agora';
    if (minutos < 60) return `há ${minutos} min`;
    const horas = Math.floor(minutos / 60);
    if (horas < 24) return `há ${horas} h`;
    if (horas < 48) return 'ontem';
    return data.toLocaleDateString('pt-BR', { day: '2-digit', month: '2-digit' });
  }
}
