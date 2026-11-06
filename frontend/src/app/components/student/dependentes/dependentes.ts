import { Component, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { CommonModule } from '@angular/common';
import { Header } from '../../../shared/header/header';
import { Button } from '../../../shared/button/button';
import { StudentService } from '../../../services/student.service';
import { Router } from '@angular/router';
import { forkJoin, switchMap, map } from 'rxjs';
import { Footer } from '../../../shared/footer/footer';

@Component({
  selector: 'app-dependentes',
  standalone: true,
  imports: [CommonModule, Header, Button, FormsModule, Footer],
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
    // foto vem do backend (photoKey -> link de leitura); admin = filhos do my-children?isAdmin=true
    forkJoin({
      filhos: this.studentService
        .getMyChildren({ relationType: this.filtroRelationType || undefined, size: 100 })
        .pipe(switchMap((pagina) => this.studentService.comFotos(pagina.content ?? []))),
      admins: this.studentService.getAdminChildIds(),
    })
      .pipe(map(({ filhos, admins }) => filhos.map((d) => ({ ...d, admin: admins.has(d.id) }))))
      .subscribe({
        next: (lista) => {
          this.dependentes.set(lista);
          this.carregando.set(false);
        },
        error: (err) => {
          console.error('erro:', err);
          this.dependentes.set([]);
          this.carregando.set(false);
        },
      });
  }


  iniciais(nome: string | undefined): string {
    return (nome ?? '')
      .split(' ')
      .filter(Boolean)
      .slice(0, 2)
      .map((parte) => parte[0].toUpperCase())
      .join('');
  }

  // Figma 1054:635: o cartao abre o filho em detalhe
  abrirDependente(dep: any) {
    this.router.navigate(['/student', dep.id]);
  }

  voltar() {
    this.router.navigate(['/responsible-home']);
  }

  adicionarDependente() {
    this.router.navigate(['/add-student']);
  }
}
