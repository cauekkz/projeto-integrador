import { DestroyRef, Directive, ElementRef, Renderer2, effect, inject, input, untracked } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { BotaoCarregandoService, DURACAO_COMPLETAR } from './botao-carregando.service';

// botao que "pinta" enquanto a acao carrega (estilo Mercado Livre):
//   <button [appCarregando]="salvando()" [disabled]="salvando()">...</button>
// - true: o fundo vai sendo preenchido da esquerda pra direita, rapido no comeco e devagar perto de 90%
//   (progresso simulado: nao da pra saber o progresso real de um HTTP); clique duplo e barrado
// - a acao deu certo (aviso do interceptor): completa 100% rapidinho; quando a flag volta pra false, some
// - deu erro: a barra volta pro comeco e o botao libera
// - menos movimento: so um preenchimento parado, sem andar
const TETO = 0.9;
const TAU = 1400; // ms: quanto maior, mais devagar chega no teto

@Directive({
  selector: '[appCarregando]',
  standalone: true,
  host: {
    class: 'botao-carregavel',
    '[class.botao-carregando]': 'appCarregando()',
    '[attr.aria-busy]': 'appCarregando() || null',
    '(click)': 'barrarClique($event)',
  },
})
export class BotaoCarregandoDirective {
  readonly appCarregando = input(false);

  private readonly el = inject<ElementRef<HTMLElement>>(ElementRef).nativeElement;
  private readonly renderer = inject(Renderer2);
  private readonly botoes = inject(BotaoCarregandoService);

  private barra: HTMLElement | null = null;
  private quadro = 0;
  private progresso = 0;
  private inicio = 0;
  private ativo = false;
  private resultado: 'ok' | 'erro' | null = null;
  private readonly poucoMovimento =
    typeof matchMedia === 'function' && matchMedia('(prefers-reduced-motion: reduce)').matches;

  constructor() {
    effect(() => {
      const carregando = this.appCarregando();
      untracked(() => (carregando ? this.comecar() : this.terminar()));
    });

    this.botoes.concluiu.pipe(takeUntilDestroyed()).subscribe(() => {
      if (this.ativo) {
        this.resultado = 'ok';
        this.irPara(1, this.poucoMovimento ? 0 : DURACAO_COMPLETAR);
      }
    });
    this.botoes.falhou.pipe(takeUntilDestroyed()).subscribe(() => {
      if (this.ativo) this.resultado = 'erro';
    });

    inject(DestroyRef).onDestroy(() => {
      cancelAnimationFrame(this.quadro);
      if (this.ativo) this.botoes.ativos--;
    });
  }

  protected barrarClique(e: Event) {
    if (this.appCarregando()) {
      e.preventDefault();
      e.stopImmediatePropagation();
    }
  }

  private comecar() {
    if (this.ativo) return;
    this.ativo = true;
    this.resultado = null;
    this.botoes.ativos++;
    cancelAnimationFrame(this.quadro);

    if (!this.barra) {
      this.barra = this.renderer.createElement('span') as HTMLElement;
      this.renderer.addClass(this.barra, 'botao-barra');
      this.renderer.setAttribute(this.barra, 'aria-hidden', 'true');
      this.renderer.insertBefore(this.el, this.barra, this.el.firstChild);
    }
    this.barra.classList.remove('saindo');

    if (this.poucoMovimento) {
      this.pintar(1);
      return;
    }
    this.pintar(0);
    this.inicio = performance.now();
    const andar = (agora: number) => {
      if (!this.ativo || this.resultado === 'ok') return;
      // sobe rapido e vai freando ate o teto (~90%), esperando a resposta
      this.pintar(TETO * (1 - Math.exp(-(agora - this.inicio) / TAU)));
      this.quadro = requestAnimationFrame(andar);
    };
    this.quadro = requestAnimationFrame(andar);
  }

  private terminar() {
    if (!this.ativo) return;
    this.ativo = false;
    this.botoes.ativos--;
    cancelAnimationFrame(this.quadro);

    if (this.resultado === 'erro') {
      // deu erro: a barra volta pro comeco e o botao libera
      this.irPara(0, this.poucoMovimento ? 0 : 260, () => this.tirarBarra());
      return;
    }
    // terminou bem (ou acao sem HTTP): completa e some
    this.irPara(1, this.resultado === 'ok' || this.poucoMovimento ? 0 : 160, () => this.tirarBarra());
  }

  // anima o progresso de onde esta ate "alvo" em "ms" (ease-out)
  private irPara(alvo: number, ms: number, depois?: () => void) {
    cancelAnimationFrame(this.quadro);
    const de = this.progresso;
    if (ms <= 0 || de === alvo) {
      this.pintar(alvo);
      depois?.();
      return;
    }
    const t0 = performance.now();
    const passo = (agora: number) => {
      const t = Math.min(1, (agora - t0) / ms);
      this.pintar(de + (alvo - de) * (1 - Math.pow(1 - t, 3)));
      if (t < 1) this.quadro = requestAnimationFrame(passo);
      else depois?.();
    };
    this.quadro = requestAnimationFrame(passo);
  }

  private pintar(p: number) {
    this.progresso = p;
    this.barra?.style.setProperty('--progresso', String(p));
  }

  private tirarBarra() {
    const barra = this.barra;
    if (!barra) return;
    barra.classList.add('saindo');
    setTimeout(() => {
      if (this.ativo || this.barra !== barra) return;
      barra.remove();
      this.barra = null;
      this.progresso = 0;
    }, 180);
  }
}
