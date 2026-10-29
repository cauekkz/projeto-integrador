import { Component, OnInit, inject, input, output, signal } from '@angular/core';
import { AuthService } from '../../services/auth.service';
import { NotificationService } from '../../services/notification.service';

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

  private authService = inject(AuthService);
  protected notificacoes = inject(NotificationService);

  protected nome = signal('');
  protected foto = signal('/testee.jpg');

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
