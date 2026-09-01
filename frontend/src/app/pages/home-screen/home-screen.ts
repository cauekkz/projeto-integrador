import { Component, OnInit, ChangeDetectorRef } from '@angular/core';
import { User } from '../../models/user.model';
import { StudentService } from '../../services/student.service';
import { AuthService } from '../../services/auth.service';
import { Router } from '@angular/router';
import { Footer } from '../../components/footer/footer';
import { ProfileSelect } from '../../components/profile-select/profile-select';

@Component({
  selector: 'app-home-screen',
  standalone: true,
  imports: [Footer, ProfileSelect],
  templateUrl: './home-screen.html',
  styleUrl: './home-screen.css',
})
export class HomeScreen implements OnInit {
  mostrarAddStudent = false;
  mostrarDependentes = false;
  mostrarPerfil = false;
  meusDependentes: any[] = [];
  usuarioLogado: User | null = null;

  constructor(
    private authService: AuthService,
    private studentService: StudentService,
    private cdr: ChangeDetectorRef,
    private router: Router,
  ) {}

  ngOnInit() {
    const cpfUsuario = this.authService.getUserIdFromToken();
    if (cpfUsuario) {
      this.authService.getUserByID(cpfUsuario).subscribe({
        next: (user) => {
          this.usuarioLogado = user;
          this.cdr.detectChanges();
        },
        error: (err) => console.error('Erro ao buscar usuário:', err),
      });
    }
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
  }

  fecharAddStudent() {
    this.mostrarAddStudent = false;
    this.carregarDependentes();
  }

  fecharDependentes() {
    this.mostrarDependentes = false;
  }

  irParaCadastro() {
    this.mostrarDependentes = false;
    this.mostrarAddStudent = true;
  }
}
