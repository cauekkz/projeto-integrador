import { Component, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { CommonModule } from '@angular/common';
import { Header } from '../../../shared/header/header';
import { Button } from '../../../shared/button/button';
import { StudentService } from '../../../services/student.service';
import { Router } from '@angular/router';

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
          console.log('resposta:', resposta);
          this.dependentes.set(resposta?.content ?? resposta ?? []);
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
    this.router.navigate(['/home-screen']);
  }

  adicionarDependente() {
    this.router.navigate(['/add-student']);
  }
}
