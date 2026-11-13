import { ChangeDetectionStrategy, Component, input } from '@angular/core';

// so o desenho animado do card "Em rota agora" (driver-routes): fica separado pra o CSS da pagina caber no limite.
// [noite]: de noite (18h as 6h) o farol e os postes acendem, nos dois temas
@Component({
  selector: 'app-cena-em-rota',
  templateUrl: './cena-em-rota.html',
  styleUrl: './cena-em-rota.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { '[class.noite]': 'noite()' },
})
export class CenaEmRota {
  readonly noite = input(false);
}
