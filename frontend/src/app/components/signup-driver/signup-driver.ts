import { Component, EventEmitter, Output } from '@angular/core';

@Component({
  selector: 'app-signup-driver',
  standalone: true,
  imports: [],
  templateUrl: './signup-driver.html',
  styleUrl: './signup-driver.css',
})
export class SignupDriver {
  @Output() cadastroConcluidoEvent = new EventEmitter<void>();
  @Output() voltarEvent = new EventEmitter<void>();

  etapa = 1;

  proximaEtapa() {
    this.etapa = 2;
  }
  voltarEtapa() {
    this.etapa = 1;
  }

  finalizarCadastro() {
    this.cadastroConcluidoEvent.emit();
  }

  goBack() {
    this.voltarEvent.emit();
  }
}
