import {
  afterNextRender,
  Component,
  DestroyRef,
  effect,
  ElementRef,
  inject,
  input,
  output,
  signal,
  ViewEncapsulation,
} from '@angular/core';
import { NavigationCancel, NavigationError, Router } from '@angular/router';
import type { AbaGlisse, TabBarMontada } from './tab-bar.react';
import { ThemeService } from '../../services/theme.service';

export type { AbaGlisse } from './tab-bar.react';

// cada tela tem o proprio rodape. Pra transicao ser continua, a barra da Glisse e uma so:
// quando a tela troca, o mesmo elemento (com o React vivo dentro) muda pro rodape novo,
// e a selecao continua deslizando de onde estava, sem nascer de novo
interface Compartilhada {
  el: HTMLElement;
  barra: TabBarMontada | null;
  dono: GlisseTabBar | null;
}
let compartilhada: Compartilhada | null = null;

// TabBar da Glisse (React) embrulhada em Angular. O React so e baixado quando este componente aparece.
// se o React nao carregar, emite (falhou) pra quem usa mostrar o rodape antigo
@Component({
  selector: 'app-glisse-tab-bar',
  standalone: true,
  template: '',
  styleUrl: './glisse-tab-bar.css',
  // o HTML e criado pelo React, sem os atributos do Angular: o CSS precisa ser global (tudo dentro de .glisse-tab-bar)
  encapsulation: ViewEncapsulation.None,
})
export class GlisseTabBar {
  readonly abas = input.required<AbaGlisse[]>();
  readonly valor = input<string | null>(null);
  readonly ariaLabel = input('Navegação');
  readonly escolheu = output<string>();
  readonly falhou = output<void>();

  private barra = signal<TabBarMontada | null>(null);

  constructor() {
    const host = inject<ElementRef<HTMLElement>>(ElementRef).nativeElement;
    let vivo = true;

    // ja existe uma barra (de outra tela): traz ela pra ca na hora, antes de pintar
    if (compartilhada) {
      host.appendChild(compartilhada.el);
      compartilhada.dono = this;
      this.barra.set(compartilhada.barra);
    } else {
      const el = document.createElement('div');
      el.className = 'glisse-tab-bar';
      // tema da Glisse = tema do app (o effect abaixo acompanha a troca)
      el.dataset['theme'] = inject(ThemeService).aplicado();
      host.appendChild(el);
      compartilhada = { el, barra: null, dono: this };
      const minha = compartilhada;
      afterNextRender(() => {
        import('./tab-bar.react')
          .then(({ montarTabBar }) => {
            if (compartilhada !== minha) return;
            minha.barra = montarTabBar(el);
            if (minha.dono) minha.dono.barra.set(minha.barra);
          })
          .catch(() => {
            if (compartilhada === minha) compartilhada = null;
            if (vivo) this.falhou.emit();
          });
      });
    }

    // modo escuro do app: a barra usa o tema dark da propria Glisse
    const tema = inject(ThemeService);
    effect(() => {
      const modo = tema.aplicado();
      if (compartilhada) compartilhada.el.dataset['theme'] = modo;
    });

    // tocou numa aba e a tela nao mudou (navegacao cancelada ou com erro): a bolha volta pra aba certa
    const navegacao = inject(Router).events.subscribe((e) => {
      if (e instanceof NavigationCancel || e instanceof NavigationError) {
        queueMicrotask(() => {
          if (vivo && compartilhada?.dono === this) this.barra()?.voltar();
        });
      }
    });

    inject(DestroyRef).onDestroy(() => {
      navegacao.unsubscribe();
      vivo = false;
      const atual = compartilhada;
      if (!atual || atual.dono !== this) return;
      atual.dono = null;
      // a tela nova pega a barra logo em seguida; se nenhuma pegar (tela sem rodape), desmonta
      setTimeout(() => {
        if (compartilhada !== atual || atual.dono) return;
        atual.barra?.desmontar();
        atual.el.remove();
        compartilhada = null;
      }, 1000);
    });

    effect(() => {
      const barra = this.barra();
      if (!barra) return;
      barra.render({
        abas: this.abas(),
        // a selecao so anda quando a tela nova aparece (o valor vem da navegacao), nao no toque
        valor: this.valor(),
        ariaLabel: this.ariaLabel(),
        aoEscolher: (v) => this.escolheu.emit(v),
      });
    });
  }
}
