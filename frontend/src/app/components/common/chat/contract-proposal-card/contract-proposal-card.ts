import { Skeleton } from '../../../../shared/skeleton/skeleton';
import { Component, OnInit, computed, effect, inject, input, signal, untracked } from '@angular/core';
import { Router } from '@angular/router';
import { ContractService } from '../../../../services/contract.service';
import { PopUpService } from '../../../../shared/pop-up';
import {
  ContractStatus,
  contractDetailsResponse,
  diaMes,
  periodicidadeLabel,
} from '../../../../services/dto/contract/contract.dto';

import { BotaoCarregandoDirective } from '../../../../shared/botao-carregando/botao-carregando.directive';
// WITHDRAWN so existe no front: o backend manda REJECTION "Proposta Retirada" quando o motorista retira
export type DecisaoContrato = ContractStatus | 'WITHDRAWN';

// card da mensagem PROPOSAL no chat (Figma "Etapa 2 · Contrato completo"): resumo do contrato, link pra analise,
// o responsavel decide (aceitar/negar) e o motorista pode retirar enquanto estiver pendente
@Component({
  selector: 'app-contract-proposal-card',
  imports: [BotaoCarregandoDirective, Skeleton],
  standalone: true,
  templateUrl: './contract-proposal-card.html',
  styleUrl: './contract-proposal-card.css',
})
export class ContractProposalCard implements OnInit {
  readonly chatId = input.required<string>();
  readonly contractId = input.required<string>();
  readonly hora = input('');
  // so o responsavel decide (o backend tambem barra o motorista); o motorista e quem pode retirar
  readonly podeDecidir = input(false);
  // decisao que chegou depois pelo chat (mensagem APPROVAL/REJECTION)
  readonly decisao = input<DecisaoContrato | null>(null);

  private contractService = inject(ContractService);
  private popUpService = inject(PopUpService);
  private router = inject(Router);

  protected contrato = signal<contractDetailsResponse | null>(null);
  protected carregando = signal(true);
  protected erroCarregar = signal(false);

  protected decisaoAberta = signal(false);
  protected retiradaAberta = signal(false);
  protected enviando = signal(false);
  // retirada feita aqui (antes da mensagem do chat chegar)
  private retiradaLocal = signal(false);

  protected status = computed<DecisaoContrato | null>(() => {
    if (this.retiradaLocal() || this.decisao() === 'WITHDRAWN') return 'WITHDRAWN';
    const daApi = this.contrato()?.status ?? null;
    // a decisao do chat vale enquanto a API ainda diz pendente; depois de recarregar, vale o status real
    if (this.decisao() && (!daApi || daApi === 'PENDING')) return this.decisao();
    return daApi;
  });
  protected pendente = computed(() => this.status() === 'PENDING');
  protected aceita = computed(() => ['SCHEDULED', 'ACTIVE', 'INACTIVE'].includes(this.status() ?? ''));
  protected periodicidade = computed(() => periodicidadeLabel(this.contrato()?.periodicity));

  protected titulo = computed(() => {
    const status = this.status();
    if (status === 'WITHDRAWN') return 'Proposta Retirada';
    if (this.aceita()) return 'Proposta aceita';
    if (status === 'PENDING' && !this.podeDecidir()) return 'Proposta enviada';
    return 'Contrato enviado';
  });

  protected texto = computed(() => {
    const status = this.status();
    const c = this.contrato();
    if (status === 'WITHDRAWN') return 'O motorista retirou esta proposta. Ela não pode mais ser aceita.';
    if (status === 'SCHEDULED' && c) return `Agendado: começa em ${diaMes(c.startDate)}.`;
    if (this.aceita()) return 'O contrato foi aceito.';
    if (status === 'PENDING' && !this.podeDecidir()) return 'Aguardando a resposta do responsável. Você ainda pode retirar.';
    if (this.podeDecidir()) return 'Um contrato foi enviado para você. Confira as informações abaixo.';
    return `Você enviou um contrato${c ? ' para ' + c.responsibleName : ''}. Confira as informações abaixo.`;
  });

  protected resultado = computed(() => {
    const status = this.status();
    if (status === 'WITHDRAWN') return { texto: 'Retirada', classe: 'neutro' };
    if (this.aceita()) return { texto: 'Aceita', classe: 'aceito' };
    if (status === 'CANCELED') return { texto: 'Negado', classe: 'negado' };
    return null;
  });

  constructor() {
    // aceito pelo chat: recarrega pra saber se ficou agendado (SCHEDULED) ou ativo
    effect(() => {
      if (this.decisao() === 'ACTIVE' && untracked(this.contrato)?.status === 'PENDING') {
        untracked(() => this.carregar());
      }
    });
  }

  ngOnInit() {
    this.carregar();
  }

  carregar() {
    this.carregando.set(true);
    this.erroCarregar.set(false);
    this.contractService.getContract(this.contractId()).subscribe({
      next: (contrato) => {
        this.contrato.set(contrato);
        this.carregando.set(false);
      },
      error: (error) => {
        console.error('Erro ao carregar o contrato:', error);
        this.erroCarregar.set(true);
        this.carregando.set(false);
      },
    });
  }

  visualizar() {
    this.router.navigate(['/contract', this.contractId()], { queryParams: { chatId: this.chatId() } });
  }

  abrirDecisao() {
    this.decisaoAberta.set(true);
  }

  fecharDecisao() {
    if (!this.enviando()) this.decisaoAberta.set(false);
  }

  abrirRetirada() {
    this.retiradaAberta.set(true);
  }

  fecharRetirada() {
    if (!this.enviando()) this.retiradaAberta.set(false);
  }

  decidir(aceitar: boolean) {
    if (this.enviando()) return;
    this.enviando.set(true);

    const pedido = aceitar
      ? this.contractService.acceptContract(this.chatId(), this.contractId())
      : this.contractService.rejectContract(this.chatId(), this.contractId());

    pedido.subscribe({
      next: () => {
        this.enviando.set(false);
        this.decisaoAberta.set(false);
        this.popUpService.show(aceitar ? 'Contrato aceito.' : 'Contrato negado.', 'success', 'Decisão enviada');
        // o status real (agendado ou ativo) vem do backend
        this.carregar();
      },
      error: (error) => {
        console.error('Erro ao decidir o contrato:', error);
        this.enviando.set(false);
        this.decisaoAberta.set(false);
        // 409 (ex.: "A data de início desta proposta já passou...") e 403 com a mensagem do backend
        this.popUpService.showError(error, 'Não foi possível enviar a decisão. Tente novamente.');
        if (error?.status === 409) this.carregar();
      },
    });
  }

  retirar() {
    if (this.enviando()) return;
    this.enviando.set(true);
    this.contractService.withdrawProposal(this.chatId(), this.contractId()).subscribe({
      next: () => {
        this.enviando.set(false);
        this.retiradaAberta.set(false);
        this.retiradaLocal.set(true);
        this.popUpService.show('O responsável foi avisado.', 'success', 'Proposta retirada');
      },
      // o popup global mostra o erro do backend (ex.: 409 ja decidida)
      error: () => {
        this.enviando.set(false);
        this.retiradaAberta.set(false);
        this.carregar();
      },
    });
  }
}
