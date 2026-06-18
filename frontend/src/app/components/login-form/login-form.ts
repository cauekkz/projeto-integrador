import { Component, Input, signal } from '@angular/core';
import { Router } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { AuthService } from '../../services/auth.service';

@Component({
  selector: 'app-login-form',
  standalone: true,
  imports: [FormsModule],
  templateUrl: './login-form.html',
  styleUrl: './login-form.css',
})
export class LoginForm {
  @Input() tipoUsuario: string = '';
 

  constructor(private router: Router,
    private authService: AuthService
  ) {}

  irParaCadastro() {
    this.router.navigate(['/signup', this.tipoUsuario]);
  }
  voltar() {
    this.router.navigate(['/']);
  }


  enteredCPF = ""
  enteredPass = ""

  login() {
  this.authService
    .login(this.enteredCPF, this.enteredPass)
    .subscribe({
      next: (response) => {
        console.log(response)
      },
      error: (err) => {
        console.error(err)
      }
    });
}
}
