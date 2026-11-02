import { Component, EventEmitter, Output } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { CodeInput } from '../../../shared/code-input/code-input';
import { Header } from '../../../shared/header/header';
import { Button } from '../../../shared/button/button';
import { AuthService } from '../../../services/auth.service';
import { PopUpService } from '../../../shared/pop-up';
import { PasswordToggleDirective } from '../../../shared/directives';

// mesma regra do backend: maiuscula, minuscula, numero e caractere especial, 8+ caracteres
const SENHA_FORTE = /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[@$!%*?&]).{8,}$/;

// esqueci a senha (publico, sem login): 1) e-mail recebe o codigo  2) digita o codigo  3) senha nova
// o codigo so e conferido no backend junto com a senha nova, na etapa 3
@Component({
  selector: 'app-forgot-password',
  standalone: true,
  imports: [FormsModule, CodeInput, Header, Button, PasswordToggleDirective],
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
  passInvalido = false;
  passFraca = false;
  confirmPassInvalido = false;
  passwordsDontMatch = false;
  enviando = false;

  constructor(
    private authService: AuthService,
    private popUpService: PopUpService,
  ) {}

  voltar() {
    if (this.etapa === 1) {
      this.voltarEvent.emit();
    } else {
      this.etapa = (this.etapa - 1) as 1 | 2 | 3;
    }
  }

  enviarEmail() {
    this.enteredEmail = this.enteredEmail.trim();
    this.emailInvalido = !this.enteredEmail;
    if (this.emailInvalido || this.enviando) return;

    this.enviando = true;
    this.authService.sendForgotPasswordCode(this.enteredEmail).subscribe({
      next: () => {
        this.enviando = false;
        this.etapa = 2;
      },
      error: () => {
        this.enviando = false;
      },
    });
  }

  enviarCodigo(codigo: string) {
    this.enteredCode = codigo;
    this.etapa = 3;
  }

  novaSenha() {
    this.passInvalido = !this.enteredNewPass;
    this.passFraca = !this.passInvalido && !SENHA_FORTE.test(this.enteredNewPass);
    this.confirmPassInvalido = !this.enteredConfirmPass;
    this.passwordsDontMatch = !this.confirmPassInvalido && this.enteredNewPass !== this.enteredConfirmPass;

    if (this.passInvalido || this.passFraca || this.confirmPassInvalido || this.passwordsDontMatch || this.enviando) {
      return;
    }

    this.enviando = true;
    this.authService
      .resetPassword({
        email: this.enteredEmail,
        code: this.enteredCode,
        password: this.enteredNewPass,
        confirmPassword: this.enteredConfirmPass,
      })
      .subscribe({
        next: () => {
          this.enviando = false;
          this.popUpService.show('Senha alterada! Entre com a nova senha.', 'success', 'Tudo certo');
          this.voltarEvent.emit();
        },
        error: () => {
          // o popup global mostra a mensagem do backend (ex.: codigo incorreto ou expirado)
          this.enviando = false;
        },
      });
  }
}
