import { Component, OnInit, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { Footer } from '../../../shared/footer/footer';
import { TrailHeader } from '../../../shared/trail-header/trail-header';
import { RouteCard } from '../../../components/driver/route/route-card/route-card';
import { RouteService } from '../../../services/route.service';
import { routeWithStops } from '../../../services/dto/route/route.dto';

// aba do meio do motorista (/driver-routes): "Qual rota você vai fazer agora?" abre a viagem do dia da rota.
// a lista pra montar as rotas e a home (/driver-home, pages/driver-routes)
@Component({
  selector: 'app-driver-home',
  standalone: true,
  imports: [Footer, TrailHeader, RouteCard],
  templateUrl: './driver-home.html',
  styleUrls: ['../../../shared/folha.css', './driver-home.css'],
})
export class DriverHome implements OnInit {
  private router = inject(Router);
  private routeService = inject(RouteService);

  protected rotas = signal<routeWithStops[]>([]);
  protected carregando = signal(true);
  protected erroCarregar = signal(false);

  ngOnInit() {
    this.carregar();
  }

  carregar() {
    this.carregando.set(true);
    this.erroCarregar.set(false);
    this.routeService.getMyRoutes({ active: true, size: 50 }).subscribe({
      next: (pagina) => {
        this.rotas.set(pagina.content ?? []);
        this.carregando.set(false);
      },
      error: () => {
        this.erroCarregar.set(true);
        this.carregando.set(false);
      },
    });
  }

  abrirRota(rota: routeWithStops) {
    this.router.navigate(['/driver-route', rota.id]);
  }

  // sem rotas (ou voltar): a home, onde ele cria e monta as rotas
  irParaRotas() {
    this.router.navigate(['/driver-home']);
  }
}
