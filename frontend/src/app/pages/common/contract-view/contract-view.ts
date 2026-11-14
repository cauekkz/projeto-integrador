import { Skeleton } from '../../../shared/skeleton/skeleton';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { Location } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Observable, forkJoin, of } from 'rxjs';
import { ContractService } from '../../../services/contract.service';
import { StorageService } from '../../../services/storage.service';
import { AuthService } from '../../../services/auth.service';
import { ChatWebSocketService } from '../../../services/chat.service';
import { PopUpService } from '../../../shared/pop-up';
import { TrailHeader } from '../../../shared/trail-header/trail-header';
import { ContractDraftService } from '../../driver/driver-contract/contract-draft.service';
import {
  ContractStatus,
  RELATION_LABEL,
  cancellationResponse,
  contractDetailsResponse,
  diaMes,
  diaSeguinte,
  isoParaData,
  periodicidadeLabel,
  valorEmReais,
} from '../../../services/dto/contract/contract.dto';
import { ConfirmacaoService } from '../../../shared/confirmacao/confirmacao.service';
import { iniciaisDe } from '../../../shared/iniciais/iniciais';

import { BotaoCarregandoDirective } from '../../../shared/botao-carregando/botao-carregando.directive';
// mesmos selos da lista /contracts (Figma 1036:698)
const STATUS: Record<ContractStatus, { texto: string; classe: string }> = {
  PENDING: { texto: 'Pendente', classe: 'pendente' },
  SCHEDULED: { texto: 'Agendado', classe: 'agendado' },
  ACTIVE: { texto: 'Ativo', classe: 'ativo' },
  INACTIVE: { texto: 'Inativo', classe: 'inativo' },
  CANCELED: { texto: 'Cancelado', classe: 'cancelado' },
};

export const MOTIVO_MAXIMO = 500;

// analise do contrato (card do chat ou lista de contratos); ?chatId= volta pra conversa.
// Figma "Etapa 2 · Contrato completo": assinaturas, responsaveis do aluno, cancelamento (pedir, aceitar, desistir),
// historico de cancelamentos, parcelas e renovar (motorista, contrato ativo)
@Component({
  selector: 'app-contract-view',
  standalone: true,
  imports: [BotaoCarregandoDirective, Skeleton, TrailHeader, FormsModule],
  templateUrl: './contract-view.html',
  styleUrl: './contract-view.css',
})
export class ContractView implements OnInit {
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private location = inject(Location);
  private contractService = inject(ContractService);
  private storageService = inject(StorageService);
  private authService = inject(AuthService);
  private chatService = inject(ChatWebSocketService);
  private popUpService = inject(PopUpService);
  private confirmacao = inject(ConfirmacaoService);
  private draft = inject(ContractDraftService);

  protected contractId = '';
  protected chatId: string | null = null;
  protected ehMotorista = false;
  private meuId: string | null = null;

  protected contrato = signal<contractDetailsResponse | null>(null);
  protected carregando = signal(true);
  protected erroCarregar = signal(false);
  protected linkPdf = signal<string | null>(null);
  protected carregandoPdf = signal(false);

  // renovacao: so com ACTIVE e sem outro contrato pendente/agendado pro mesmo aluno
  protected podeRenovar = signal(false);
  protected renovando = signal(false);

  protected cancelamentoAberto = signal(false);
  protected motivo = '';
  protected enviando = signal(false);
  protected readonly motivoMaximo = MOTIVO_MAXIMO;

  // pedido de cancelamento em andamento (o backend so deixa um por vez)
  protected pedidoPendente = computed<cancellationResponse | null>(
    () => this.contrato()?.cancellations?.find((c) => c.status === 'PENDING') ?? null,
  );
  protected pedidoMeu = computed(() => {
    const pedido = this.pedidoPendente();
    return !!pedido && pedido.requestedById === this.meuId;
  });

