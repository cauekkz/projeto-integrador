import { Component, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { CommonModule } from '@angular/common';
import { Header } from '../header/header';
import { Button } from '../button/button';
import { StudentService } from '../../services/student.service';
import { Router } from '@angular/router';

@Component({
  selector: 'app-dependentes',
  standalone: true,
  imports: [CommonModule, Header, Button, FormsModule],
  templateUrl: './dependentes.html',
  styleUrl: './dependentes.css',
})
export class Dependentes implements OnInit {
  dependentes: any[] = [];
  carregando = true;
  filtroRelationType = '';

  constructor(
    private studentService: StudentService,
    private router: Router,
  ) {}

  ngOnInit() {
    this.carregarDependentes();
  }

  carregarDependentes() {
    this.carregando = true;
    this.studentService
      .getMyChildren({
        relationType: this.filtroRelationType || undefined,
      })
      .subscribe({
        next: (resposta: any) => {
          console.log('resposta:', resposta); // vê o que chega
          this.dependentes = resposta?.content ?? resposta ?? [];
          this.carregando = false;
        },
        error: (err) => {
          console.error('erro:', err);
          this.dependentes = [];
          this.carregando = false;
        },
      });
  }

  toggleDependente(dep: any) {
    dep.aberto = !dep.aberto;
  }

  voltar() {
    this.router.navigate(['/home-screen']);
  }

  adicionarDependente() {
    this.router.navigate(['/add-student']);
  }
}
