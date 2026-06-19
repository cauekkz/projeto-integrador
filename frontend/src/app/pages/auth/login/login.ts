import { Component } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { LoginForm } from '../../../components/login-form/login-form';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [LoginForm],
  templateUrl: './login.html',
  styleUrl: './login.css',
})
export class Login {

  tipoUsuario = '';
  constructor(private route: ActivatedRoute) {}
  ngOnInit() {
    this.tipoUsuario = this.route.snapshot.paramMap.get('tipo') || '';
  }
}
