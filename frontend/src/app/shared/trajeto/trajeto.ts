import { Component, computed, input } from '@angular/core';
import { BoardingStatus } from '../../services/dto/trip/trip.dto';

// a linha de trajeto do app (embarque ●- - -🚐- - -● desembarque): a van fica onde o aluno esta.
// aguardando: no comeco; embarcou: no meio, com a parte feita em azul; entregue: no fim; ausente: linha apagada
@Component({
  selector: 'app-trajeto',
  standalone: true,
  templateUrl: './trajeto.html',
  styleUrl: './trajeto.css',
})
export class Trajeto {
  readonly status = input.required<BoardingStatus>();
  // viagem que ja acabou: quem ficou aguardando nao foi atendido (linha apagada, sem van)
  readonly encerrada = input(false);

  protected readonly etapa = computed(() => {
    switch (this.status()) {
      case 'BOARDED':
        return 'meio';
      case 'DELIVERED':
        return 'fim';
      case 'ABSENT':
        return 'fora';
      default:
        return this.encerrada() ? 'fora' : 'inicio';
    }
  });
}
