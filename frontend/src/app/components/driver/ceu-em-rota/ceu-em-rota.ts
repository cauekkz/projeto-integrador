import { ChangeDetectionStrategy, Component, input } from '@angular/core';

export type PeriodoDoDia = 'dia' | 'por-do-sol' | 'noite';

// dia ate 16h00, por do sol das 16h01 as 17h59 e noite das 18h as 5h59
export function periodoDoDia(agora: Date): PeriodoDoDia {
  const hora = agora.getHours();
  if (hora >= 18 || hora < 6) return 'noite';
  return hora * 60 + agora.getMinutes() <= 16 * 60 ? 'dia' : 'por-do-sol';
}

// 0 em 16h01 e 1 em 17h59: o quanto o sol ja desceu
export function progressoTarde(agora: Date): number {
  const minuto = agora.getHours() * 60 + agora.getMinutes();
  return Math.min(1, Math.max(0, (minuto - 961) / (1079 - 961)));
}

// fundo do card "Em rota agora" (driver-routes): o ceu muda pela hora, nao pelo tema.
// dia (6h as 16h): azul limpo com o sol; por do sol (16h01 as 17h59): laranja, o sol vai descendo minuto a minuto;
// noite (18h as 5h59): o fundo do card como sempre foi, com a lua e as estrelas.
@Component({
  selector: 'app-ceu-em-rota',
  templateUrl: './ceu-em-rota.html',
  styleUrl: './ceu-em-rota.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { '[attr.data-periodo]': 'periodo()', '[style.--p]': 'progresso()' },
})
export class CeuEmRota {
  readonly periodo = input<PeriodoDoDia>('dia');
  // 0 = 16h01, 1 = 17h59: quanto o sol ja desceu
  readonly progresso = input(0);

  // posicao (%), tamanho (px), tempo do pisca e atraso de cada estrela; fixas pra nao pular a cada render
  protected readonly estrelas = [
    [8, 14, 1.5, 3.1, 0], [21, 30, 1, 4.2, 1.3], [33, 9, 2, 3.6, 0.6], [46, 22, 1, 4.8, 2.1],
    [55, 7, 1.5, 3.3, 1.7], [64, 34, 1, 4.4, 0.4], [72, 16, 2, 3.8, 2.6], [83, 44, 1, 4.1, 1.1],
    [92, 30, 1.5, 3.5, 3], [40, 46, 1, 5, 1.9], [14, 52, 1.5, 4.6, 0.9], [60, 56, 1, 3.9, 2.4],
  ];
}
