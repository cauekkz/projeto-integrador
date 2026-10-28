import { Component, inject } from '@angular/core';
import { DateMaskDirective } from '../../../shared/directives';
import { formatarData, limparMascara } from '../../../shared/utils';
import { ContractPeriodicity, PERIODICITY_OPTIONS } from '../../../services/dto/contract/contract.dto';
import {
  ContractDraftService,
  formatarReais,
  VALOR_MAXIMO_CENTAVOS,
} from '../../../pages/driver-contract/contract-draft.service';
import { ContractReview } from '../contract-review/contract-review';

// etapa 2: periodicidade, valor e vigencia
@Component({
  selector: 'app-contract-details',
  standalone: true,
  imports: [DateMaskDirective, ContractReview],
  templateUrl: './contract-details.html',
  styleUrl: '../contract-form.css',
})
export class ContractDetails {
  protected draft = inject(ContractDraftService);
  protected periodicidades = PERIODICITY_OPTIONS;

  selecionarPeriodicidade(event: Event) {
    this.draft.periodicity.set((event.target as HTMLSelectElement).value as ContractPeriodicity);
  }

  // digita como caixa eletronico: os digitos viram centavos (1234 -> R$ 12,34)
  digitarValor(event: Event) {
    const input = event.target as HTMLInputElement;
    const digitos = limparMascara(input.value).replace(/^0+/, '').slice(0, 10);
    const centavos = Math.min(Number(digitos || '0'), VALOR_MAXIMO_CENTAVOS);

    this.draft.valorCentavos.set(centavos);
    input.value = formatarReais(centavos);
  }

  // o appDateMask formata o input; formata aqui tambem pra nao depender da ordem dos listeners
  digitarData(campo: 'inicio' | 'fim', event: Event) {
    const valor = formatarData((event.target as HTMLInputElement).value);
    (campo === 'inicio' ? this.draft.dataInicio : this.draft.dataFim).set(valor);
  }
}
