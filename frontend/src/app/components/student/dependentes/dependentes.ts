import { Component, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { CommonModule } from '@angular/common';
import { Header } from '../../../shared/header/header';
import { Button } from '../../../shared/button/button';
import { StudentService } from '../../../services/student.service';
import { Router } from '@angular/router';
import { StudentPhotoService } from '../../../services/student-photo.service';

@Component({
  selector: 'app-dependentes',
  standalone: true,
  imports: [CommonModule, Header, Button, FormsModule],
  templateUrl: './dependentes.html',
  styleUrl: './dependentes.css',
})
export class Dependentes implements OnInit {
  dependentes = signal<any[]>([]);
  carregando = signal(true);
  filtroRelationType = '';

  constructor(
    private studentService: StudentService,
    private router: Router,
    private studentPhotoService: StudentPhotoService,
  ) {}

  ngOnInit() {
    this.carregarDependentes();
  }

  carregarDependentes() {
    this.carregando.set(true);
    this.studentService
      .getMyChildren({
        relationType: this.filtroRelationType || undefined,
      })
      .subscribe({
        next: (resposta: any) => {
          const lista: any[] = resposta?.content ?? resposta ?? [];
          // foto escolhida no cadastro (guardada neste navegador ate o backend ter foto de aluno)
          this.dependentes.set(
            lista.map((d) => ({ ...d, photoUrl: d.photoUrl || this.studentPhotoService.obter(d.id) })),
          );
          this.carregando.set(false);
        },
        error: (err) => {
          console.error('erro:', err);
          this.dependentes.set([]);
          this.carregando.set(false);
        },
      });
  }

  toggleDependente(dep: any) {
    this.dependentes.update((lista) =>
      lista.map((d) => (d === dep ? { ...d, aberto: !d.aberto } : d)),
    );
  }

  voltar() {
    this.router.navigate(['/responsible-home']);
  }

  adicionarDependente() {
    this.router.navigate(['/add-student']);
  }
}
