import { Component, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ContractDraftService, iniciais } from '../../../pages/driver-contract/contract-draft.service';

// etapa 1: responsavel do chat + escolha do dependente e da escola
@Component({
  selector: 'app-contract-student-data',
  standalone: true,
  imports: [RouterLink],
  templateUrl: './contract-student-data.html',
  styleUrls: ['../contract-form.css', './contract-student-data.css'],
})
export class ContractStudentData {
  protected draft = inject(ContractDraftService);
  protected iniciais = iniciais;

  selecionarAluno(event: Event) {
    this.draft.studentId.set((event.target as HTMLSelectElement).value);
  }

  selecionarEscola(event: Event) {
    this.draft.schoolId.set((event.target as HTMLSelectElement).value);
  }
}
