import { Component, DestroyRef, ElementRef, OnInit, inject, signal, viewChild, effect } from '@angular/core';
import { Router } from '@angular/router';
import { Location } from '@angular/common';
import { ContractService } from '../../services/contract.service';
import { StudentService } from '../../services/student.service';
import { switchMap } from 'rxjs';
import { AuthService } from '../../services/auth.service';
import { TrailHeader } from '../../shared/trail-header/trail-header';
import { Footer } from '../../shared/footer/footer';
import {
  ContractStatus,
  contractSummaryResponse,
  isoParaData,
  periodicidadeLabel,
} from '../../services/dto/contract/contract.dto';

type Aba = 'todos' | ContractStatus;

// uma aba por status do backend (Figma "Ambos · /contracts · status novos e paginação")
const ABAS: { id: Aba; label: string }[] = [
  { id: 'todos', label: 'Todos' },
  { id: 'PENDING', label: 'Pendentes' },
  { id: 'SCHEDULED', label: 'Agendados' },
  { id: 'ACTIVE', label: 'Ativos' },
  { id: 'INACTIVE', label: 'Inativos' },
  { id: 'CANCELED', label: 'Cancelados' },
];

// texto e cor do selo (componente Selo do Figma)
const STATUS: Record<ContractStatus, { texto: string; classe: string }> = {
  PENDING: { texto: 'Pendente', classe: 'pendente' },
  SCHEDULED: { texto: 'Agendado', classe: 'agendado' },
  ACTIVE: { texto: 'Ativo', classe: 'ativo' },
  INACTIVE: { texto: 'Inativo', classe: 'inativo' },
  CANCELED: { texto: 'Cancelado', classe: 'cancelado' },
};

const TAMANHO_PAGINA = 20;

// contratos do usuario logado (aba "Contratos" do profile select); o backend filtra e pagina
@Component({
  selector: 'app-contracts',
  standalone: true,
  imports: [TrailHeader, Footer],
  templateUrl: './contracts.html',
  styleUrl: './contracts.css',
})
export class Contracts implements OnInit {
  private contractService = inject(ContractService);
  private studentService = inject(StudentService);
  private authService = inject(AuthService);
  private router = inject(Router);
  private location = inject(Location);
  private destroyRef = inject(DestroyRef);

  protected readonly abas = ABAS;
  protected readonly statusInfo = STATUS;
  protected readonly periodicidade = periodicidadeLabel;

  protected ehMotorista = false;
  protected abaAtiva = signal<Aba>('todos');
  protected contratos = signal<contractSummaryResponse[]>([]);
  protected carregando = signal(true);
  protected carregandoMais = signal(false);
  protected erroCarregar = signal(false);
  protected temMais = signal(false);
  // foto do aluno pelo studentId (o contrato nao traz foto): pro responsavel, vem do my-children
  protected fotos = signal<Map<string, string>>(new Map());
  private pagina = 0;
  // ignora resposta atrasada de uma aba que ja foi trocada
  private pedidoAtual = 0;

  // fim da lista: quando aparece na tela, busca a proxima pagina
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
    this.ehMotorista = this.authService.getRolesFromToken().includes('ROLE_DRIVER');
    this.carregar();
    if (!this.ehMotorista) this.carregarFotos();
  }

  trocarAba(aba: Aba) {
    if (aba === this.abaAtiva()) return;
    this.abaAtiva.set(aba);
    this.carregar();
  }

  carregar() {
    this.pagina = 0;
    this.contratos.set([]);
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
    const aba = this.abaAtiva();
    this.contractService
      .getMyContracts({ status: aba === 'todos' ? undefined : aba, page: this.pagina, size: TAMANHO_PAGINA })
      .subscribe({
        next: (pagina) => {
          if (pedido !== this.pedidoAtual) return;
          this.contratos.update((atuais) => [...atuais, ...(pagina.content ?? [])]);
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

  // linhas de aviso do cartao: inicio do agendado, data do cancelamento pedido e parcelas atrasadas
  inicioAgendado(c: contractSummaryResponse): string | null {
    return c.status === 'SCHEDULED' ? diaMes(c.startDate) : null;
  }

  dataCancelamento(c: contractSummaryResponse): string | null {
    return c.pendingCancellation?.effectiveDate ? diaMes(c.pendingCancellation.effectiveDate) : null;
  }

  atrasadas(c: contractSummaryResponse): string | null {
    const n = c.overduePayments ?? 0;
    if (n <= 0) return null;
    return n === 1 ? '1 parcela atrasada' : `${n} parcelas atrasadas`;
  }

  private carregarFotos() {
    this.studentService
      .getMyChildren({ size: 100 })
      .pipe(switchMap((pagina) => this.studentService.comFotos(pagina.content ?? [])))
      .subscribe({
        next: (filhos) =>
          this.fotos.set(new Map(filhos.filter((f) => f.photoUrl).map((f) => [f.id, f.photoUrl as string]))),
        error: () => {},
      });
  }

  // sem foto (ou motorista, que ainda nao tem a lista de alunos aqui): iniciais
  iniciais(nome: string): string {
    return (nome ?? '')
      .split(' ')
      .filter(Boolean)
      .slice(0, 2)
      .map((parte) => parte[0].toUpperCase())
      .join('');
  }

  visualizar(contrato: contractSummaryResponse) {
    this.router.navigate(['/contract', contrato.contractId]);
  }

  voltar() {
    this.location.back();
  }
}

// AAAA-MM-DD -> DD/MM
function diaMes(iso: string): string {
  return isoParaData(iso).slice(0, 5);
}
