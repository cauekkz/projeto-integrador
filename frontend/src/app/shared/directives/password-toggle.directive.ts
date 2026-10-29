import { afterNextRender, Directive, ElementRef, OnDestroy, OnInit, Renderer2 } from '@angular/core';

// pega todo input de senha: envolve o campo e poe um olhinho dentro pra mostrar/esconder (estilo em src/styles.css)
@Directive({
  selector: 'input[type="password"]',
  standalone: true,
})
export class PasswordToggleDirective implements OnInit, OnDestroy {
  private botao?: HTMLButtonElement;
  private icone?: HTMLElement;
  private visivel = false;
  private wrap?: HTMLElement;
  private desligar: Array<() => void> = [];

  constructor(
    private el: ElementRef<HTMLInputElement>,
    private renderer: Renderer2,
  ) {
    // margem de baixo do input passa pro wrapper, senao o olho fica descentralizado (login/esqueci a senha)
    afterNextRender(() => {
      const margem = getComputedStyle(this.el.nativeElement).marginBottom;
      if (!this.wrap || !margem || margem === '0px') return;
      this.renderer.setStyle(this.wrap, 'margin-bottom', margem);
      this.renderer.setStyle(this.el.nativeElement, 'margin-bottom', '0');
    });
  }

  ngOnInit(): void {
    const input = this.el.nativeElement;
    const pai = input.parentNode;
    if (!pai) return;

    const wrap: HTMLElement = this.renderer.createElement('span');
    this.wrap = wrap;
    this.renderer.addClass(wrap, 'senha-wrap');
    this.renderer.insertBefore(pai, wrap, input);
    this.renderer.appendChild(wrap, input);

    this.botao = this.renderer.createElement('button');
    this.icone = this.renderer.createElement('i');
    this.renderer.setAttribute(this.botao, 'type', 'button');
    this.renderer.addClass(this.botao, 'senha-olho');
    this.renderer.appendChild(this.botao, this.icone);
    this.renderer.appendChild(wrap, this.botao);

    // texto nao passa por baixo do olhinho
    this.renderer.setStyle(input, 'padding-right', '46px');

    this.desligar.push(
      // nao tira o foco do campo ao clicar no olho
      this.renderer.listen(this.botao, 'mousedown', (e: MouseEvent) => e.preventDefault()),
      this.renderer.listen(this.botao, 'click', () => this.alternar()),
    );
    this.atualizar();
  }

  ngOnDestroy(): void {
    this.desligar.forEach((fn) => fn());
  }

  private alternar(): void {
    this.visivel = !this.visivel;
    this.atualizar();
  }

  private atualizar(): void {
    if (!this.botao || !this.icone) return;
    this.renderer.setProperty(this.el.nativeElement, 'type', this.visivel ? 'text' : 'password');
    // o icone mostra o estado atual: olho aberto = senha visivel, olho riscado = escondida
    this.renderer.setAttribute(this.icone, 'class', this.visivel ? 'bi bi-eye' : 'bi bi-eye-slash');
    this.renderer.setAttribute(this.botao, 'aria-label', this.visivel ? 'Ocultar senha' : 'Mostrar senha');
    this.renderer.setAttribute(this.botao, 'aria-pressed', String(this.visivel));
  }
}
