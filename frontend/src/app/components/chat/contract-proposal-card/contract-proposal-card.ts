import { Component, OnInit, computed, inject, input, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { Router } from '@angular/router';
import { ContractService } from '../../../services/contract.service';
import { PopUpService } from '../../../shared/pop-up';
import {
  ContractStatus,
  contractDetailsResponse,
  periodicidadeLabel,
} from '../../../services/dto/contract/contract.dto';

const STATUS: Record<ContractStatus, { texto: string; classe: string }> = {
  PENDING: { texto: 'Aguardando decisão', classe: 'pendente' },
  ACTIVE: { texto: 'Aceito', classe: 'aceito' },
  CANCELED: { texto: 'Negado', classe: 'negado' },
  INACTIVE: { texto: 'Inativo', classe: 'neutro' },
  SUSPENDED: { texto: 'Suspenso', classe: 'neutro' },
  OVERDUE: { texto: 'Em atraso', classe: 'negado' },
};

// card da mensagem PROPOSAL no chat: resumo do contrato, link pra analise e (pro responsavel) aceitar ou negar
@Component({
  selector: 'app-contract-proposal-card',
  standalone: true,
  templateUrl: './contract-proposal-card.html',
  styleUrl: './contract-proposal-card.css',
})
export class ContractProposalCard implements OnInit {
  readonly chatId = input.required<string>();
  readonly contractId = input.required<string>();
  readonly hora = input('');
  // so o responsavel decide (o backend tambem barra o motorista)
  readonly podeDecidir = input(false);
  // decisao que chegou depois pelo chat (mensagem APPROVAL/REJECTION)
  readonly decisao = input<ContractStatus | null>(null);

  private contractService = inject(ContractService);
  private popUpService = inject(PopUpService);
  private router = inject(Router);

  protected contrato = signal<contractDetailsResponse | null>(null);
  protected carregando = signal(true);
  protected erroCarregar = signal(false);

  protected decisaoAberta = signal(false);
  protected decidindo = signal(false);
  protected erroDecisao = signal('');

  // o que voltou da API vale ate chegar uma decisao pelo chat
  protected status = computed<ContractStatus | null>(() => this.decisao() ?? this.contrato()?.status ?? null);
  protected statusInfo = computed(() => {
    const status = this.status();
    return status ? STATUS[status] : null;
  });
  protected pendente = computed(() => this.status() === 'PENDING');
  protected periodicidade = computed(() => periodicidadeLabel(this.contrato()?.periodicity));

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
    this.erroDecisao.set('');
    this.decisaoAberta.set(true);
  }

  fecharDecisao() {
    if (!this.decidindo()) this.decisaoAberta.set(false);
  }

  decidir(aceitar: boolean) {
    if (this.decidindo()) return;

    this.decidindo.set(true);
    this.erroDecisao.set('');

    const pedido = aceitar
      ? this.contractService.acceptContract(this.chatId(), this.contractId())
      : this.contractService.rejectContract(this.chatId(), this.contractId());

    pedido.subscribe({
      next: () => {
        const atual = this.contrato();
        if (atual) this.contrato.set({ ...atual, status: aceitar ? 'ACTIVE' : 'CANCELED' });
        this.decidindo.set(false);
        this.decisaoAberta.set(false);
        this.popUpService.show(aceitar ? 'Contrato aceito.' : 'Contrato negado.', 'success', 'Decisão enviada');
      },
      error: (error) => {
        console.error('Erro ao decidir o contrato:', error);
        this.decidindo.set(false);
        const status = error instanceof HttpErrorResponse ? error.status : 0;
        if (status === 409) {
          this.erroDecisao.set('Este contrato já foi decidido.');
          this.carregar();
        } else if (status === 403) {
          this.erroDecisao.set('Só o responsável do contrato pode decidir.');
        } else {
          this.erroDecisao.set('Não foi possível enviar a decisão. Tente novamente.');
        }
      },
    });
  }
}
