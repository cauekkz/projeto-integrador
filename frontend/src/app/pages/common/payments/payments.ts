import { Skeleton } from '../../../shared/skeleton/skeleton';
import { Component, DestroyRef, ElementRef, Input, OnInit, computed, effect, inject, signal, viewChild } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { Location } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { TrailHeader } from '../../../shared/trail-header/trail-header';
import { Footer } from '../../../shared/footer/footer';
import { PopUpService } from '../../../shared/pop-up';
import { AuthService } from '../../../services/auth.service';
import { PaymentService } from '../../../services/payment.service';
import { ContractService } from '../../../services/contract.service';
import { ChatWebSocketService } from '../../../services/chat.service';
import { METODOS, PaymentMethod, paymentResponse, PaymentStatus } from '../../../services/dto/payment/payment.dto';
import { formatarChavePix, pixTipoLabel } from '../../../services/dto/driver/pix.dto';
import { diaMes, valorEmReais } from '../../../services/dto/contract/contract.dto';

import { BotaoCarregandoDirective } from '../../../shared/botao-carregando/botao-carregando.directive';
type Aba = 'todas' | PaymentStatus;

const ABAS: { id: Aba; label: string }[] = [
  { id: 'todas', label: 'Todas' },
  { id: 'PENDING', label: 'Pendentes' },
  { id: 'OVERDUE', label: 'Atrasadas' },
  { id: 'PAID', label: 'Pagas' },
  { id: 'CANCELED', label: 'Dispensadas' },
];

const STATUS: Record<PaymentStatus, { texto: string; classe: string }> = {
  PENDING: { texto: 'Pendente', classe: 'pendente' },
  OVERDUE: { texto: 'Atrasada', classe: 'atrasada' },
  PAID: { texto: 'Paga', classe: 'paga' },
  CANCELED: { texto: 'Dispensada', classe: 'dispensada' },
};

const TAMANHO_PAGINA = 20;

interface Opcao {
  id: string;
  nome: string;
}

// Figma "Etapa 4 · Parcelas": mesma tela pros dois perfis.
// motorista: "Recebi" (com o metodo) e "Dispensar", sempre com confirmacao (nao tem volta);
// responsavel: chave Pix do motorista pra copiar e "Avisar no chat" (quem da baixa e o motorista).
// ?contractId= (botao "Parcelas" do contrato) mostra so as parcelas daquele contrato
@Component({
  selector: 'app-payments',
  standalone: true,
  imports: [BotaoCarregandoDirective, Skeleton, TrailHeader, Footer, FormsModule],
  templateUrl: './payments.html',
  styleUrls: ['../../../shared/folha.css', './payments.css'],
})
export class Payments implements OnInit {
  // dentro da pasta do filho (/contracts/aluno/:id): so as parcelas dele, sem cabecalho nem rodape
  @Input() alunoId: string | null = null;
  @Input() embutido = false;

  private paymentService = inject(PaymentService);
  private contractService = inject(ContractService);
  private chatService = inject(ChatWebSocketService);
  private authService = inject(AuthService);
  private popUpService = inject(PopUpService);
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private location = inject(Location);
  private destroyRef = inject(DestroyRef);

  protected readonly abas = ABAS;
  protected readonly statusInfo = STATUS;
  protected readonly metodos = METODOS;
  protected readonly valor = valorEmReais;
  protected readonly tipoPix = pixTipoLabel;
  protected readonly chavePix = formatarChavePix;

  protected ehMotorista = false;
  protected contractId: string | null = null;
  protected abaAtiva = signal<Aba>('todas');
  protected parcelas = signal<paymentResponse[]>([]);
  // responsavel: alguma parcela em aberto de motorista sem chave Pix (aviso unico no topo)
  protected semPix = computed(() => !this.ehMotorista && this.parcelas().some((p) => this.aberta(p) && !p.driverPixKey));
  protected carregando = signal(true);
  protected carregandoMais = signal(false);
  protected erroCarregar = signal(false);
  protected temMais = signal(false);
  private pagina = 0;
  private pedidoAtual = 0;

