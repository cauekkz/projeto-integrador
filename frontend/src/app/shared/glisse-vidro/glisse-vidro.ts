import { afterNextRender, Component, DestroyRef, ElementRef, inject, output, ViewEncapsulation } from '@angular/core';
import { ThemeService } from '../../services/theme.service';

// vidro da Glisse (componente Glass, React) como fundo de quem o usa: fica atras do conteudo,
// ocupando a caixa inteira do pai (que precisa de position: relative e border-radius).
// se o React nao carregar, emite (falhou) pra quem usa manter o vidro antigo
@Component({
  selector: 'app-glisse-vidro',
  standalone: true,
  template: '',
  styleUrl: './glisse-vidro.css',
  // o HTML e criado pelo React, sem os atributos do Angular: CSS global, preso em app-glisse-vidro
  encapsulation: ViewEncapsulation.None,
  // tema da Glisse = tema do app (no escuro usa o dark da propria Glisse)
  host: { 'aria-hidden': 'true', '[attr.data-theme]': 'tema.aplicado()' },
})
export class GlisseVidro {
  readonly falhou = output<void>();
  protected readonly tema = inject(ThemeService);

  constructor() {
    const host = inject<ElementRef<HTMLElement>>(ElementRef).nativeElement;
    let vivo = true;
    let desmontar: (() => void) | null = null;

    afterNextRender(() => {
      import('./vidro.react')
        .then(({ montarVidro }) => {
          if (vivo) desmontar = montarVidro(host);
        })
        .catch(() => vivo && this.falhou.emit());
    });

    inject(DestroyRef).onDestroy(() => {
      vivo = false;
      desmontar?.();
    });
  }
}
