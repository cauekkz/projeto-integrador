import { Component, EventEmitter, Output } from '@angular/core';

@Component({
  selector: 'app-signup-responsible',
  standalone: true,
  imports: [],
  templateUrl: './signup-responsible.html',
  styleUrl: './signup-responsible.css',
})
export class SignupResponsible {
  @Output() cadastroConcluidoEvent = new EventEmitter<void>();
  @Output() voltarEvent = new EventEmitter<void>();

  concluir() {
    this.cadastroConcluidoEvent.emit();
  }

  voltar() {
    this.voltarEvent.emit();
  }
}
