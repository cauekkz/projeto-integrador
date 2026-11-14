import { Component, OnInit, inject, input, output, signal } from '@angular/core';
import { NotificationService } from '../../services/notification.service';
import { GlisseVidro } from '../glisse-vidro/glisse-vidro';

// Figma "HeaderAvatarAndType" (1051:689): foto, status no meio ("Sem percurso" amarelo / "Em percurso" azul) e o sino.
// usado na home do motorista e por cima do mapa expandido
@Component({
  selector: 'app-status-topbar',
  standalone: true,
  imports: [GlisseVidro],
  template: `
    <header class="barra" [class.flutuante]="flutuante()" [class.com-glisse]="flutuante() && !glisseFalhou()">
      <!-- por cima do mapa: o fundo e o vidro da Glisse -->
      @if (flutuante() && !glisseFalhou()) {
        <app-glisse-vidro (falhou)="glisseFalhou.set(true)" />
      }
      <button type="button" class="foto" aria-label="Abrir perfil" (click)="perfil.emit()">
        <img [src]="foto() || '/avatar-padrao.svg'" alt="" />
      </button>
      <span class="status" [class.ativo]="emPercurso()" role="status">
        <span class="ponto" aria-hidden="true"></span>
        {{ texto() || (emPercurso() ? 'Em percurso' : 'Sem percurso') }}
      </span>
      <button type="button" class="sino" aria-label="Abrir notificações" (click)="notificacoes.abrirTela()">
        <i class="bi bi-bell" aria-hidden="true"></i>
        @if (notificacoes.naoLidas()) {
          <span class="badge" [attr.aria-label]="notificacoes.naoLidas() + ' notificações não lidas'">{{ notificacoes.rotulo() }}</span>
        }
      </button>
    </header>
  `,
  styleUrl: './status-topbar.css',
})
export class StatusTopbar implements OnInit {
  readonly foto = input<string | null | undefined>(null);
  readonly emPercurso = input(false);
  // o responsavel troca o texto pelo momento do filho ("A caminho", "Na van")
  readonly texto = input<string | null>(null);
  // por cima do mapa (vidro); na home fica no fluxo da pagina
  readonly flutuante = input(false);
  readonly perfil = output<void>();

  protected notificacoes = inject(NotificationService);
  // se o React da Glisse nao carregar, volta pro vidro antigo (status-topbar.css)
  protected glisseFalhou = signal(false);

  ngOnInit() {
    this.notificacoes.iniciar();
  }
}
