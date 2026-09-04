import { Component, EventEmitter, Output } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RegisterService } from '../../../services/register.service';
import { AuthService } from '../../../services/auth.service';
import { Header } from '../../../shared/header/header';
import { Button } from '../../../shared/button/button';
import { PhoneMaskDirective, NameFormatDirective } from '../../../shared/directives';
import { PopUpService } from '../../../shared/pop-up';
import { jwtDecode } from 'jwt-decode';

@Component({
  selector: 'app-signup-driver',
  standalone: true,
  imports: [FormsModule, Header, Button, PhoneMaskDirective, NameFormatDirective],
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

  nameInvalido = false;
  emailInvalido = false;
  passInvalido = false;
  confirmPassInvalido = false;
  phoneInvalido = false;
  passwordsDontMatch = false;

  enteredName = '';
  enteredEmail = '';
  enteredPass = '';
  enteredConfirmPass = '';
  enteredPhone = '';

  etapa = 1;

  voltar() {
    this.voltarEvent.emit();
  }

  verification(value: string): boolean {
    return value.trim() === '';
  }

  getUserRoleFromToken(): string | null {
    const token = localStorage.getItem('token');
    if (!token) return null;

    try {
      const decoded: any = jwtDecode(token);
      return decoded.role || decoded.roles?.[0] || decoded.tipo || null;
    } catch (error) {
      console.error('Erro ao decodificar a role do token:', error);
      return null;
    }
  }

  signup() {
    this.passwordsDontMatch = false;

    this.nameInvalido = this.verification(this.enteredName);
    this.emailInvalido = this.verification(this.enteredEmail);
    this.passInvalido = this.verification(this.enteredPass);
    this.confirmPassInvalido = this.verification(this.enteredConfirmPass);
    this.phoneInvalido = this.verification(this.enteredPhone);

    if (
      this.nameInvalido ||
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

    if (!this.registerService.selectedFile) {
      this.popUpService.show('É necessário anexar o arquivo PDF da CNH antes de continuar.', 'warning', 'CNH Obrigatória');
      return;
    }

    const data = {
      documentPdf: this.registerService.selectedFile,
      name: this.enteredName,
      email: this.enteredEmail,
      password: this.enteredPass,
      confirmPassword: this.enteredConfirmPass,
      phone: this.enteredPhone,
    };

    console.log('Enviando cadastro do motorista...');

    this.authService.createDriver(data).subscribe({
      next: (response) => {
        console.log('Motorista cadastrado com sucesso:', response);
        this.cadastroConcluidoEvent.emit({
          email: this.enteredEmail,
        });
      },
      error: (err) => {
        console.error('Erro ao cadastrar motorista:', err);
      },
    });
  }
}
