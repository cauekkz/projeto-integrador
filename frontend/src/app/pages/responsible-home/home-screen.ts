import { Component, OnInit, ChangeDetectorRef } from '@angular/core';
import { UserMe } from '../../models/user.model';
import { StudentService } from '../../services/student.service';
import { AuthService } from '../../services/auth.service';
import { ActivatedRoute, Router } from '@angular/router';
import { Footer } from '../../shared/footer/footer';
import { ProfileSelect } from '../../components/profile-select/profile-select';

@Component({
  selector: 'app-responsible-home',
  standalone: true,
  imports: [Footer, ProfileSelect],
  templateUrl: './home-screen.html',
  styleUrl: './home-screen.css',
})
export class HomeScreen implements OnInit {
  mostrarPerfil = false;
  meusDependentes: any[] = [];
  usuarioLogado: UserMe | null = null;

  constructor(
    private authService: AuthService,
    private studentService: StudentService,
    private cdr: ChangeDetectorRef,
    private route: ActivatedRoute,
    private router: Router,
  ) {}

  ngOnInit() {
    // voltando de "codigo de conversa"/"vincular motorista" reabre o painel do perfil
    this.mostrarPerfil = this.route.snapshot.queryParamMap.get('perfil') === 'aberto';

    this.authService.getMe().subscribe({
      next: (user) => {
        this.usuarioLogado = user;
        this.cdr.detectChanges();
      },
      error: (err) => console.error('Erro ao buscar usuário:', err),
    });
    this.carregarDependentes();
  }

  carregarDependentes() {
    this.studentService.getMyChildren().subscribe({
      next: (resposta: any) => {
        this.meusDependentes = resposta?.content ?? resposta ?? [];
      },
      error: () => {
        this.meusDependentes = [];
      },
    });
  }

  abrirFluxoDependente() {
    if (this.meusDependentes.length === 0) {
      this.router.navigate(['/add-student']);
    } else {
      this.router.navigate(['/dependentes']);
    }
  }

  irParaPerfil() {
    this.mostrarPerfil = true;
  }

  fecharPerfil() {
    this.mostrarPerfil = false;
    if (this.route.snapshot.queryParamMap.has('perfil')) {
      this.router.navigate([], { queryParams: {}, replaceUrl: true });
    }
  }

  irParaChat() {
    this.router.navigate(['/chat'], { queryParams: { from: 'responsible-home' } });
  }
}
