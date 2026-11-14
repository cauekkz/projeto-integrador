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
import { ThemeService } from '../../services/theme.service';
import type { OpcaoSegmentada, SegmentadoMontado } from './segmentado.react';

export type { OpcaoSegmentada } from './segmentado.react';

// SegmentedControl da Glisse (React, controle segmentado de vidro do iOS 26) embrulhado em Angular.
// o React so e baixado quando este componente aparece; se nao carregar, emite (falhou) pra quem usa
// mostrar os botoes de reserva
@Component({
  selector: 'app-glisse-segmentado',
  standalone: true,
  template: '',
  styleUrl: './glisse-segmentado.css',
  // o HTML e criado pelo React, sem os atributos do Angular: CSS global, preso em .glisse-segmentado
  encapsulation: ViewEncapsulation.None,
})
export class GlisseSegmentado {
  readonly opcoes = input.required<OpcaoSegmentada[]>();
  readonly valor = input<string | null>(null);
  readonly ariaLabel = input('Opções');
  readonly escolheu = output<string>();
  readonly falhou = output<void>();

  private montado = signal<SegmentadoMontado | null>(null);

  constructor() {
    const host = inject<ElementRef<HTMLElement>>(ElementRef).nativeElement;
    const el = document.createElement('div');
    el.className = 'glisse-segmentado';
    // tema da Glisse = tema do app (no escuro usa o dark da propria Glisse)
    const tema = inject(ThemeService);
    effect(() => (el.dataset['theme'] = tema.aplicado()));
    host.appendChild(el);
    let vivo = true;

    afterNextRender(() => {
      import('./segmentado.react')
        .then(({ montarSegmentado }) => {
          if (vivo) this.montado.set(montarSegmentado(el));
        })
        .catch(() => vivo && this.falhou.emit());
    });

    inject(DestroyRef).onDestroy(() => {
      vivo = false;
      this.montado()?.desmontar();
    });

    effect(() => {
      const m = this.montado();
      if (!m) return;
      m.render({
        opcoes: this.opcoes(),
        valor: this.valor(),
        ariaLabel: this.ariaLabel(),
        aoEscolher: (v) => this.escolheu.emit(v),
      });
    });
  }
}
