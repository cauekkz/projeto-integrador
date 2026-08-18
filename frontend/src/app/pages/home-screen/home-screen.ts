import { Component, OnInit } from '@angular/core';
import { AddStudent } from '../../components/add-student/add-student';
import { Dependentes } from '../../components/dependentes/dependentes';
import { AddStudentService } from '../../services/addStudent.service';

@Component({
  selector: 'app-home-screen',
  standalone: true,
  imports: [AddStudent,],
  templateUrl: './home-screen.html',
  styleUrl: './home-screen.css',
})
export class HomeScreen implements OnInit {
  mostrarAddStudent = false;
  mostrarDependentes = false;
  meusDependentes: any[] = [];

  constructor(private addStudentService: AddStudentService) {}

  ngOnInit() {
    this.carregarDependentes();
  }

  carregarDependentes() {
    this.addStudentService.getMyChildren().subscribe({
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
