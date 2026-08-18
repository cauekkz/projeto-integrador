import { Component, EventEmitter, Output } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { CodeInput } from '../code-input/code-input';
import { Header } from '../../components/header/header';

@Component({
  selector: 'app-forgot-password',
  standalone: true,
  imports: [FormsModule, CodeInput,Header],
  templateUrl: './forgot-password.html',
  styleUrl: './forgot-password.css',
})
export class ForgotPassword {
  @Output() voltarEvent = new EventEmitter<void>();

  etapa: 1 | 2 | 3 = 1;

  enteredEmail = '';
  enteredCode = '';
  enteredNewPass = '';
  enteredConfirmPass = '';

  emailInvalido = false;
  codeInvalido = false;
  passInvalido = false;
  confirmPassInvalido = false;
  passwordsDontMatch = false;

  codigoCampos = ['', '', '', '', '', ''];

  voltar() {
    if (this.etapa === 1) {
      this.voltarEvent.emit();
    } else {
      this.etapa = (this.etapa - 1) as 1 | 2 | 3;
    }
  }

  enviarEmail() {
    this.emailInvalido = !this.enteredEmail;
    if (!this.emailInvalido) this.etapa = 2;
  }

  onInput(event: Event, index: number) {
    const input = event.target as HTMLInputElement;
    input.classList.remove('animate');
    void input.offsetWidth;
    input.classList.add('animate');
    if (input.value.length === 1) {
      const inputs = document.querySelectorAll('.code-input');
      const next = inputs[index + 1] as HTMLInputElement;
      if (next) next.focus();
    }
    this.codigoCampos[index] = input.value;
  }

  onKeydown(event: KeyboardEvent, index: number) {
    const input = event.target as HTMLInputElement;
    if (event.key === 'Backspace' && input.value === '') {
      const inputs = document.querySelectorAll('.code-input');
      const prev = inputs[index - 1] as HTMLInputElement;
      if (prev) prev.focus();
    }
  }

  enviarCodigo(codigo: string) {
    this.enteredCode = codigo;
    this.etapa = 3;
  }

  novaSenha() {
    this.passInvalido = !this.enteredNewPass;
    this.confirmPassInvalido = !this.enteredConfirmPass;
    this.passwordsDontMatch = this.enteredNewPass !== this.enteredConfirmPass;
    if (!this.passInvalido && !this.confirmPassInvalido && !this.passwordsDontMatch) {
      this.voltarEvent.emit();
    }
  }
}
