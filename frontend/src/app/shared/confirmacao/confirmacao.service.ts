import { Injectable, signal } from '@angular/core';

export interface PedidoConfirmacao {
  titulo: string;
  texto?: string;
  /** texto do botao da acao */
  acao: string;
  /** perigo = borda vermelha (apagar, cancelar, sair); azul = acao normal (iniciar rota) */
  tom?: 'perigo' | 'azul';
}

interface Aberta extends PedidoConfirmacao {
  responder: (sim: boolean) => void;
}

// pop-up de confirmacao no meio da tela (componente <app-confirmacao> no app.html).
// uso: if (!(await this.confirmacao.perguntar({ titulo: 'Sair da conta?', acao: 'Sair' }))) return;
@Injectable({ providedIn: 'root' })
export class ConfirmacaoService {
  readonly aberta = signal<Aberta | null>(null);

  perguntar(pedido: PedidoConfirmacao): Promise<boolean> {
    // uma pergunta por vez: a anterior conta como "Voltar"
    this.aberta()?.responder(false);
    return new Promise((resolve) => {
      this.aberta.set({
        ...pedido,
        responder: (sim) => {
          this.aberta.set(null);
          resolve(sim);
        },
      });
    });
  }
}
