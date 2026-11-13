import { Directive, ElementRef, effect, input, Renderer2 } from '@angular/core';

// efeito Liquid Glass da Glisse (0kzh/glisse) em Angular.
// a Glisse so tem componentes React; aqui usamos so o CSS dela (classes .lg-*, carregado no angular.json)
// so pega o elemento que tiver o atributo appGlass escrito no template: nada e aplicado sozinho.
// uso: <div appGlass> | <div appGlass [glassBlur]="8" glassTint="rgb(255 255 255 / 0.3)">
@Directive({
  selector: '[appGlass]',
  standalone: true,
})
export class GlassDirective {
  /** desfoque do que fica atras, em px (padrao da Glisse: 3) */
  readonly glassBlur = input<number | null>(null);
  /** cor do vidro, qualquer cor CSS (padrao da Glisse: branco 60%) */
  readonly glassTint = input<string | null>(null);

  constructor(el: ElementRef<HTMLElement>, renderer: Renderer2) {
    const alvo = el.nativeElement;
    renderer.addClass(alvo, 'lg-glass');

    effect(() => {
      const blur = this.glassBlur();
      const tint = this.glassTint();
      if (blur == null) alvo.style.removeProperty('--lg-blur');
      else alvo.style.setProperty('--lg-blur', `${blur}px`);
      if (tint == null) alvo.style.removeProperty('--lg-glass');
      else alvo.style.setProperty('--lg-glass', tint);
    });
  }
}
