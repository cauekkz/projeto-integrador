import { Component, Input } from '@angular/core';
import { Router } from '@angular/router';

@Component({
  selector: 'app-login-form',
  standalone: true,
  imports: [],
  templateUrl: './login-form.html',
  styleUrl: './login-form.css',
})
export class LoginForm {
  @Input() tipoUsuario: string = '';

  constructor(private router: Router) {}

  irParaCadastro() {
    this.router.navigate(['/signup', this.tipoUsuario]);
  }
  voltar() {
    this.router.navigate(['/']);
  }
}
