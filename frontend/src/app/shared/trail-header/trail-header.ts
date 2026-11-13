import { Component, OnInit, inject, input, signal } from '@angular/core';
import { Location } from '@angular/common';
import { Router } from '@angular/router';
import { AuthService } from '../../services/auth.service';
import { NotificationService } from '../../services/notification.service';
import { ProfileSelect } from '../../components/common/profile-select/profile-select';
import { UserMe } from '../../models/user.model';

// cada item da trilha (menos o ultimo, que e a tela atual) leva pra pagina dele
export const DESTINO_DA_TRILHA: Record<string, string> = {
  Motorista: '/driver-home',
  'Responsável': '/responsible-route',
  Rotas: '/driver-home',
  Grupos: '/driver-groups',
  Contrato: '/contracts',
  Contratos: '/contracts',
};

// topo das telas: seta = home da role, itens da trilha = tela anterior (historico); trilha ("Contrato > Análise contrato") e o usuario logado
@Component({
  selector: 'app-trail-header',
  standalone: true,
  imports: [ProfileSelect],
  templateUrl: './trail-header.html',
  styleUrl: './trail-header.css',
})
export class TrailHeader implements OnInit {
  readonly trilha = input<string[]>([]);
  readonly mostrarVoltar = input(true);
  readonly mostrarSino = input(false);
  // destino de cada item quando o nome nao basta (null = item sem link); sem isso usa DESTINO_DA_TRILHA
  readonly links = input<(string | null)[] | null>(null);

  private router = inject(Router);
  private location = inject(Location);
  private authService = inject(AuthService);
  protected notificacoes = inject(NotificationService);

  protected nome = signal('');
  protected foto = signal('/avatar-padrao.svg');
  // painel do perfil (o mesmo das homes), aberto pela foto/nome
  protected mostrarPerfil = signal(false);
  protected usuario = signal<UserMe | null>(null);

  protected destino(indice: number, item: string): string | null {
    const links = this.links();
    if (links) return links[indice] ?? null;
    return DESTINO_DA_TRILHA[item] ?? null;
  }

  // seta: home de quem esta logado (motorista ou responsavel)
  protected irParaHome() {
    this.router.navigateByUrl(this.authService.getHomeRoute() ?? '/');
  }

  // nome na trilha: volta pra tela anterior; se abriu direto (sem tela anterior no app), vai pro destino do item
  protected voltarParaAnterior(url: string) {
    const navegacao = (history.state as { navigationId?: number } | null)?.navigationId ?? 1;
    if (navegacao > 1) this.location.back();
    else this.router.navigateByUrl(url);
  }

  ngOnInit() {
    if (this.mostrarSino()) this.notificacoes.iniciar();
    this.authService.getMe().subscribe({
      next: (me) => {
        this.usuario.set(me);
        // so o primeiro nome cabe na pilula
        this.nome.set((me.name ?? '').split(' ')[0]);
        if (me.photoUrl) this.foto.set(me.photoUrl);
      },
      error: (err) => console.error(err),
    });
  }
}
