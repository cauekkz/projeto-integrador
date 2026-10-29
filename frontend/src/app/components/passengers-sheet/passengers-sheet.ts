import { Component, ElementRef, computed, input, output, signal, viewChild } from '@angular/core';

export interface Passageiro {
  nome: string;
  endereco: string;
  foto?: string | null;
}

type Aba = 'embarcados' | 'esperando';

// arrastou menos que isso: conta como toque
const TOQUE_PX = 6;
// a partir de quanto do painel ja visivel ele termina de abrir sozinho ao soltar
const LIMIAR_ABRIR = 0.25;

// painel arrastavel da rota do motorista: fechado vira uma bolinha com seta acima do footer;
// arrastando a bolinha pra cima o painel sobe com os passageiros embarcados e esperando
@Component({
  selector: 'app-passengers-sheet',
  standalone: true,
  templateUrl: './passengers-sheet.html',
  styleUrl: './passengers-sheet.css',
})
export class PassengersSheet {
  readonly embarcados = input<Passageiro[]>([]);
  readonly esperando = input<Passageiro[]>([]);
  // true assim que comeca a arrastar ou com o painel aberto: a pagina esconde o footer
  readonly footerOcultoChange = output<boolean>();

  private painel = viewChild.required<ElementRef<HTMLElement>>('painel');

  protected aba = signal<Aba>('embarcados');
  protected aberto = signal(false);
  // quanto o painel esta abaixo da posicao aberta, em px, enquanto o dedo arrasta (null = parado)
  protected arrasto = signal<number | null>(null);
  // de 0 (fechado) a 1 (aberto): a bolinha some conforme o painel sobe
  protected progresso = signal(0);

  protected lista = computed(() => (this.aba() === 'embarcados' ? this.embarcados() : this.esperando()));
  protected transform = computed(() => {
    const arrasto = this.arrasto();
    if (arrasto !== null) return `translateY(${arrasto}px)`;
    return this.aberto() ? 'translateY(0)' : 'translateY(100%)';
  });

  private inicioY = 0;
  private inicioDeslocamento = 0;
  private altura = 0;
  private moveu = false;
  private footerOculto = false;

  iniciarArrasto(event: PointerEvent) {
    this.altura = this.painel().nativeElement.offsetHeight;
    this.inicioY = event.clientY;
    this.inicioDeslocamento = this.aberto() ? 0 : this.altura;
    this.moveu = false;
    (event.currentTarget as HTMLElement).setPointerCapture(event.pointerId);
    this.arrasto.set(this.inicioDeslocamento);
    this.avisarFooter();
  }

  arrastar(event: PointerEvent) {
    if (this.arrasto() === null) return;
    const dy = event.clientY - this.inicioY;
    if (Math.abs(dy) > TOQUE_PX) this.moveu = true;
    const deslocamento = Math.min(Math.max(this.inicioDeslocamento + dy, 0), this.altura);
    this.arrasto.set(deslocamento);
    this.progresso.set(this.altura ? 1 - deslocamento / this.altura : 0);
  }

  soltar() {
    const atual = this.arrasto();
    if (atual === null) return;
    // toque alterna; arrasto abre se ja mostrou um pedaco do painel (e fecha se desceu do mesmo jeito)
    const visivel = this.altura ? 1 - atual / this.altura : 0;
    const abrir = !this.moveu ? !this.aberto() : this.aberto() ? visivel > 1 - LIMIAR_ABRIR : visivel > LIMIAR_ABRIR;
    this.aberto.set(abrir);
    this.progresso.set(abrir ? 1 : 0);
    this.arrasto.set(null);
    this.avisarFooter();
  }

  // teclado: Enter/Espaco na bolinha ou no puxador
  alternar() {
    this.aberto.update((aberto) => !aberto);
    this.progresso.set(this.aberto() ? 1 : 0);
    this.avisarFooter();
  }

  selecionarAba(aba: Aba) {
    this.aba.set(aba);
  }

  // emite direto no evento (e so quando muda) pra pagina esconder/mostrar o footer
  private avisarFooter() {
    const oculto = this.aberto() || this.arrasto() !== null;
    if (oculto === this.footerOculto) return;
    this.footerOculto = oculto;
    this.footerOcultoChange.emit(oculto);
  }
}
