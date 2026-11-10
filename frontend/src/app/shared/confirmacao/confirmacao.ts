import { Component, inject } from '@angular/core';
import { ConfirmacaoService } from './confirmacao.service';

// o pop-up do ConfirmacaoService: acao em cima, "Voltar" embaixo (como as outras confirmacoes).
// o formato de pop-up no meio vem de "confirmacao" no src/styles.css
@Component({
  selector: 'app-confirmacao',
  standalone: true,
  template: `
    @if (servico.aberta(); as c) {
      <div class="fundo confirmacao" animate.enter="entrar-fade" (click)="c.responder(false)">
        <div class="folha" role="alertdialog" aria-modal="true" aria-labelledby="confirmacao-titulo" [attr.aria-describedby]="c.texto ? 'confirmacao-texto' : null" (click)="$event.stopPropagation()">
          <h2 id="confirmacao-titulo">{{ c.titulo }}</h2>
          @if (c.texto) {
            <p id="confirmacao-texto">{{ c.texto }}</p>
          }
          <button type="button" class="btn" [class.btn-perigo]="(c.tom ?? 'perigo') === 'perigo'" [class.btn-azul]="c.tom === 'azul'" (click)="c.responder(true)">{{ c.acao }}</button>
          <button type="button" class="btn btn-contorno" (click)="c.responder(false)">Voltar</button>
        </div>
      </div>
    }
  `,
  styleUrls: ['../folha.css'],
  styles: [':host { display: contents !important; }'],
  // Esc = Voltar
  host: { '(document:keydown.escape)': 'servico.aberta()?.responder(false)' },
})
export class Confirmacao {
  protected servico = inject(ConfirmacaoService);
}
