import { Component, EventEmitter, Output } from '@angular/core';
import { FormsModule } from '@angular/forms';

@Component({
  selector: 'app-add-student',
  standalone: true,
  imports: [FormsModule],
  templateUrl: './add-student.html',
  styleUrl: './add-student.css',
})
export class AddStudent {
  @Output() fecharEvent = new EventEmitter<void>();

  // controla qual "tela" aparece dentro do componente
  etapa: 'form' | 'code' = 'form';

  enteredName = '';
  enteredBirthdate = '';
  enteredObservacoes = '';
  maxObs = 1200;
  mostrarBottomSheet = false;

  codigoGerado = '';
  copiado = false;

  fechar() {
    this.fecharEvent.emit();
  }

  voltar() {
    if (this.etapa === 'code') {
      this.etapa = 'form';
      return;
    }
    this.fecharEvent.emit();
  }

  pronto() {
    // aqui depois entra a chamada pro back pra pegar o codigoGerado de verdade
    this.etapa = 'code';
  }

  copiarCodigo() {
    if (!this.codigoGerado) return;
    navigator.clipboard.writeText(this.codigoGerado).then(() => {
      this.copiado = true;
      setTimeout(() => (this.copiado = false), 2000);
    });
  }

  concluir() {
    this.fecharEvent.emit();
  }

  abrirBottomSheet() {
    this.mostrarBottomSheet = true;
  }
  fecharBottomSheet() {
    this.mostrarBottomSheet = false;
  }
}