  protected statusInfo = computed(() => {
    const c = this.contrato();
    if (!c) return null;
    const pedido = this.pedidoPendente();
    if (pedido?.effectiveDate) return { texto: `Cancela em ${diaMes(pedido.effectiveDate)}`, classe: 'pendente' };
    if (c.status === 'PENDING' && this.ehMotorista) return { texto: 'Enviado', classe: 'pendente' };
    return STATUS[c.status];
  });
  protected periodicidade = computed(() => periodicidadeLabel(this.contrato()?.periodicity));
  protected aceito = computed(() => ['SCHEDULED', 'ACTIVE'].includes(this.contrato()?.status ?? ''));
  protected podeCancelar = computed(() => this.aceito() && !this.pedidoPendente());
  protected assinaturaInvalida = computed(() => (this.contrato()?.signatures ?? []).some((a) => !a.valid));
  protected enderecoEscola = computed(() => {
    const e = this.contrato()?.schoolAddress;
    if (!e) return '';
    return [e.street, e.number].filter((p) => p !== null && p !== '').join(', ');
  });

  protected readonly valor = valorEmReais;
  protected readonly data = isoParaData;
  protected readonly diaMes = diaMes;

  ngOnInit() {
    this.contractId = this.route.snapshot.paramMap.get('contractId') || '';
    this.chatId = this.route.snapshot.queryParamMap.get('chatId');
    this.ehMotorista = this.authService.getRolesFromToken().includes('ROLE_DRIVER');
    this.meuId = this.authService.getUserIdFromToken();
    this.carregar();
  }

  carregar(avisarPedido = true) {
    this.carregando.set(true);
    this.erroCarregar.set(false);
    this.contractService.getContract(this.contractId).subscribe({
      next: (contrato) => {
        this.contrato.set(contrato);
        this.carregando.set(false);
        if (contrato.documentFileKey && !this.linkPdf()) this.carregarPdf(contrato.documentFileKey);
        if (avisarPedido) this.avisarPedidoPendente();
        this.verificarRenovacao(contrato);
      },
      error: (error) => {
        console.error('Erro ao carregar o contrato:', error);
        this.erroCarregar.set(true);
        this.carregando.set(false);
      },
    });
  }

  // o link do PDF vale 15 minutos: gera ao abrir a tela
  private carregarPdf(fileKey: string) {
    this.carregandoPdf.set(true);
    this.storageService.getDownloadUrl(fileKey).subscribe({
      next: (url) => {
        this.linkPdf.set(url);
        this.carregandoPdf.set(false);
      },
      error: () => this.carregandoPdf.set(false),
    });
  }

  // Figma: o pedido em andamento aparece como aviso no topo
  private avisarPedidoPendente() {
    const pedido = this.pedidoPendente();
    if (!pedido) return;
    const dia = diaMes(pedido.effectiveDate);
    if (this.pedidoMeu()) {
      this.popUpService.show(`Cancela em ${dia}. Até lá o contrato continua valendo.`, 'warning', 'Você pediu o cancelamento');
    } else {
      const motivo = pedido.reason ? `Motivo: ${pedido.reason}. ` : '';
      this.popUpService.show(
        `${motivo}Cancela sozinho em ${dia}, ou agora se você aceitar.`,
        'warning',
        `${pedido.requestedByName} pediu o cancelamento`,
      );
    }
  }

  private verificarRenovacao(c: contractDetailsResponse) {
    this.podeRenovar.set(false);
    if (!this.ehMotorista || c.status !== 'ACTIVE' || this.pedidoPendente()) return;
    forkJoin([
      this.contractService.getMyContracts({ studentId: c.studentId, status: 'PENDING', size: 1 }),
      this.contractService.getMyContracts({ studentId: c.studentId, status: 'SCHEDULED', size: 1 }),
    ]).subscribe({
      next: ([pendentes, agendados]) =>
        this.podeRenovar.set(!pendentes.totalElements && !agendados.totalElements),
      error: () => this.podeRenovar.set(false),
    });
  }

  iniciais(nome: string | null | undefined): string {
    return iniciaisDe(nome);
  }

  vinculo(relationType: string): string {
    return RELATION_LABEL[relationType] ?? relationType;
  }

  // ---------- cancelamento ----------

  abrirCancelamento() {
    this.motivo = '';
    this.cancelamentoAberto.set(true);
    this.popUpService.show(
      'O contrato será cancelado em 30 dias, ou antes se a outra parte aceitar. Parcelas que vencerem até lá continuam devidas.',
      'warning',
      'Como funciona',
    );
  }

  fecharCancelamento() {
    if (!this.enviando()) this.cancelamentoAberto.set(false);
  }

