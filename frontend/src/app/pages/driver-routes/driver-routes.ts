import { Component, DestroyRef, ElementRef, OnInit, effect, inject, signal, viewChild } from '@angular/core';
import { Location } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { TrailHeader } from '../../shared/trail-header/trail-header';
import { Footer } from '../../shared/footer/footer';
import { PopUpService } from '../../shared/pop-up';
import { RouteService } from '../../services/route.service';
import { routeWithStops } from '../../services/dto/route/route.dto';

const TAMANHO_PAGINA = 10;

// Figma "Rotas · Motorista": lista (Ativas/Todas), sem rotas e a folha "Nova rota"
@Component({
  selector: 'app-driver-routes',
  standalone: true,
  imports: [TrailHeader, Footer, FormsModule],
  templateUrl: './driver-routes.html',
  styleUrls: ['../../shared/folha.css', '../../shared/rota.css', './driver-routes.css'],
})
export class DriverRoutes implements OnInit {
  private routeService = inject(RouteService);
  private popUpService = inject(PopUpService);
  private router = inject(Router);
  private location = inject(Location);
  private destroyRef = inject(DestroyRef);

  // "Ativas" = so as rotas em que ele esta hoje; "Todas" inclui as que ele deixou
  protected ativas = signal(true);
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
    this.carregar();
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

  // "3 pontos" / "1 ponto"
  pontos(rota: routeWithStops): string {
    const total = rota.stops?.length ?? 0;
    return total === 1 ? '1 ponto' : `${total} pontos`;
  }

  // alunos diferentes na rota (o mesmo aluno embarca e desce)
  alunos(rota: routeWithStops): number {
    return new Set((rota.stops ?? []).flatMap((s) => s.students.map((a) => a.studentId))).size;
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

  voltar() {
    this.location.back();
  }
}
