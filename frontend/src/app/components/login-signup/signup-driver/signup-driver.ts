import { Component, EventEmitter, Output } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RegisterService } from '../../../services/register.service';
import { AuthService } from '../../../services/auth.service';
import { Header } from '../../../shared/header/header';
import { Button } from '../../../shared/button/button';
import { PhoneMaskDirective, PasswordToggleDirective } from '../../../shared/directives';
import { TermsAccept } from '../terms-accept/terms-accept';
import { PopUpService } from '../../../shared/pop-up';
import { limparMascara } from '../../../shared/utils';

@Component({
  selector: 'app-signup-driver',
  standalone: true,
  imports: [FormsModule, Header, Button, PhoneMaskDirective, PasswordToggleDirective, TermsAccept],
  templateUrl: './signup-driver.html',
  styleUrl: './signup-driver.css',
})
export class SignupDriver {
  @Output() cadastroConcluidoEvent = new EventEmitter<{
    email: string;
  }>();

  @Output() voltarEvent = new EventEmitter<void>();

  constructor(
    protected registerService: RegisterService,
    private authService: AuthService,
    private popUpService: PopUpService,
  ) {}

  emailInvalido = false;
  passInvalido = false;
  confirmPassInvalido = false;
  phoneInvalido = false;
  passwordsDontMatch = false;

  enteredEmail = '';
  enteredPass = '';
  enteredConfirmPass = '';
  enteredPhone = '';
  // versao dos termos marcada no checkbox (o backend exige a atual no cadastro)
  versaoTermos: string | null = null;

  etapa = 1;

  voltar() {
    this.voltarEvent.emit();
  }

  verification(value: string): boolean {
    return value.trim() === '';
  }

  signup() {
    this.passwordsDontMatch = false;

    this.emailInvalido = this.verification(this.enteredEmail);
    this.passInvalido = this.verification(this.enteredPass);
    this.confirmPassInvalido = this.verification(this.enteredConfirmPass);
    this.phoneInvalido = this.verification(this.enteredPhone);

    if (
      this.emailInvalido ||
      this.passInvalido ||
      this.confirmPassInvalido ||
      this.phoneInvalido
    ) {
      this.popUpService.show('Por favor, preencha todos os campos obrigatórios.', 'warning', 'Campos Incompletos');
      return;
    }

    if (this.enteredPass !== this.enteredConfirmPass) {
      this.passwordsDontMatch = true;
      this.popUpService.show('As senhas digitadas não coincidem.', 'error', 'Senhas Incompatíveis');
      return;
    }

    if (!this.versaoTermos) {
      this.popUpService.show('Aceite os Termos de Uso e a Política de Privacidade para continuar.', 'warning', 'Termos de Uso');
      return;
    }

    if (!this.registerService.selectedFile) {
      this.popUpService.show('É necessário anexar o arquivo PDF da CNH antes de continuar.', 'warning', 'CNH Obrigatória');
      return;
    }

    const data = {
      documentPdf: this.registerService.selectedFile,
      email: this.enteredEmail,
      password: this.enteredPass,
      confirmPassword: this.enteredConfirmPass,
      phone: limparMascara(this.enteredPhone),
      acceptedTermsVersion: this.versaoTermos,
    };

    this.authService.createDriver(data).subscribe({
      next: () => {
        this.cadastroConcluidoEvent.emit({
          email: this.enteredEmail,
        });
      },
      // o popup global mostra a mensagem do backend (CNH recusada, e-mail em uso, senha fraca...)
      error: () => {},
    });
  }
}
