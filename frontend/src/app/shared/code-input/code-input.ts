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
  @Input() desabilitado = false; // trava inputs e botao enquanto a tela envia o codigo
  @Output() codigoVerificado = new EventEmitter<string>();
  @Output() reenviarEvent = new EventEmitter<void>();

  codes: string[] = [];
  invalido = false;
  @Input() separadores: number[] = []; // posições onde aparece o traço ex: [3, 6] para XXX-XXX-XXX

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

  // colar o codigo inteiro em qualquer caixa espalha os caracteres (ignora traco/espaco)
  onPaste(event: ClipboardEvent, index: number) {
    const texto = event.clipboardData?.getData('text') ?? '';
    const caracteres = texto.replace(/[^A-Za-z0-9]/g, '').split('');
    if (caracteres.length <= 1) return;

    event.preventDefault();
    const inputs = document.querySelectorAll<HTMLInputElement>('.code-input');
    let ultimo = index;
    for (let i = 0; i < caracteres.length && index + i < this.length; i++) {
      this.codes[index + i] = caracteres[i];
      if (inputs[index + i]) inputs[index + i].value = caracteres[i];
      ultimo = index + i;
    }
    inputs[Math.min(ultimo + 1, this.length - 1)]?.focus();
  }

  verificar() {
    if (this.desabilitado) return;
    const codigo = this.codes.join('');
    this.invalido = codigo.length < this.length;
    if (!this.invalido) this.codigoVerificado.emit(codigo);
  }

  reenviar() {
    this.reenviarEvent.emit();
  }
}
