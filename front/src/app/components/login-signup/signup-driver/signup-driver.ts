import { Component, EventEmitter, Output } from '@angular/core';
import { RegisterService } from '../../../services/register.service';
import { FormsModule } from '@angular/forms';
import { AuthService } from '../../../services/auth.service';
import { Header } from '../../../shared/header/header';
import { Button } from '../../../shared/button/button';

@Component({
  selector: 'app-signup-driver',
  standalone: true,
  imports: [FormsModule, Header, Button],
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
  ) {}

  nameInvalido = false;
  emailInvalido = false;
  passInvalido = false;
  confirmPassInvalido = false;
  phoneInvalido = false;

  enteredName = '';
  enteredEmail = '';
  enteredPass = '';
  enteredConfirmPass = '';
  enteredPhone = '';

  verification(value: string): boolean {
    return value.trim() === '';
  }

  etapa = 1;

  proximaEtapa() {
    this.etapa = 2;
  }

  voltar() {
    this.voltarEvent.emit();
  }

  signup() {
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
      return;
    }

    if (!this.registerService.selectedFile) {
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

    this.authService.createDriver(data).subscribe({
      next: (response) => {
        this.cadastroConcluidoEvent.emit({
          email: this.enteredEmail,
        });

        console.log(response);
      },
      error: (err) => {
        console.error(err);
      },
    });
  }
}
