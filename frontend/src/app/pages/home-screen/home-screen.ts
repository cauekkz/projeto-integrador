import { Component, OnInit } from '@angular/core';
import { AddStudent } from '../../components/add-student/add-student';
import { Dependentes } from '../../components/dependentes/dependentes';
import { StudentService } from '../../services/student.service';
import { AuthService } from '../../services/auth.service';

@Component({
  selector: 'app-home-screen',
  standalone: true,
  imports: [AddStudent],
  templateUrl: './home-screen.html',
  styleUrl: './home-screen.css',
})
export class HomeScreen implements OnInit {
  mostrarAddStudent = false;
  mostrarDependentes = false;
  meusDependentes: any[] = [];

  usuarioLogado: any = null;

  constructor(
    private authService: AuthService,
    private StudentService: StudentService,
  ) {}

  ngOnInit() {
    const idUsuario = this.authService.getUserIdFromToken();

    if (idUsuario) {
      this.authService.getUserByID(idUsuario).subscribe({
        next: (user) => {
          this.usuarioLogado = user;
        },
        error: (err) => {
          console.error('Erro ao buscar usuário pelo ID do token:', err);
        },
      });
    }
  }

  carregarDependentes() {
    this.StudentService.getMyChildren().subscribe({
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
      this.mostrarAddStudent = true;
    } else {
      this.mostrarDependentes = true;
    }
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
