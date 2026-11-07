import { Component, OnInit, inject, input, output, signal } from '@angular/core';
import { Router } from '@angular/router';
import { AuthService } from '../../services/auth.service';
import { NotificationService } from '../../services/notification.service';

// cada item da trilha (menos o ultimo, que e a tela atual) leva pra pagina dele
export const DESTINO_DA_TRILHA: Record<string, string> = {
  Motorista: '/driver-home',
  'Responsável': '/responsible-home',
  Rotas: '/driver-home',
  Grupos: '/driver-groups',
  Contrato: '/contracts',
  Contratos: '/contracts',
};

// topo das telas de contrato: voltar, trilha ("Contrato > Análise contrato") e o usuario logado
@Component({
  selector: 'app-trail-header',
  standalone: true,
  templateUrl: './trail-header.html',
  styleUrl: './trail-header.css',
})
export class TrailHeader implements OnInit {
  readonly trilha = input<string[]>([]);
  readonly mostrarVoltar = input(true);
  readonly mostrarSino = input(false);
  readonly voltarEvent = output<void>();
  // destino de cada item quando o nome nao basta (null = item sem link); sem isso usa DESTINO_DA_TRILHA
  readonly links = input<(string | null)[] | null>(null);

  private router = inject(Router);
  private authService = inject(AuthService);
  protected notificacoes = inject(NotificationService);

  protected nome = signal('');
  protected foto = signal('/testee.jpg');

  protected destino(indice: number, item: string): string | null {
    const links = this.links();
    if (links) return links[indice] ?? null;
    return DESTINO_DA_TRILHA[item] ?? null;
  }

  protected irPara(url: string) {
    this.router.navigateByUrl(url);
  }

  ngOnInit() {
    if (this.mostrarSino()) this.notificacoes.iniciar();
    this.authService.getMe().subscribe({
      next: (me) => {
        // so o primeiro nome cabe na pilula
        this.nome.set((me.name ?? '').split(' ')[0]);
        if (me.photoUrl) this.foto.set(me.photoUrl);
      },
      error: (err) => console.error(err),
    });
  }
}
