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

  enteredName = '';
  enteredBirthdate = '';
  enteredObservacoes = '';
  maxObs = 1200;

  fechar() { this.fecharEvent.emit(); }

  voltar() { this.fecharEvent.emit(); }

  pronto() {
    // logica depois
    this.fecharEvent.emit();
  }
}