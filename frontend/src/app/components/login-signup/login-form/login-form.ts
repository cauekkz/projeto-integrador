import { Component, EventEmitter, Input, Output } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { AuthService } from '../../../services/auth.service';
import { Header } from '../../../shared/header/header';
import { Button } from '../../../shared/button/button';
import { ForgotPassword } from '../../../components/login-signup/forgot-password/forgot-password';
import { CpfMaskDirective } from '../../../shared/directives';

@Component({
  selector: 'app-login-form',
  standalone: true,
  imports: [FormsModule, Header, Button, ForgotPassword, CpfMaskDirective],
  templateUrl: './login-form.html',
  styleUrl: './login-form.css',
})
export class LoginForm {
  @Input() tipoUsuario = '';

  @Output() voltarEvent = new EventEmitter<void>();

  constructor(
    private router: Router,
    private authService: AuthService,
  ) {}

  enteredCPF = '';
  enteredPass = '';

  cpfInvalido = false;
  passInvalido = false;

  mostrarForgotPassword = false;

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
      return;
    }

    this.authService.login(this.enteredCPF, this.enteredPass).subscribe({
      next: (response) => {
        localStorage.setItem('token', response.token);

        if (this.tipoUsuario === 'motorista') {
          this.router.navigate(['/driver-home']);
        } else {
          this.router.navigate(['/home-screen']);
        }
      },
      error: (err) => {
        console.error(err);
      },
    });
  }
}
