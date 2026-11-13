import { Component, OnInit, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { Footer } from '../../../shared/footer/footer';
import { Skeleton } from '../../../shared/skeleton/skeleton';
import { StudentService } from '../../../services/student.service';
import { AuthService } from '../../../services/auth.service';
import { NotificationService } from '../../../services/notification.service';
import { UserMe } from '../../../models/user.model';
import { ProfileSelect } from '../../../components/common/profile-select/profile-select';
import { DependenteDoTopo, TopoResponsavel } from '../../../shared/topo-responsavel/topo-responsavel';
import { switchMap } from 'rxjs';

// home do responsavel (casinha e login): procura a viagem em andamento de um dos filhos e abre o mapa;
// sem viagem, avisa que nao ha rota pra acompanhar. Em cima, o mesmo topo de perfil da lista dos filhos
@Component({
  selector: 'app-sem-viagem',
  standalone: true,
  imports: [Footer, Skeleton, TopoResponsavel, ProfileSelect],
  template: `
    <app-topo-responsavel [usuario]="usuario()" [filhos]="filhos()" (perfil)="mostrarPerfil.set(true)" />

    @if (procurando()) {
      <main class="vazio">
        <div class="sk-tela sk-centro" role="status" aria-label="Procurando a viagem...">
          <app-skeleton variante="circle" largura="56px" altura="56px" />
          <app-skeleton variante="title" largura="70%" />
          <app-skeleton variante="text" largura="85%" [quantos]="2" />
        </div>
      </main>
    } @else {
      <main class="vazio">
        <i class="bi bi-truck" aria-hidden="true"></i>
        <h1>Não há uma rota para acompanhar</h1>
        <p>Quando a van sair com um dos seus filhos, o acompanhamento aparece aqui.</p>
      </main>
    }
    <app-footer tipoUsuario="responsavel" />

    @if (mostrarPerfil()) {
      <app-profile-select animate.leave="sair-perfil" tipoUsuario="responsavel" [usuario]="usuario()" (fecharEvent)="mostrarPerfil.set(false)" />
    }
  `,
  styles: `
    :host {
      display: flex;
      flex-direction: column;
      min-height: 100dvh;
      background: var(--cor-fundo);
    }

    .vazio {
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      gap: 8px;
      flex: 1;
      max-width: 400px;
      margin: 0 auto;
      padding: 24px 24px 120px;
      text-align: center;
    }

    .vazio i {
      font-size: 40px;
      color: var(--cor-texto-4);
    }

    .vazio h1 {
      font-size: 20px;
      font-weight: 600;
      color: var(--cor-texto);
    }

    .vazio p {
      font-size: 14px;
      color: var(--cor-texto-3);
    }
  `,
})
export class SemViagem implements OnInit {
  private studentService = inject(StudentService);
  private router = inject(Router);
  private authService = inject(AuthService);
  private notificacoes = inject(NotificationService);
  protected procurando = signal(true);

  // topo de perfil
  protected usuario = signal<UserMe | null>(null);
  protected filhos = signal<DependenteDoTopo[]>([]);
  protected mostrarPerfil = signal(false);

  ngOnInit() {
    this.notificacoes.iniciar();
    this.authService.getMe().subscribe({ next: (u) => this.usuario.set(u), error: () => {} });
    this.studentService
      .getMyChildren({ size: 100 })
      .pipe(switchMap((pagina) => this.studentService.comFotos(pagina.content ?? [])))
      .subscribe({ next: (lista) => this.filhos.set(lista), error: () => {} });

    // a casinha do rodape ja procurou e nao achou viagem
    if (this.router.getCurrentNavigation()?.extras.state?.['semViagem'] || history.state?.semViagem) {
      this.procurando.set(false);
      return;
    }
    this.studentService.viagemEmAndamento().subscribe((viagem) => {
      if (viagem) {
        // troca no lugar: o voltar nao cai de novo aqui
        this.router.navigate(['/responsible-route', viagem.tripId], { queryParams: { aluno: viagem.filho }, replaceUrl: true });
      } else {
        this.procurando.set(false);
      }
    });
  }
}
