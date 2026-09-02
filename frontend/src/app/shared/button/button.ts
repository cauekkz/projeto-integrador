import { Component, EventEmitter, Input, Output } from '@angular/core';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-button',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './button.html',
  styleUrl: './button.css',
})
export class Button {
  @Input() texto = '';
  @Input() tipo: 'primary' | 'secondary' | 'danger' = 'primary';
  @Input() posicao: 'top' | 'middle' | 'bottom' | 'sticky' = 'middle';
  @Input() type: 'button' | 'submit' = 'button';
  @Input() disabled = false;
  @Output() clickEvent = new EventEmitter<void>();

  onClick() {
    if (!this.disabled) this.clickEvent.emit();
  }
}
