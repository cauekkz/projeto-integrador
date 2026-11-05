import { Component, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { CommonModule } from '@angular/common';
import { Header } from '../../../shared/header/header';
import { Button } from '../../../shared/button/button';
import { StudentService } from '../../../services/student.service';
import { Router } from '@angular/router';
import { forkJoin, switchMap, map } from 'rxjs';
import { StudentPhoto } from '../student-photo/student-photo';

@Component({
  selector: 'app-dependentes',
  standalone: true,
  imports: [CommonModule, Header, Button, FormsModule, StudentPhoto],
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

  // o admin trocou ou tirou a foto: atualiza so esse dependente
  fotoMudou(dep: any, foto: { photoKey: string | null; photoUrl: string | null }) {
    this.dependentes.update((lista) =>
      lista.map((d) => (d.id === dep.id ? { ...d, photoKey: foto.photoKey, photoUrl: foto.photoUrl } : d)),
    );
  }

  iniciais(nome: string | undefined): string {
    return (nome ?? '')
      .split(' ')
      .filter(Boolean)
      .slice(0, 2)
      .map((parte) => parte[0].toUpperCase())
      .join('');
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
