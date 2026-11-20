import { Component } from '@angular/core';
import { Router } from '@angular/router';
import { GlisseVidro } from '../../../shared/glisse-vidro/glisse-vidro';
import { CeuEmRota, periodoDoDia, progressoTarde } from '../../../components/driver/ceu-em-rota/ceu-em-rota';

// 404: a estrada do card "Em rota agora" na tela inteira, com a van grande rodando e uma placa "404" passando.
// o ceu segue a hora, igual ao card (dia, por do sol, noite)
@Component({
  selector: 'app-not-found',
  standalone: true,
  imports: [GlisseVidro, CeuEmRota],
  templateUrl: './not-found.html',
  styleUrl: './not-found.css',
})
export class NotFound {
  private readonly agora = new Date();
  protected readonly periodo = periodoDoDia(this.agora);
  protected readonly progresso = progressoTarde(this.agora);

  constructor(private router: Router) {}

  // '/' tem guestGuard: se tiver logado ja cai na home da role
  voltarInicio() {
    this.router.navigate(['/']);
  }
}
