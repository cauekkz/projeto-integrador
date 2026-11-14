import { Component, computed, input } from '@angular/core';

// pedaco de esqueleto: o formato do conteudo aparece enquanto os dados carregam.
// variantes: text (linha), title (titulo), circle (avatar), rect (imagem/bloco) e card (cartao que
// recebe outros skeletons dentro). largura/altura aceitam qualquer medida CSS; quantos repete o bloco.
// a tela poe role="status" + aria-label no container; o skeleton em si e so visual (aria-hidden)
export type VarianteSkeleton = 'text' | 'title' | 'circle' | 'rect' | 'card';

@Component({
  selector: 'app-skeleton',
  standalone: true,
  templateUrl: './skeleton.html',
  styleUrl: './skeleton.css',
  host: {
    'aria-hidden': 'true',
    '[class]': "'sk-' + variante()",
    '[style.--sk-gap]': 'gap()',
  },
})
export class Skeleton {
  readonly variante = input<VarianteSkeleton>('text');
  readonly largura = input<string | null>(null);
  readonly altura = input<string | null>(null);
  readonly quantos = input(1);
  readonly gap = input('8px');

  protected readonly itens = computed(() => Array.from({ length: Math.max(1, this.quantos()) }, (_, i) => i));

  // a ultima linha de um paragrafo fica mais curta, como texto de verdade
  protected larguraDe(i: number): string | null {
    const l = this.largura();
    if (this.variante() === 'text' && !l && this.quantos() > 1 && i === this.quantos() - 1) return '60%';
    return l;
  }
}
