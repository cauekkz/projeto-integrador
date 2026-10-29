import { Component, OnInit, ChangeDetectorRef, inject } from '@angular/core';
import { NotificationService } from '../../services/notification.service';
import { UserMe } from '../../models/user.model';
import { StudentService } from '../../services/student.service';
import { StudentPhotoService } from '../../services/student-photo.service';
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

  // badge do sino: nao lidas de verdade (GET /api/notifications/unread-count + WebSocket)
  protected notificacoes = inject(NotificationService);
  private studentPhotoService = inject(StudentPhotoService);

  constructor(
    private authService: AuthService,
    private studentService: StudentService,
    private cdr: ChangeDetectorRef,
    private route: ActivatedRoute,
    private router: Router,
  ) {}

  ngOnInit() {
    this.notificacoes.iniciar();
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
        const lista: any[] = resposta?.content ?? resposta ?? [];
        // foto escolhida no cadastro (guardada neste navegador ate o backend ter foto de aluno)
        this.meusDependentes = lista.map((d) => ({ ...d, photoUrl: d.photoUrl || this.studentPhotoService.obter(d.id) }));
        this.cdr.detectChanges();
      },
      error: () => {
        this.meusDependentes = [];
      },
    });
  }

  // 1 dependente: o primeiro nome dele; mais: a quantidade
  rotuloDependentes(): string {
    if (this.meusDependentes.length === 1) return (this.meusDependentes[0]?.name ?? '').split(' ')[0] || 'Dependente';
    return `${this.meusDependentes.length} dependentes`;
  }

  iniciais(nome: string | undefined): string {
    return (nome ?? '')
      .split(' ')
      .filter(Boolean)
      .slice(0, 2)
      .map((parte) => parte[0].toUpperCase())
      .join('') || '?';
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

  irParaRota() {
    this.router.navigate(['/responsible-route']);
  }

  irParaChat() {
    this.router.navigate(['/chat'], { queryParams: { from: 'responsible-home' } });
  }
}
