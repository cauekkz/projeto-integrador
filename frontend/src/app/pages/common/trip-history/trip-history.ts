import { Trajeto } from '../../../shared/trajeto/trajeto';
import { Esqueleto } from '../../../shared/esqueleto/esqueleto';
import { Component, DestroyRef, ElementRef, OnInit, afterNextRender, computed, inject, signal, viewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Location } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { ActivatedRoute, Router } from '@angular/router';
import { TrailHeader } from '../../../shared/trail-header/trail-header';
import { Footer } from '../../../shared/footer/footer';
import { PopUpService } from '../../../shared/pop-up';
import { AuthService } from '../../../services/auth.service';
import { TripService } from '../../../services/trip.service';
import { ContractService } from '../../../services/contract.service';
import { RouteService } from '../../../services/route.service';
import { StudentService } from '../../../services/student.service';
import { tripHistoryItem } from '../../../services/dto/group/group.dto';
import { diaEMes } from '../../../services/dto/trip/trip.dto';

interface Filtro {
  studentId: string;
  routeId: string;
  from: string;
  to: string;
}

const VAZIO: Filtro = { studentId: '', routeId: '', from: '', to: '' };

// Figma "/trip-history" (1058:1387 motorista, 1058:1699 responsável, 1058:1645 vazio, 1058:1466/1748 filtrar, 1058:1558 erro 400)
@Component({
  selector: 'app-trip-history',
  standalone: true,
  imports: [Trajeto, Esqueleto, FormsModule, TrailHeader, Footer],
  templateUrl: './trip-history.html',
  styleUrls: ['../../../shared/folha.css', '../../../shared/rota.css', './trip-history.css'],
})
export class TripHistory implements OnInit {
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private authService = inject(AuthService);
  private tripService = inject(TripService);
  private contractService = inject(ContractService);
  private routeService = inject(RouteService);
  private studentService = inject(StudentService);
  private popUpService = inject(PopUpService);
  private destroyRef = inject(DestroyRef);
  private location = inject(Location);

  protected ehMotorista = false;
  protected carregando = signal(true);
  protected carregandoMais = signal(false);
  protected erroCarregar = signal(false);
  protected itens = signal<tripHistoryItem[]>([]);
  protected temMais = signal(false);
  private pagina = 0;

  protected filtro: Filtro = { ...VAZIO };
  protected rascunho: Filtro = { ...VAZIO };
  protected filtroAberto = signal(false);
  protected filtroAtivo = signal(false);
  protected alunos = signal<{ id: string; nome: string }[]>([]);
  protected rotas = signal<{ id: string; nome: string }[]>([]);

  // pesquisa (motorista): filtra no front o que ja carregou, pelo aluno, pela rota e pelo dia
  protected busca = signal('');
  protected visiveis = computed(() => {
    const termo = normalizar(this.busca().trim());
    if (!termo) return this.itens();
    return this.itens().filter((i) =>
      normalizar(`${i.studentName} ${i.routeName} ${diaEMes(i.date)}`).includes(termo),
    );
  });

  private fimDaLista = viewChild<ElementRef<HTMLElement>>('fimDaLista');
  private observador?: IntersectionObserver;

  constructor() {
    afterNextRender(() => this.observarFim());
  }

  ngOnInit() {
    this.ehMotorista = this.authService.getRolesFromToken().includes('ROLE_DRIVER');
    // ?studentId= (do detalhe do filho) ja abre filtrado
    const studentId = this.route.snapshot.queryParamMap.get('studentId');
    if (studentId) {
      this.filtro = { ...VAZIO, studentId };
      this.filtroAtivo.set(true);
    }
    this.carregar();
    this.carregarOpcoes();
  }

  private observarFim() {
    const fim = this.fimDaLista()?.nativeElement;
    if (!fim || typeof IntersectionObserver === 'undefined') return;
    this.observador = new IntersectionObserver((e) => {
      if (e.some((x) => x.isIntersecting)) this.carregarMais();
    });
    this.observador.observe(fim);
    this.destroyRef.onDestroy(() => this.observador?.disconnect());
  }

  carregar() {
    this.pagina = 0;
    this.itens.set([]);
    this.carregando.set(true);
    this.erroCarregar.set(false);
    this.buscar();
  }

  carregarMais() {
    if (!this.temMais() || this.carregando() || this.carregandoMais()) return;
    this.pagina++;
    this.carregandoMais.set(true);
    this.buscar();
  }

  private buscar() {
    this.tripService.getHistory({ ...this.filtro, page: this.pagina }).subscribe({
      next: (pagina) => {
        this.itens.update((l) => [...l, ...(pagina.content ?? [])]);
        this.temMais.set(!pagina.last);
        this.carregando.set(false);
        this.carregandoMais.set(false);
        this.seguirSeFimNaTela();
      },
      error: (erro: HttpErrorResponse) => {
        this.carregando.set(false);
        this.carregandoMais.set(false);
        if (erro?.status === 400) {
          this.popUpService.show(erro?.error?.message || 'Confira o período.', 'error', 'Dados inválidos');
          return;
        }
        this.erroCarregar.set(true);
      },
    });
  }

