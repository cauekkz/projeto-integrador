import { Component, EventEmitter, Output } from '@angular/core';

@Component({
  selector: 'app-signup-cnh-form',
  standalone: true,
  imports: [],
  templateUrl: './signup-cnh-form.html',
  styleUrl: './signup-cnh-form.css',
})
export class SignupCnhForm {
  @Output() prontoEvent = new EventEmitter<void>();
  @Output() voltarEvent = new EventEmitter<void>();

  mostrarBottomSheet = false;

  abrirBottomSheet() {
    this.mostrarBottomSheet = true;
  }

  fecharBottomSheet() {
    this.mostrarBottomSheet = false;
  }

  onUploadConcluido() {
    this.mostrarBottomSheet = false;
    this.prontoEvent.emit();
  }

  pronto() {
    this.prontoEvent.emit();
  }

  voltar() {
    this.voltarEvent.emit();
  }
}
