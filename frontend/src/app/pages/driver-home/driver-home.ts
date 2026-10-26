import { Component, OnInit, ChangeDetectorRef } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { Footer } from '../../shared/footer/footer';
import { ProfileSelect } from '../../components/profile-select/profile-select';
import { AuthService } from '../../services/auth.service';
import { User } from '../../models/user.model';

@Component({
  selector: 'app-driver-home',
  standalone: true,
  imports: [Footer, ProfileSelect],
  templateUrl: './driver-home.html',
  styleUrl: './driver-home.css',
})
export class DriverHome implements OnInit {
  temRota = true;
  mostrarPerfil = false;
  usuarioLogado: User | null = null;

  constructor(
    private router: Router,
    private authService: AuthService,
    private cdr: ChangeDetectorRef,
    private route: ActivatedRoute,
  ) {}

  ngOnInit() {
    // voltando de "codigo de conversa"/"vincular motorista" reabre o painel do perfil
    this.mostrarPerfil = this.route.snapshot.queryParamMap.get('perfil') === 'aberto';

    const userId = this.authService.getUserIdFromToken();
    if (userId) {
      this.authService.getUserByID(userId).subscribe({
        next: (user) => {
          this.usuarioLogado = user;
          this.cdr.detectChanges();
        },
        error: (err) => console.error('Erro ao buscar motorista:', err),
      });
    }
  }

  irParaRota() {
    this.router.navigate(['/driver-route']);
  }

  irParaPerfil() {
    this.mostrarPerfil = true;
  }

  irParaChat() {
    this.router.navigate(['/chat'], { queryParams: { from: 'driver-home' } });
  }

  fecharPerfil() {
    this.mostrarPerfil = false;
    if (this.route.snapshot.queryParamMap.has('perfil')) {
      this.router.navigate([], { queryParams: {}, replaceUrl: true });
    }
  }
}
