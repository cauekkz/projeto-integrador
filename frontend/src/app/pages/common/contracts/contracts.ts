import { Skeleton } from '../../../shared/skeleton/skeleton';
import { Component, DestroyRef, ElementRef, Input, OnInit, computed, inject, signal, viewChild, effect } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { Location } from '@angular/common';
import { ContractService } from '../../../services/contract.service';
import { StudentService } from '../../../services/student.service';
import { switchMap } from 'rxjs';
import { AuthService } from '../../../services/auth.service';
import { TrailHeader } from '../../../shared/trail-header/trail-header';
import { Footer } from '../../../shared/footer/footer';
import {
  ContractStatus,
  contractSummaryResponse,
  isoParaData,
  periodicidadeLabel,
} from '../../../services/dto/contract/contract.dto';
import { iniciaisDe } from '../../../shared/iniciais/iniciais';


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

// tempo da animacao de abrir a pasta (folhas saindo, contracts.css) antes de trocar de tela
const TEMPO_ABRIR_PASTA = 380;

// pasta na tela de Contratos: de um filho (responsavel, ou motorista dentro do responsavel) ou de um responsavel (motorista)
interface Pasta {
  id: string;
  nome: string;
  ativo: boolean;
  pendente: boolean;
  atrasadas: number;
}

// contratos do usuario logado (aba "Contratos" do profile select); o backend filtra e pagina
@Component({
  selector: 'app-contracts',
  standalone: true,
  imports: [Skeleton, TrailHeader, Footer],
  templateUrl: './contracts.html',
  styleUrl: './contracts.css',
})
export class Contracts implements OnInit {
  // dentro da pasta do filho (/contracts/aluno/:id): so os contratos dele, sem cabecalho nem rodape
  @Input() alunoId: string | null = null;
  @Input() embutido = false;

  private contractService = inject(ContractService);
  private studentService = inject(StudentService);
  private authService = inject(AuthService);
  private router = inject(Router);
  private route = inject(ActivatedRoute);
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

  // fora da pasta do filho a tela mostra pastas, com qualquer contrato; quem tem contrato ativo vem primeiro.
  // responsavel: uma por filho. Motorista: uma por responsavel (/contracts) e, dentro dela
  // (/contracts/responsavel/:id), uma por dependente daquele responsavel
  protected modoPastas = false;
  protected responsavelId: string | null = null;
  protected responsavelNome = '';
  private todosContratos = signal<contractSummaryResponse[]>([]);
  protected pastas = computed<Pasta[]>(() => {
    const porResponsavel = this.ehMotorista && !this.responsavelId;
    const grupos = new Map<string, Pasta>();
    for (const c of this.todosContratos()) {
      if (this.responsavelId && c.responsibleId !== this.responsavelId) continue;
      const id = porResponsavel ? c.responsibleId : c.studentId;
      const nome = porResponsavel ? c.responsibleName : c.studentName;
      const p = grupos.get(id) ?? { id, nome, ativo: false, pendente: false, atrasadas: 0 };
      p.ativo ||= c.status === 'ACTIVE';
      p.pendente ||= c.status === 'PENDING';
      p.atrasadas += c.overduePayments ?? 0;
      grupos.set(id, p);
    }
    return [...grupos.values()].sort((a, b) => Number(b.ativo) - Number(a.ativo) || a.nome.localeCompare(b.nome));
  });
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
    this.modoPastas = !this.embutido;
    this.responsavelId = this.embutido ? null : this.route.snapshot.paramMap.get('responsibleId');
    this.responsavelNome = this.route.snapshot.queryParamMap.get('nome') ?? 'Responsável';
    if (this.modoPastas) {
      this.carregarPastas();
    } else {
      this.carregar();
    }
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
      .getMyContracts({
        status: aba === 'todos' ? undefined : aba,
        studentId: this.alunoId ?? undefined,
        page: this.pagina,
        size: TAMANHO_PAGINA,
      })
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

  // todos os contratos do responsavel (de qualquer status), so pra montar as pastas
  carregarPastas() {
    this.carregando.set(true);
    this.erroCarregar.set(false);
    this.contractService.getMyContracts({ size: 100 }).subscribe({
      next: (pagina) => {
        this.todosContratos.set(pagina.content ?? []);
        this.carregando.set(false);
      },
      error: () => {
        this.erroCarregar.set(true);
        this.carregando.set(false);
      },
    });
  }

  // a pasta "abre" (folhas saem) e so depois a tela troca: no celular nao tem hover, entao a animacao vem no toque.
  // quem pediu menos movimento vai direto
  protected abrindo = signal<string | null>(null);

  abrirPasta(pasta: Pasta) {
    if (this.abrindo()) return;
    // motorista na lista de responsaveis: abre as pastas dos dependentes dele; senao, a pasta do filho
    const destino = this.ehMotorista && !this.responsavelId ? '/contracts/responsavel' : '/contracts/aluno';
    const ir = () => this.router.navigate([destino, pasta.id], { queryParams: { nome: pasta.nome } });
    const menosMovimento = typeof matchMedia === 'function' && matchMedia('(prefers-reduced-motion: reduce)').matches;
    if (menosMovimento) {
      ir();
      return;
    }
    this.abrindo.set(pasta.id);
    const espera = setTimeout(() => {
      this.abrindo.set(null);
      ir();
    }, TEMPO_ABRIR_PASTA);
    this.destroyRef.onDestroy(() => clearTimeout(espera));
  }

  // selo embaixo da pasta: o que pede atencao primeiro
  resumoPasta(p: Pasta): { texto: string; classe: string } {
    if (p.atrasadas > 0) return { texto: p.atrasadas === 1 ? '1 parcela atrasada' : `${p.atrasadas} parcelas atrasadas`, classe: 'atrasada' };
    if (p.pendente) return { texto: 'Contrato pendente', classe: 'pendente' };
    if (p.ativo) return { texto: 'Ativo', classe: 'ativo' };
    return { texto: 'Sem contrato ativo', classe: 'inativo' };
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
  iniciais(nome: string | null | undefined): string {
    return iniciaisDe(nome);
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
