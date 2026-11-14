import { ChangeDetectionStrategy, Component, input } from '@angular/core';

// fim da viagem: a van entra, freia e para na frente do ultimo desembarque (escola ou casa)
@Component({
  selector: 'app-cena-chegada',
  templateUrl: './cena-chegada.html',
  styleUrl: './cena-chegada.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { '[class.noite]': 'noite()' },
})
export class CenaChegada {
  readonly destino = input<'escola' | 'casa'>('escola');
  readonly noite = input(false);
}
