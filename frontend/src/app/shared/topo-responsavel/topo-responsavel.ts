import { Component, inject, input, output } from '@angular/core';
import { Router } from '@angular/router';
import { NotificationService } from '../../services/notification.service';
import { UserMe } from '../../models/user.model';
import { iniciaisDe } from '../iniciais/iniciais';

export interface DependenteDoTopo {
  id: string;
  name: string;
  photoUrl?: string | null;
}

// topo de perfil do responsavel (home e lista dos filhos): sino, o chip com a foto e o nome (abre o menu
// do perfil) e os dependentes empilhados com "Responsavel" (abre a lista, ou adicionar quando nao tem)
@Component({
  selector: 'app-topo-responsavel',
  standalone: true,
  templateUrl: './topo-responsavel.html',
  styleUrl: './topo-responsavel.css',
})
export class TopoResponsavel {
  readonly usuario = input<UserMe | null>(null);
  readonly filhos = input<DependenteDoTopo[]>([]);
  readonly perfil = output<void>();

  protected notificacoes = inject(NotificationService);
  private router = inject(Router);

  // 1 dependente: o primeiro nome dele; mais: a quantidade
  rotuloDependentes(): string {
    const lista = this.filhos();
    if (lista.length === 1) return (lista[0]?.name ?? '').trim().split(/\s+/)[0] || 'Dependente';
    return `${lista.length} dependentes`;
  }

  iniciais(nome: string | null | undefined): string {
    return iniciaisDe(nome);
  }

  abrirFluxoDependente() {
    this.router.navigate([this.filhos().length === 0 ? '/add-student' : '/dependentes']);
  }
}
