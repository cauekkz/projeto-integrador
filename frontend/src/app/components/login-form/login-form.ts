import { Component, Input } from '@angular/core';
import { Router } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { AuthService } from '../../services/auth.service';
import { ForgotPassword } from '../forgot-password/forgot-password';
import { Header } from '../../components/header/header';


@Component({
  selector: 'app-login-form',
  standalone: true,
  imports: [FormsModule, ForgotPassword,Header],
  templateUrl: './login-form.html',
  styleUrl: './login-form.css',
})
export class LoginForm {
  @Input() tipoUsuario: string = '';
  cpfInvalido: boolean = false;
  passInvalido: boolean = false;
  mostrarForgotPassword = false;

  verification(value: string): boolean {
    return value.trim() === '';
  }

  constructor(
    private router: Router,
    private authService: AuthService,
  ) {}

  irParaCadastro() {
    this.router.navigate(['/signup', this.tipoUsuario]);
  }

  voltar() {
    this.router.navigate(['/']);
  }

  enteredCPF = '';
  enteredPass = '';

  login() {
    this.cpfInvalido = this.verification(this.enteredCPF);
    this.passInvalido = this.verification(this.enteredPass);

    if (this.cpfInvalido || this.passInvalido) {
      return;
    }

    this.authService.login(this.enteredCPF, this.enteredPass).subscribe({
      next: (response) => {
        console.log(response);
      },
      error: (err) => {
        console.error(err);
      },
    });
  }
}
