import { Component, EventEmitter, Input, Output } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { AuthService } from '../../../services/auth.service';
import { Header } from '../../../shared/header/header';
import { Button } from '../../../shared/button/button';
import { ForgotPassword } from '../../../components/login-signup/forgot-password/forgot-password';
import { CpfMaskDirective, PasswordToggleDirective } from '../../../shared/directives';
import { PopUpService } from '../../../shared/pop-up';
import { limparMascara } from '../../../shared/utils';
import { EmailCode } from '../email-code/email-code';
import { HttpErrorResponse } from '@angular/common/http';

// senha certa, mas a conta ainda nao confirmou o e-mail (o backend responde 400 com essa frase)
const EMAIL_NAO_VERIFICADO = 'Verifique seu e-mail';

@Component({
  selector: 'app-login-form',
  standalone: true,
  imports: [FormsModule, Header, Button, ForgotPassword, CpfMaskDirective, PasswordToggleDirective, EmailCode],
  templateUrl: './login-form.html',
  styleUrl: './login-form.css',
})
export class LoginForm {
  @Input() tipoUsuario = '';

  @Output() voltarEvent = new EventEmitter<void>();

  constructor(
    private router: Router,
    private authService: AuthService,
    private popUpService: PopUpService,
  ) {}

  enteredCPF = '';
  enteredPass = '';

  cpfInvalido = false;
  passInvalido = false;

  mostrarForgotPassword = false;

  // confirmar o e-mail depois de sair do cadastro sem digitar o codigo
  etapaEmail: 'nenhuma' | 'email' | 'codigo' = 'nenhuma';
  enteredEmail = '';
  emailInvalido = false;
  enviandoCodigo = false;

  verification(value: string): boolean {
    return value.trim() === '';
  }

  voltar() {
    this.router.navigate(['/']);
  }

  irParaCadastro() {
    this.router.navigate(['/signup', this.tipoUsuario]);
  }

  login() {
    this.cpfInvalido = this.verification(this.enteredCPF);
    this.passInvalido = this.verification(this.enteredPass);

    if (this.cpfInvalido || this.passInvalido) {
      this.popUpService.show('Por favor, preencha o CPF e a senha para entrar.', 'warning', 'Campos Obrigatórios');
      return;
    }

    this.authService
      .login({
        cpf: limparMascara(this.enteredCPF),
        passwordHash: this.enteredPass,
      })
      .subscribe({
        next: (response) => {
          this.authService.saveTokens(response);

          // destino vem da role do token, nao do :tipo da url
          const home = this.authService.getHomeRoute();
          if (!home) {
            this.authService.logout();
            this.popUpService.show('Não foi possível identificar o tipo da sua conta.', 'error', 'Erro no login');
            return;
          }

          this.router.navigate([home]);
        },
        // o popup global ja mostra o erro (senha errada, 429...); e-mail nao confirmado abre a confirmacao
        error: (err: unknown) => {
          if (err instanceof HttpErrorResponse && err.status === 400 && String(err.error?.message ?? err.error ?? '').includes(EMAIL_NAO_VERIFICADO)) {
            this.etapaEmail = 'email';
          }
        },
      });
  }

  enviarCodigoEmail() {
    this.enteredEmail = this.enteredEmail.trim();
    this.emailInvalido = !this.enteredEmail;
    if (this.emailInvalido || this.enviandoCodigo) return;

    this.enviandoCodigo = true;
    this.authService.sendCode(this.enteredEmail).subscribe({
      next: () => {
        this.enviandoCodigo = false;
        this.etapaEmail = 'codigo';
      },
      error: () => (this.enviandoCodigo = false),
    });
  }

  // e-mail confirmado: entra direto com o CPF e a senha que ja estao digitados
  emailVerificado() {
    this.etapaEmail = 'nenhuma';
    this.popUpService.show('E-mail confirmado!', 'success', 'Tudo certo');
    this.login();
  }
}
