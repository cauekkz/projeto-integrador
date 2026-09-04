import { Component, EventEmitter, Output } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { AuthService } from '../../../services/auth.service';
import { Header } from '../../../shared//header/header';
import { Button } from '../../../shared/button/button';
import { CommonModule} from '@angular/common';
import { CpfMaskDirective, PhoneMaskDirective, NameFormatDirective } from '../../../shared/directives';

@Component({
  selector: 'app-signup-responsible',
  standalone: true,
  imports: [FormsModule, Header, Button, CommonModule, CpfMaskDirective, PhoneMaskDirective, NameFormatDirective],
  templateUrl: './signup-responsible.html',
  styleUrl: './signup-responsible.css',
})
export class SignupResponsible {
  @Output() voltarEvent = new EventEmitter<void>();
  @Output() cadastroConcluidoEvent = new EventEmitter<{
    email: string;
  }>();

  constructor(
    private authService: AuthService,
    private router: Router
  ) {}

  cpfInvalido: boolean = false;
  nameInvalido: boolean = false;
  emailInvalido: boolean = false;
  passInvalido: boolean = false;
  confirmPassInvalido: boolean = false;
  phoneInvalido: boolean = false;
  passwordsDontMatch: boolean = false;

  enteredCpf = '';
  enteredName = '';
  enteredEmail = '';
  enteredPass = '';
  enteredConfirmPass = '';
  enteredPhone = '';

  verification(value: string): boolean {
    return value.trim() === '';
  }

  voltar(){
    this.voltarEvent.emit();
  }

  signup() {

    this.cpfInvalido = this.verification(this.enteredCpf);
    this.nameInvalido = this.verification(this.enteredName);
    this.emailInvalido = this.verification(this.enteredEmail);
    this.passInvalido = this.verification(this.enteredPass);
    this.confirmPassInvalido = this.verification(this.enteredConfirmPass);
    this.phoneInvalido = this.verification(this.enteredPhone);
    if (
      this.cpfInvalido ||
      this.nameInvalido ||
      this.emailInvalido ||
      this.passInvalido ||
      this.confirmPassInvalido ||
      this.phoneInvalido
    ) {
      return;
    }

    this.passwordsDontMatch = false;

    if (this.enteredPass !== this.enteredConfirmPass) {
      this.passwordsDontMatch = true;
      return;
    }

    const data = {
      name: this.enteredName,
      cpf: this.enteredCpf,
      email: this.enteredEmail,
      phone: this.enteredPhone,
      password: this.enteredPass,
      confirmPassword: this.enteredConfirmPass,
    };

    this.authService.createUser(data).subscribe({
      next: (response) => {
        // router e get user, pegar token e user
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
