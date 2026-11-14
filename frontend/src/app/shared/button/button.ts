import { Component, EventEmitter, Input, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { BotaoCarregandoDirective } from '../botao-carregando/botao-carregando.directive';

@Component({
  selector: 'app-button',
  standalone: true,
  imports: [CommonModule, BotaoCarregandoDirective],
  templateUrl: './button.html',
  styleUrl: './button.css',
})
export class Button {
  @Input() texto = '';
  @Input() tipo: 'primary' | 'secondary' | 'danger' = 'primary';
  @Input() posicao: 'top' | 'middle' | 'bottom' | 'sticky' = 'middle';
  @Input() type: 'button' | 'submit' = 'button';
  @Input() disabled = false;
  // acao em andamento: barra pintando o botao ([appCarregando]) e clique travado
  @Input() carregando = false;
  @Output() clickEvent = new EventEmitter<void>();

  onClick() {
    if (!this.disabled && !this.carregando) this.clickEvent.emit();
  }
}
