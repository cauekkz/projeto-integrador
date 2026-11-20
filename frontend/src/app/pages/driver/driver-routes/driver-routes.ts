import { Skeleton } from '../../../shared/skeleton/skeleton';
import { Component, DestroyRef, ElementRef, OnInit, computed, effect, inject, signal, viewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, NavigationCancel, NavigationEnd, NavigationError, NavigationStart, Router } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ProfileSelect } from '../../../components/common/profile-select/profile-select';
import { AuthService } from '../../../services/auth.service';
import { NotificationService } from '../../../services/notification.service';
import { UserMe } from '../../../models/user.model';
import { Footer } from '../../../shared/footer/footer';
import { PopUpService } from '../../../shared/pop-up';
import { RouteService } from '../../../services/route.service';
import { TripService } from '../../../services/trip.service';
import { routeWithStops, ruaENumero } from '../../../services/dto/route/route.dto';

import { BotaoCarregandoDirective } from '../../../shared/botao-carregando/botao-carregando.directive';
import { CenaChegada } from '../../../components/driver/cena-chegada/cena-chegada';
import { CenaEmRota } from '../../../components/driver/cena-em-rota/cena-em-rota';
import { CeuEmRota, PeriodoDoDia } from '../../../components/driver/ceu-em-rota/ceu-em-rota';
import { GlisseVidro } from '../../../shared/glisse-vidro/glisse-vidro';
const TAMANHO_PAGINA = 10;

// home do motorista (/driver-home): a lista das rotas (Ativas/Todas), sem rotas e a folha "Nova rota" (Figma "Rotas · Motorista"),
// com o topo de sempre. A escolha da viagem do dia fica na aba do meio (/driver-routes, pages/driver-home)
@Component({
  selector: 'app-driver-routes',
  standalone: true,
  imports: [BotaoCarregandoDirective, Skeleton, Footer, FormsModule, ProfileSelect, CenaEmRota, CenaChegada, CeuEmRota, GlisseVidro],
  templateUrl: './driver-routes.html',
  styleUrls: ['../../../shared/folha.css', '../../../shared/rota.css', './driver-routes.css'],
})
export class DriverRoutes implements OnInit {
  private routeService = inject(RouteService);
  private tripService = inject(TripService);
  private popUpService = inject(PopUpService);
  private router = inject(Router);
  private route = inject(ActivatedRoute);
  private authService = inject(AuthService);
  // badge do sino: nao lidas de verdade
  protected notificacoes = inject(NotificationService);

  protected mostrarPerfil = signal(false);
  protected usuarioLogado = signal<UserMe | null>(null);
  private destroyRef = inject(DestroyRef);

  // o ceu do card "Em rota agora" segue a hora: dia ate 16h, por do sol das 16h01 as 17h59 e noite das 18h as 5h59.
  // de noite os postes e o farol acendem. Confere o relogio a cada minuto e quando a aba volta a aparecer
  private agora = signal(new Date());
  protected periodo = computed(() => DriverRoutes.periodoDoDia(this.agora()));
  protected noite = computed(() => this.periodo() === 'noite');
  // 0 em 16h01 e 1 em 17h59: o quanto o sol ja desceu
  protected progressoTarde = computed(() => {
    const a = this.agora();
    const minuto = a.getHours() * 60 + a.getMinutes();
    return Math.min(1, Math.max(0, (minuto - 961) / (1079 - 961)));
  });
  private conferirHora = () => this.agora.set(new Date());
  private relogio = setInterval(this.conferirHora, 60_000);
  private ouvirAba = (document.addEventListener('visibilitychange', this.conferirHora), window.addEventListener('focus', this.conferirHora));
  private pararRelogio = this.destroyRef.onDestroy(() => {
    clearInterval(this.relogio);
    document.removeEventListener('visibilitychange', this.conferirHora);
    window.removeEventListener('focus', this.conferirHora);
  });

  private static periodoDoDia(agora: Date): PeriodoDoDia {
    const hora = agora.getHours();
    if (hora >= 18 || hora < 6) return 'noite';
    return hora * 60 + agora.getMinutes() <= 16 * 60 ? 'dia' : 'por-do-sol';
  }

  // "Ativas" = so as rotas em que ele esta hoje; "Todas" inclui as que ele deixou
  protected ativas = signal(true);
  protected readonly ruaENumero = ruaENumero;

  // paradas e alunos (diferentes) das rotas da lista
  protected resumo = computed(() => {
    const rotas = this.rotas();
    const alunos = new Set(rotas.flatMap((r) => r.stops.flatMap((s) => s.students.map((a) => a.studentId))));
    return { paradas: rotas.reduce((t, r) => t + r.stops.length, 0), alunos: alunos.size };
  });
  // viagem rodando agora: aparece o card "Em rota" no topo, com o botao pro mapa
  protected emRota = signal<{ routeId: string; nome: string } | null>(null);
  // acabou de finalizar (vem da tela da viagem): o card mostra a van andando, depois a chegada na escola/casa, e some
  protected chegada = signal<'escola' | 'casa' | null>(null);
  protected chegou = signal(false);
  protected rotas = signal<routeWithStops[]>([]);
  protected carregando = signal(true);
  protected carregandoMais = signal(false);
  protected erroCarregar = signal(false);
  protected temMais = signal(false);
  private pagina = 0;
  private pedidoAtual = 0;

  // folha "Nova rota"
  protected criando = signal(false);
  protected nome = '';
  protected enviando = signal(false);

  private fimDaLista = viewChild<ElementRef<HTMLElement>>('fimDaLista');
  private observador: IntersectionObserver | null = null;

