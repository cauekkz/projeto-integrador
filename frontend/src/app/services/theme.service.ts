import { DOCUMENT } from '@angular/common';
import { Injectable, computed, effect, inject, signal } from '@angular/core';

export type Tema = 'sistema' | 'claro' | 'escuro';

export const CHAVE_TEMA = 'vanroute-tema';
// cor da barra do navegador no celular (igual ao fundo do topo de cada tema)
export const COR_BARRA = { light: '#ffffff', dark: '#323232' } as const;

function lerPreferencia(): Tema {
  try {
    const salvo = localStorage.getItem(CHAVE_TEMA);
    if (salvo === 'claro' || salvo === 'escuro' || salvo === 'sistema') return salvo;
  } catch {
    // modo privado / storage bloqueado: segue o sistema
  }
  return 'sistema';
}

@Injectable({ providedIn: 'root' })
export class ThemeService {
  private readonly doc = inject(DOCUMENT);
  private readonly midia =
    typeof matchMedia === 'function' ? matchMedia('(prefers-color-scheme: dark)') : null;

  /** o que a pessoa escolheu */
  readonly tema = signal<Tema>(lerPreferencia());
  private readonly sistemaEscuro = signal(this.midia?.matches ?? false);

  /** tema aplicado de fato no <html> */
  readonly aplicado = computed<'light' | 'dark'>(() => {
    const t = this.tema();
    return t === 'escuro' || (t === 'sistema' && this.sistemaEscuro()) ? 'dark' : 'light';
  });

  constructor() {
    // sistema mudou (ex.: celular entrou no modo noturno): acompanha na hora
    this.midia?.addEventListener('change', (e) => this.sistemaEscuro.set(e.matches));

    effect(() => this.aplicar(this.aplicado()));
  }

  escolher(tema: Tema): void {
    try {
      localStorage.setItem(CHAVE_TEMA, tema);
    } catch {
      // sem storage: vale so ate fechar a aba
    }
    this.comTransicao(() => this.tema.set(tema));
  }

  private aplicar(modo: 'light' | 'dark'): void {
    const html = this.doc.documentElement;
    html.dataset['theme'] = modo;
    // as duas metas do index.html (por media) viram uma cor so: a do tema escolhido
    this.doc.querySelectorAll<HTMLMetaElement>('meta[name="theme-color"]').forEach((meta) => {
      meta.content = COR_BARRA[modo];
    });
  }

  // troca de tema: um fade unico da tela (View Transition), sem cada botao animar a cor no seu tempo.
  // quem pediu menos movimento troca direto
  private comTransicao(mudar: () => void): void {
    const html = this.doc.documentElement;
    const semMovimento =
      typeof matchMedia === 'function' && matchMedia('(prefers-reduced-motion: reduce)').matches;
    const doc = this.doc as Document & { startViewTransition?: (cb: () => void) => { finished: Promise<void> } };

    html.classList.add('troca-tema');
    const fim = () => html.classList.remove('troca-tema');

    if (semMovimento || typeof doc.startViewTransition !== 'function') {
      mudar();
      this.aplicar(this.aplicado());
      requestAnimationFrame(fim);
      return;
    }
    doc
      .startViewTransition(() => {
        mudar();
        // aplica ja dentro do callback (o effect so roda depois)
        this.aplicar(this.aplicado());
      })
      .finished.finally(fim);
  }
}
