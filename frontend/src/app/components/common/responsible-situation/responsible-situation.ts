import { Component, Input, inject } from '@angular/core';
import { Router } from '@angular/router';
import { responsibleProfile } from '../../../services/dto/profile/profile.dto';
import { ContractStatus, RELATION_LABEL } from '../../../services/dto/contract/contract.dto';

const SELO: Record<ContractStatus, { texto: string; classe: string }> = {
  PENDING: { texto: 'Pendente', classe: 'pendente' },
  SCHEDULED: { texto: 'Agendado', classe: 'agendado' },
  ACTIVE: { texto: 'Ativo', classe: 'ativo' },
  INACTIVE: { texto: 'Inativo', classe: 'inativo' },
  CANCELED: { texto: 'Cancelado', classe: 'cancelado' },
};

// situacao financeira + contratos dos filhos (Figma 1057:1016 no proprio perfil, 1057:1059/1100 pro motorista)
@Component({
  selector: 'app-responsible-situation',
  standalone: true,
  templateUrl: './responsible-situation.html',
  styleUrls: ['../../../shared/folha.css', '../../../shared/perfil.css', './responsible-situation.css'],
})
export class ResponsibleSituation {
  @Input({ required: true }) perfil!: responsibleProfile;
  // motorista: so as parcelas e os filhos com ele; proprio: de todos os contratos
  @Input() modo: 'motorista' | 'proprio' = 'motorista';

  private router = inject(Router);
  protected readonly relacao = RELATION_LABEL;

  selo(status: ContractStatus) {
    return SELO[status] ?? { texto: status, classe: 'inativo' };
  }

  atrasadas(): string {
    const n = this.perfil.overduePayments;
    return n === 1 ? '1 parcela atrasada' : `${n} parcelas atrasadas`;
  }

  abrirContrato(contractId: string) {
    this.router.navigate(['/contract', contractId]);
  }

  // atraso: abre a aba "Atrasadas"; o motorista sem atraso ve todas
  verParcelas() {
    const atrasado = this.perfil.financialStatus === 'OVERDUE';
    this.router.navigate(['/payments'], atrasado ? { queryParams: { status: 'OVERDUE' } } : {});
  }
}