  // filtro (folha "Filtrar parcelas"): aluno, a outra parte e o periodo do vencimento
  protected filtroAberto = signal(false);
  protected alunos = signal<Opcao[]>([]);
  protected pessoas = signal<Opcao[]>([]);
  protected filtro = { studentId: '', pessoaId: '', from: '', to: '' };
  protected rascunho = { ...this.filtro };
  protected filtroAtivo = signal(false);

  // confirmacoes do motorista
  protected recebendo = signal<paymentResponse | null>(null);
  protected metodo = signal<PaymentMethod>('PIX');
  protected dispensando = signal<paymentResponse | null>(null);
  protected enviando = signal(false);
  protected abrindoChat = signal<string | null>(null);

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
    this.contractId = this.embutido ? null : this.route.snapshot.queryParamMap.get('contractId');
    if (this.alunoId) this.filtro = this.rascunho = { studentId: this.alunoId, pessoaId: '', from: '', to: '' };
    // ?status=OVERDUE (do perfil) abre direto a aba "Atrasadas"
    const status = this.route.snapshot.queryParamMap.get('status');
    const aba = ABAS.find((a) => a.id === status);
    if (aba) this.abaAtiva.set(aba.id);
    this.carregar();
    if (!this.contractId) this.carregarOpcoes();
  }

  // chip meio cortado na borda da fileira: rola ate ele aparecer inteiro
  mostrarChip(evento: Event) {
    const chip = evento.currentTarget as HTMLElement | null;
    chip?.scrollIntoView?.({ behavior: 'smooth', block: 'nearest', inline: 'nearest' });
  }

  trocarAba(aba: Aba) {
    if (aba === this.abaAtiva()) return;
    this.abaAtiva.set(aba);
    this.carregar();
  }

  carregar() {
    this.pagina = 0;
    this.parcelas.set([]);
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
    const f = this.filtro;
    this.paymentService
      .getPayments({
        status: aba === 'todas' ? undefined : aba,
        contractId: this.contractId ?? undefined,
        studentId: f.studentId || undefined,
        responsibleId: this.ehMotorista ? f.pessoaId || undefined : undefined,
        driverId: !this.ehMotorista ? f.pessoaId || undefined : undefined,
        from: f.from || undefined,
        to: f.to || undefined,
        page: this.pagina,
        size: TAMANHO_PAGINA,
      })
      .subscribe({
        next: (pagina) => {
          if (pedido !== this.pedidoAtual) return;
          const primeira = this.pagina === 0;
          this.parcelas.update((atuais) => [...atuais, ...(pagina.content ?? [])]);
          this.temMais.set(!pagina.last);
          this.carregando.set(false);
          this.carregandoMais.set(false);
          if (primeira && this.contractId) this.avisarContrato();
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

  // Figma "filtrada pelo contrato": aviso no topo com o nome do aluno
  private avisarContrato() {
    const aluno = this.parcelas()[0]?.studentName;
    if (!aluno) return;
    this.popUpService.show(
      'Parcela vencida de contrato cancelado continua aberta e pode receber baixa.',
      'info',
      `Parcelas do contrato de ${aluno}`,
    );
  }

  // opcoes do filtro: alunos e a outra parte dos meus contratos (sem repetir)
  private carregarOpcoes() {
    this.contractService.getMyContracts({ size: 100 }).subscribe({
      next: (pagina) => {
        const alunos = new Map<string, string>();
        const pessoas = new Map<string, string>();
        for (const c of pagina.content ?? []) {
          alunos.set(c.studentId, c.studentName);
          if (this.ehMotorista) pessoas.set(c.responsibleId, c.responsibleName);
          else pessoas.set(c.driverId, c.driverName);
        }
        const ordenar = (m: Map<string, string>) =>
          [...m].map(([id, nome]) => ({ id, nome })).sort((a, b) => a.nome.localeCompare(b.nome));
        this.alunos.set(ordenar(alunos));
        this.pessoas.set(ordenar(pessoas));
      },
      error: () => {},
    });
  }

  // ---------- textos do cartao ----------

  linhaData(p: paymentResponse): string {
    switch (p.status) {
      case 'OVERDUE':
        return `Venceu em ${diaMes(p.dueDate)}`;
      case 'PENDING':
        return `Vence em ${diaMes(p.dueDate)}`;
      case 'PAID': {
        const metodo = METODOS.find((m) => m.value === p.paymentMethod)?.label;
        return `Paga em ${diaMes(p.paidAt ?? p.dueDate)}${metodo ? ' · ' + metodo : ''}`;
      }
      default:
        return `Vencimento ${diaMes(p.dueDate)}`;
    }
  }

  aberta(p: paymentResponse): boolean {
    return p.status === 'PENDING' || p.status === 'OVERDUE';
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
    if (this.rascunho.from && this.rascunho.to && this.rascunho.to < this.rascunho.from) {
      this.popUpService.show('A data final não pode ser anterior à inicial.', 'error', 'Dados inválidos');
      return;
    }
    this.filtro = { ...this.rascunho };
    // na pasta o filho e fixo: nao conta como filtro
    const { studentId, ...resto } = this.filtro;
    this.filtroAtivo.set(Object.values(resto).some(Boolean) || (!!studentId && !this.alunoId));
    this.filtroAberto.set(false);
    this.carregar();
  }

  limparFiltro() {
    this.rascunho = { studentId: this.alunoId ?? '', pessoaId: '', from: '', to: '' };
    this.aplicarFiltro();
  }

  // ---------- motorista: recebi / dispensar ----------

  abrirRecebi(p: paymentResponse) {
    this.metodo.set('PIX');
    this.recebendo.set(p);
  }

  fecharRecebi() {
    if (!this.enviando()) this.recebendo.set(null);
  }

  confirmarRecebimento() {
    const p = this.recebendo();
    if (!p || this.enviando()) return;
    this.enviando.set(true);
    this.paymentService.markPaid(p.id, this.metodo()).subscribe({
      next: (atualizada) => {
        this.enviando.set(false);
        this.recebendo.set(null);
        this.atualizar(atualizada);
        this.popUpService.show('O responsável vê a parcela como paga.', 'success', 'Recebimento confirmado');
      },
      // 409 "Essa parcela já foi paga ou cancelada" vem no popup global; recarrega pra mostrar o status real
      error: (error) => {
        this.enviando.set(false);
        if (error?.status === 409) {
          this.recebendo.set(null);
          this.carregar();
        }
      },
    });
  }

  abrirDispensar(p: paymentResponse) {
    this.dispensando.set(p);
  }

  fecharDispensar() {
    if (!this.enviando()) this.dispensando.set(null);
  }

  confirmarDispensa() {
    const p = this.dispensando();
    if (!p || this.enviando()) return;
    this.enviando.set(true);
    this.paymentService.cancel(p.id).subscribe({
      next: (atualizada) => {
        this.enviando.set(false);
        this.dispensando.set(null);
        this.atualizar(atualizada);
        this.popUpService.show('O responsável foi avisado.', 'success', 'Parcela dispensada');
      },
      error: (error) => {
        this.enviando.set(false);
        if (error?.status === 409) {
          this.dispensando.set(null);
          this.carregar();
        }
      },
    });
  }

  // a parcela mudou de status: some da aba filtrada ou troca no lugar
  private atualizar(atualizada: paymentResponse) {
    const aba = this.abaAtiva();
    this.parcelas.update((lista) =>
      aba !== 'todas' && aba !== atualizada.status
        ? lista.filter((p) => p.id !== atualizada.id)
        : lista.map((p) => (p.id === atualizada.id ? atualizada : p)),
    );
  }

  // ---------- responsavel: copiar chave e avisar no chat ----------

  async copiarChave(p: paymentResponse) {
    if (!p.driverPixKey) return;
    try {
      await navigator.clipboard.writeText(p.driverPixKey);
      this.popUpService.show(
        'Pague no app do seu banco. Quem confirma o pagamento é o motorista.',
        'success',
        'Chave Pix copiada',
      );
    } catch {
      this.popUpService.show('Não foi possível copiar. Copie a chave manualmente.', 'error', 'Erro');
    }
  }

  avisarNoChat(p: paymentResponse) {
    if (this.abrindoChat()) return;
    this.abrindoChat.set(p.id);
    this.chatService.findChatWith(p.driverId).subscribe((chatId) => {
      this.abrindoChat.set(null);
      if (chatId) {
        this.router.navigate(['/chat-details', chatId]);
      } else {
        this.popUpService.show('Não encontramos a conversa com o motorista.', 'error', 'Não encontrado');
      }
    });
  }

  voltar() {
    this.location.back();
  }
}
