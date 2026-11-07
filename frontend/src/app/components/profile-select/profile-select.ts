import { ChangeDetectorRef, Component, EventEmitter, Input, OnInit, Output, inject } from '@angular/core';
import { Router } from '@angular/router';
import { AuthService } from '../../services/auth.service';

@Component({
  selector: 'app-profile-select',
  standalone: true,
  imports: [],
  templateUrl: './profile-select.html',
  styleUrl: './profile-select.css',
})
export class ProfileSelect implements OnInit {
  @Input() tipoUsuario = '';
  @Input() usuario: any = null;
  @Output() fecharEvent = new EventEmitter<void>();

  private cdr = inject(ChangeDetectorRef);

  constructor(
    private router: Router,
    private authService: AuthService,
  ) {}

  // aberto direto pela rota /profile-select (sem a tela de tras passar o usuario): busca os dados
  ngOnInit() {
    if (!this.tipoUsuario) {
      this.tipoUsuario = this.authService.getRolesFromToken().includes('ROLE_DRIVER') ? 'motorista' : 'responsavel';
    }
    if (!this.usuario) {
      this.authService.getMe().subscribe({
        next: (me) => {
          this.usuario = me;
          this.cdr.markForCheck();
        },
        error: () => {},
      });
    }
  }

  fechar() {
    // pela rota ninguem escuta o fechar: volta pra home
    if (!this.fecharEvent.observed) {
      this.router.navigate([this.authService.getHomeRoute() ?? '/']);
      return;
    }
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

  irPara(rota: string) {
    this.fecharEvent.emit();
    this.router.navigate([rota]);
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
