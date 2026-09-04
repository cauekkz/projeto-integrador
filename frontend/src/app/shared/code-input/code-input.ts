import { Component, EventEmitter, Input, Output, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';

@Component({
  selector: 'app-code-input',
  standalone: true,
  imports: [FormsModule],
  templateUrl: './code-input.html',
  styleUrl: './code-input.css',
})
export class CodeInput implements OnInit {
  @Input() instrucao = '';
  @Input() mostrarReenviar = false;
  @Input() length = 6;
  @Input() textoBotao = 'Verificar';
  @Output() codigoVerificado = new EventEmitter<string>();
  @Output() reenviarEvent = new EventEmitter<void>();

  codes: string[] = [];
  invalido = false;

  ngOnInit() {
    this.codes = Array(this.length).fill('');
  }

  onInput(event: Event, index: number) {
    const input = event.target as HTMLInputElement;
    input.classList.remove('animate');
    void input.offsetWidth;
    input.classList.add('animate');
    this.codes[index] = input.value;
    if (input.value.length === 1) {
      const inputs = document.querySelectorAll('.code-input');
      const next = inputs[index + 1] as HTMLInputElement;
      if (next) next.focus();
    }
  }

  onKeydown(event: KeyboardEvent, index: number) {
    const input = event.target as HTMLInputElement;
    if (event.key === 'Backspace' && input.value === '') {
      const inputs = document.querySelectorAll('.code-input');
      const prev = inputs[index - 1] as HTMLInputElement;
      if (prev) prev.focus();
    }
  }

  verificar() {
    const codigo = this.codes.join('');
    this.invalido = codigo.length < this.length;
    if (!this.invalido) this.codigoVerificado.emit(codigo);
  }

  reenviar() {
    this.reenviarEvent.emit();
  }
}