  pesquisar(texto: string) {
    this.busca.set(texto);
    this.seguirSeFimNaTela();
  }

  // com a pesquisa a lista encolhe e o fim continua na tela: o observador nao avisa de novo,
  // entao busca a proxima pagina por aqui ate o fim sair da tela (ou acabar o historico)
  private seguirSeFimNaTela() {
    if (!this.busca().trim()) return;
    requestAnimationFrame(() => {
      const fim = this.fimDaLista()?.nativeElement;
      if (fim && fim.getBoundingClientRect().top < window.innerHeight) this.carregarMais();
    });
  }

  // motorista: alunos dos contratos e as rotas dele; responsavel: os filhos
  private carregarOpcoes() {
    const ordenar = (l: { id: string; nome: string }[]) => l.sort((a, b) => a.nome.localeCompare(b.nome));
    if (this.ehMotorista) {
      this.contractService.getMyContracts({ size: 100 }).subscribe({
        next: (pagina) => {
          const m = new Map<string, string>();
          for (const c of pagina.content ?? []) m.set(c.studentId, c.studentName);
          this.alunos.set(ordenar([...m].map(([id, nome]) => ({ id, nome }))));
        },
        error: () => {},
      });
      this.routeService.getMyRoutes({ size: 100 }).subscribe({
        next: (pagina) => this.rotas.set(ordenar((pagina.content ?? []).map((r) => ({ id: r.id, nome: r.name })))),
        error: () => {},
      });
    } else {
      this.studentService.getMyChildren({ size: 100 }).subscribe({
        next: (pagina) => this.alunos.set(ordenar((pagina.content ?? []).map((f) => ({ id: f.id, nome: f.name })))),
        error: () => {},
      });
    }
  }

  voltar() {
    this.location.back();
  }

  // ---------- cartao ----------

  readonly diaEMes = diaEMes;

  hora(dataHora: string | null): string {
    return dataHora ? dataHora.slice(11, 16) : '';
  }

  selo(item: tripHistoryItem): { texto: string; classe: string } {
    switch (item.boardingStatus) {
      case 'DELIVERED':
        return { texto: 'Entregue', classe: 'entregue' };
      case 'ABSENT':
        return { texto: 'Ausente', classe: 'ausente' };
      case 'BOARDED':
        return { texto: 'Embarcou', classe: 'embarcou' };
      default:
        return item.tripStatus === 'FINISHED' || item.tripStatus === 'CANCELED'
          ? { texto: 'Não atendido', classe: 'ausente' }
          : { texto: 'Aguardando', classe: 'aguardando' };
    }
  }

  // "Embarcou às 06:42 · Chegou às 07:10", "Não embarcou", "Embarcou às 17:05 · Viagem fechada pelo sistema"
  detalhe(item: tripHistoryItem): string {
    if (item.boardingStatus === 'ABSENT') return 'Não embarcou';
    const partes: string[] = [];
    if (item.boardingTime) partes.push(`Embarcou às ${this.hora(item.boardingTime)}`);
    if (item.unboardingTime) partes.push(`Chegou às ${this.hora(item.unboardingTime)}`);
    if (item.tripStatus === 'FINISHED' && !item.endTime) partes.push('Viagem fechada pelo sistema');
    if (!partes.length) return item.tripStatus === 'FINISHED' ? 'Não foi atendido nesta viagem' : 'Viagem em andamento';
    return partes.join(' · ');
  }

  ocorrencias(n: number) {
    return n === 1 ? '1 ocorrência' : `${n} ocorrências`;
  }

  abrirOcorrencias(item: tripHistoryItem) {
    this.router.navigate(['/trip-history', item.tripId, 'ocorrencias'], {
      queryParams: { data: item.date, rota: item.routeName },
    });
  }

  // ---------- filtro ----------

  abrirFiltro() {
    this.rascunho = { ...this.filtro };
    this.filtroAberto.set(true);
  }

  fecharFiltro() {
    this.filtroAberto.set(false);
  }

  aplicarFiltro() {
    // Figma 1058:1558: o aviso aparece e a folha continua aberta
    if (this.rascunho.from && this.rascunho.to && this.rascunho.to < this.rascunho.from) {
      this.popUpService.show('O fim do período não pode ser antes do início', 'error', 'Dados inválidos');
      return;
    }
    this.filtro = { ...this.rascunho };
    this.filtroAtivo.set(Object.values(this.filtro).some(Boolean));
    this.filtroAberto.set(false);
    this.carregar();
  }

  limparFiltro() {
    this.rascunho = { ...VAZIO };
    this.aplicarFiltro();
  }
}

// minusculas e sem acento: "joão" acha "Joao"
function normalizar(texto: string): string {
  return texto.normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLowerCase();
}