  pedirCancelamento() {
    if (this.enviando()) return;
    const motivo = this.motivo.trim();
    // mesmo texto do backend
    if (!motivo) {
      this.popUpService.show('Informe o motivo do cancelamento', 'error', 'Dados inválidos');
      return;
    }

    this.enviando.set(true);
    this.contractService.requestCancellation(this.contractId, motivo).subscribe({
      next: (pedido) => {
        this.enviando.set(false);
        this.cancelamentoAberto.set(false);
        if (pedido.status === 'ACCEPTED' && pedido.unilateral) {
          this.popUpService.show(
            'Havia parcela vencida há 30 dias ou mais. Parcelas vencidas continuam aparecendo e podem receber baixa.',
            'warning',
            'Contrato cancelado na hora',
          );
        } else {
          this.popUpService.show(
            `Cancela em ${diaMes(pedido.effectiveDate)}. Até lá o contrato continua valendo.`,
            'warning',
            'Você pediu o cancelamento',
          );
        }
        this.carregar(false);
      },
      // 400/409 (ex.: "Tem uma viagem rodando com ... agora") vem no popup global com a mensagem do backend
      error: () => this.enviando.set(false),
    });
  }

  async aceitarCancelamento() {
    if (this.enviando()) return;
    const sim = await this.confirmacao.perguntar({
      titulo: 'Aceitar o cancelamento?',
      texto: 'O contrato é cancelado agora e isso não tem volta.',
      acao: 'Aceitar e cancelar',
    });
    if (!sim || this.enviando()) return;
    this.enviando.set(true);
    this.contractService.acceptCancellation(this.contractId).subscribe({
      next: () => {
        this.enviando.set(false);
        this.popUpService.show('O contrato foi cancelado.', 'success', 'Contrato cancelado');
        this.carregar(false);
      },
      error: () => this.enviando.set(false),
    });
  }

  async desistirCancelamento() {
    if (this.enviando()) return;
    const sim = await this.confirmacao.perguntar({
      titulo: 'Desistir do cancelamento?',
      texto: 'Seu pedido de cancelamento é retirado e o contrato continua valendo.',
      acao: 'Desistir do cancelamento',
      tom: 'azul',
    });
    if (!sim || this.enviando()) return;
    this.enviando.set(true);
    this.contractService.withdrawCancellation(this.contractId).subscribe({
      next: () => {
        this.enviando.set(false);
        this.popUpService.show('O contrato continua valendo.', 'success', 'Pedido de cancelamento retirado');
        this.carregar(false);
      },
      error: () => this.enviando.set(false),
    });
  }

  // ---------- parcelas e renovacao ----------

  parcelas() {
    this.router.navigate(['/payments'], { queryParams: { contractId: this.contractId } });
  }

  // abre a proposta no chat com o responsavel: aluno, escola, periodicidade e valor iguais; inicio = fim + 1 dia
  renovar() {
    const c = this.contrato();
    if (!c || this.renovando()) return;
    this.renovando.set(true);

    const chat$: Observable<string | null> = this.chatId ? of(this.chatId) : this.chatService.findChatWith(c.responsibleId);
    chat$.subscribe({
      next: (chatId) => {
        this.renovando.set(false);
        if (!chatId) {
          this.popUpService.show('Não encontramos a conversa com o responsável.', 'error', 'Não encontrado');
          return;
        }
        this.draft.prepararRenovacao(chatId, {
          studentId: c.studentId,
          studentName: c.studentName,
          schoolId: c.schoolId,
          periodicity: c.periodicity,
          value: c.value,
          inicio: diaSeguinte(c.endDate),
        });
        this.router.navigate(['/driver-contract', chatId]);
      },
      error: () => this.renovando.set(false),
    });
  }

  voltar() {
    if (this.chatId) {
      this.router.navigate(['/chat-details', this.chatId]);
    } else {
      this.location.back();
    }
  }

  // motorista abre o perfil do responsavel; responsavel abre o do motorista
  abrirPerfil(c: { driverId: string; responsibleId: string }) {
    if (this.ehMotorista) this.router.navigate(['/responsible-profile', c.responsibleId]);
    else this.router.navigate(['/driver-profile', c.driverId]);
  }
}
