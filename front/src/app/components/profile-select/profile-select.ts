import { Component, EventEmitter, Input, Output } from '@angular/core';
import { Router } from '@angular/router';
import { AuthService } from '../../services/auth.service';

@Component({
  selector: 'app-profile-select',
  standalone: true,
  imports: [],
  templateUrl: './profile-select.html',
  styleUrl: './profile-select.css',
})
export class ProfileSelect {
  @Input() tipoUsuario = '';
  @Input() usuario: any = null;
  @Output() fecharEvent = new EventEmitter<void>();

  constructor(
    private router: Router,
    private authService: AuthService,
  ) {}

  fechar() {
    this.fecharEvent.emit();
  }

  irParaPerfil() {
    this.fecharEvent.emit();
    this.router.navigate(['/profile-info', this.tipoUsuario]);
  }

  sairDaConta() {
    this.authService.logout();
    this.router.navigate(['/']);
  }
}
