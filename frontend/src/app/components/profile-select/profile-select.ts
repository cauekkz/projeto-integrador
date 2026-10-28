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

  // motorista ve o proprio codigo de conversa
  irParaCodigoConversa() {
    this.fecharEvent.emit();
    this.router.navigate(['/driver-link-code']);
  }

  // responsavel digita o codigo do motorista (tela driver-code que ja existia)
  irParaVincularMotorista() {
    this.fecharEvent.emit();
    this.router.navigate(['/driver-code']);
  }

  // motorista cadastra as escolas que atende (usadas no contrato e na rota)
  irParaEscolas() {
    this.fecharEvent.emit();
    this.router.navigate(['/driver-schools']);
  }

  irParaContratos() {
    this.fecharEvent.emit();
    this.router.navigate(['/contracts']);
  }

  sairDaConta() {
    this.authService.logout();
    this.router.navigate(['/']);
  }
}