  constructor() {
    effect(() => {
      const fim = this.fimDaLista()?.nativeElement;
      this.observador?.disconnect();
      if (!fim || typeof IntersectionObserver === 'undefined') return;
      this.observador = new IntersectionObserver((entradas) => {
        if (entradas.some((e) => e.isIntersecting)) this.carregarMais();
      });
      this.observador.observe(fim);
    });
    this.destroyRef.onDestroy(() => this.observador?.disconnect());
  }

  ngOnInit() {
    this.notificacoes.iniciar();
    // voltando de "codigo de conversa"/"vincular motorista" reabre o painel do perfil
    this.mostrarPerfil.set(this.route.snapshot.queryParamMap.get('perfil') === 'aberto');
    this.authService.getMe().subscribe({
      next: (user) => this.usuarioLogado.set(user),
      error: (err) => console.error('Erro ao buscar motorista:', err),
    });
    this.carregar();
    const chegada = (history.state as { chegada?: { routeId: string; nome: string; destino: 'escola' | 'casa' } })?.chegada;
    if (chegada) {
      // nao repete a animacao ao recarregar a pagina
      history.replaceState({ ...history.state, chegada: null }, '');
      this.tocarChegada(chegada);
    }
    this.tripService.viagemAberta().subscribe((v) => {
      if (this.chegada()) this.depoisDaChegada = v;
      else this.emRota.set(v);
    });
  }

  // a viagem que ainda estiver aberta (se tiver) aparece no card quando a chegada terminar
  private depoisDaChegada: { routeId: string; nome: string } | null = null;

  private tocarChegada(c: { routeId: string; nome: string; destino: 'escola' | 'casa' }) {
    this.emRota.set({ routeId: c.routeId, nome: c.nome });
    this.chegada.set(c.destino);
    // ~3s da van andando normal, ~4,6s chegando e freando, ~2,4s parada com o check; depois o card some
    const t1 = setTimeout(() => this.chegou.set(true), 3000);
    const t2 = setTimeout(() => {
      this.chegada.set(null);
      this.chegou.set(false);
      this.emRota.set(this.depoisDaChegada);
    }, 10000);
    this.destroyRef.onDestroy(() => {
      clearTimeout(t1);
      clearTimeout(t2);
    });
  }

  // o card "Em rota" so faz a animacao de sumir (1,2s) quando a viagem e finalizada; indo pra outra tela
  // (Abrir mapa, rodape...) ele sai junto com a tela, sem segurar a troca
  protected saindoDaTela = signal(false);
  private ouvirNavegacao = this.router.events
    .pipe(takeUntilDestroyed())
    // se a navegacao nao sair daqui (cancelou, so mudou a query...), a tela continua: volta a animar o fim da viagem
    .subscribe((e) => {
      if (e instanceof NavigationStart) this.saindoDaTela.set(true);
      else if (e instanceof NavigationEnd || e instanceof NavigationCancel || e instanceof NavigationError) this.saindoDaTela.set(false);
    });

  abrirViagem() {
    const v = this.emRota();
    if (v) this.router.navigate(['/driver-route', v.routeId], { queryParams: { continuar: 1 } });
  }

  trocarFiltro(ativas: boolean) {
    if (ativas === this.ativas()) return;
    this.ativas.set(ativas);
    this.carregar();
  }

  carregar() {
    this.pagina = 0;
    this.rotas.set([]);
    this.carregando.set(true);
    this.erroCarregar.set(false);
    this.buscarPagina();
  }

  carregarMais() {
    if (!this.temMais() || this.carregando() || this.carregandoMais()) return;
    this.pagina++;
    this.carregandoMais.set(true);
    this.buscarPagina();
  }

  private buscarPagina() {
    const pedido = ++this.pedidoAtual;
    this.routeService.getMyRoutes({ active: this.ativas(), page: this.pagina, size: TAMANHO_PAGINA }).subscribe({
      next: (pagina) => {
        if (pedido !== this.pedidoAtual) return;
        this.rotas.update((atuais) => [...atuais, ...(pagina.content ?? [])]);
        this.temMais.set(!pagina.last);
        this.carregando.set(false);
        this.carregandoMais.set(false);
      },
      error: () => {
        if (pedido !== this.pedidoAtual) return;
        this.erroCarregar.set(this.pagina === 0);
        this.temMais.set(false);
        this.carregando.set(false);
        this.carregandoMais.set(false);
      },
    });
  }

  // alunos diferentes da rota (o mesmo aluno embarca e desce)
  alunosDa(rota: routeWithStops): number {
    return new Set(rota.stops.flatMap((s) => s.students.map((a) => a.studentId))).size;
  }

  abrir(rota: routeWithStops) {
    this.router.navigate(['/driver-routes', rota.id]);
  }

  // ---------- nova rota ----------

  abrirNova() {
    this.nome = '';
    this.criando.set(true);
  }

  fecharNova() {
    if (!this.enviando()) this.criando.set(false);
  }

  criar() {
    if (this.enviando()) return;
    const nome = this.nome.trim();
    if (!nome) {
      this.popUpService.show('Dê um nome para a rota', 'warning', 'Campos Incompletos');
      return;
    }
    this.enviando.set(true);
    this.routeService.createRoute(nome).subscribe({
      next: (rota) => {
        this.enviando.set(false);
        this.criando.set(false);
        // a rota nasce vazia: vai direto montar
        this.router.navigate(['/driver-routes', rota.id]);
      },
      error: () => this.enviando.set(false),
    });
  }

  fecharPerfil() {
    this.mostrarPerfil.set(false);
    if (this.route.snapshot.queryParamMap.has('perfil')) {
      this.router.navigate([], { queryParams: {}, replaceUrl: true });
    }
  }
}
