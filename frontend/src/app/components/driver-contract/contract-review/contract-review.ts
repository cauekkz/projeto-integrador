import { Component, inject, input, signal } from '@angular/core';
import { ContractDraftService, iniciais } from '../../../pages/driver-contract/contract-draft.service';

// resumo so de leitura do que ja foi preenchido, dentro de uma secao expansivel
// "aluno" = responsavel, dependente e escola; "contrato" = periodicidade, valor e vigencia
@Component({
  selector: 'app-contract-review',
  standalone: true,
  templateUrl: './contract-review.html',
  styleUrls: ['../contract-form.css', './contract-review.css'],
})
export class ContractReview {
  readonly secao = input.required<'aluno' | 'contrato'>();

  protected draft = inject(ContractDraftService);
  protected iniciais = iniciais;
  protected aberto = signal(false);

  alternar() {
    this.aberto.update((valor) => !valor);
  }
}
