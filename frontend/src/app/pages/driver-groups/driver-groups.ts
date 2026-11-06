import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { Router } from '@angular/router';
import { TrailHeader } from '../../shared/trail-header/trail-header';
import { Footer } from '../../shared/footer/footer';
import { PopUpService } from '../../shared/pop-up';
import { GroupService } from '../../services/group.service';
import { driverGroup } from '../../services/dto/group/group.dto';

// Figma "Motorista · /driver-groups" (1058:635 lista, 1058:699 vazio, 1058:751 novo grupo, 1058:829 erro 409)
@Component({
  selector: 'app-driver-groups',
  standalone: true,
  imports: [FormsModule, TrailHeader, Footer],
  templateUrl: './driver-groups.html',
  styleUrls: ['../../shared/folha.css', '../../shared/rota.css', './driver-groups.css'],
})
export class DriverGroups implements OnInit {
  private groupService = inject(GroupService);
  private router = inject(Router);
  private popUpService = inject(PopUpService);

  protected carregando = signal(true);
  protected erroCarregar = signal(false);
  protected grupos = signal<driverGroup[]>([]);

  protected criando = signal(false);
  protected enviando = signal(false);
  protected nome = '';

  ngOnInit() {
    this.carregar();
  }

  carregar() {
    this.carregando.set(true);
    this.erroCarregar.set(false);
    this.groupService.getGroups().subscribe({
      next: (lista) => {
        this.grupos.set([...lista].sort((a, b) => a.name.localeCompare(b.name)));
        this.carregando.set(false);
      },
      error: () => {
        this.carregando.set(false);
        this.erroCarregar.set(true);
      },
    });
  }

  voltar() {
    this.router.navigate(['/driver-home']);
  }

  abrir(grupo: driverGroup) {
    this.router.navigate(['/driver-groups', grupo.id]);
  }

  quantos(grupo: driverGroup) {
    const n = grupo.students.length;
    return n === 1 ? '1 aluno' : `${n} alunos`;
  }

  abrirNovo() {
    this.nome = '';
    this.criando.set(true);
  }

  fecharNovo() {
    if (!this.enviando()) this.criando.set(false);
  }

  criar() {
    const nome = this.nome.trim();
    if (this.enviando()) return;
    if (!nome) {
      this.popUpService.show('Dê um nome ao grupo.', 'warning', 'Campos Incompletos');
      return;
    }
    this.enviando.set(true);
    this.groupService.createGroup(nome).subscribe({
      next: (grupo) => {
        this.enviando.set(false);
        this.criando.set(false);
        // grupo novo abre direto pra pôr os alunos
        this.router.navigate(['/driver-groups', grupo.id]);
      },
      error: (erro: HttpErrorResponse) => {
        this.enviando.set(false);
        this.popUpService.show(erro?.error?.message || 'Tente de novo em instantes.', 'warning', 'Não foi possível concluir');
      },
    });
  }
}
