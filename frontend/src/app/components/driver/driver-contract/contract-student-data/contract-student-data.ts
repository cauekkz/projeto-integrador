import { Component, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ContractDraftService, iniciais } from '../../../../pages/driver/driver-contract/contract-draft.service';
import { fotosQuebradas } from '../../../../shared/iniciais/iniciais';

// etapa 1: responsavel do chat + escolha do dependente e da escola
@Component({
  selector: 'app-contract-student-data',
  standalone: true,
  imports: [RouterLink],
  templateUrl: './contract-student-data.html',
  styleUrls: ['../contract-form.css', './contract-student-data.css'],
})
export class ContractStudentData {
  // foto que nao carrega (link expirado) volta pras iniciais
  protected fotos = fotosQuebradas();
  protected draft = inject(ContractDraftService);
  protected iniciais = iniciais;

  selecionarAluno(event: Event) {
    this.draft.studentId.set((event.target as HTMLSelectElement).value);
  }

  selecionarEscola(event: Event) {
    this.draft.schoolId.set((event.target as HTMLSelectElement).value);
  }
}
