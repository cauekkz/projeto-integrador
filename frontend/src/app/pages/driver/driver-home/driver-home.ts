import { Skeleton } from '../../../shared/skeleton/skeleton';
import { Component, OnInit, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, forkJoin, map, of } from 'rxjs';
import { Footer } from '../../../shared/footer/footer';
import { TrailHeader } from '../../../shared/trail-header/trail-header';
import { RouteService } from '../../../services/route.service';
import { TripService } from '../../../services/trip.service';
import { WeekDay, routeStop, routeWithStops } from '../../../services/dto/route/route.dto';
import { TripStatus, viagemAtiva } from '../../../services/dto/trip/trip.dto';

// getDay() do JS: 0 = domingo
const DIA_DE_HOJE: WeekDay[] = ['SUNDAY', 'MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY'];

type Situacao = 'livre' | 'aberta' | 'feita';

// aba do meio do motorista (/driver-routes): "Qual rota você vai fazer agora?" abre a viagem do dia da rota.
// cada rota e um cartao de partida (trajeto, alunos de hoje, van e o botao), pra nao ficar igual a
// lista da home (/driver-home, pages/driver-routes), que e onde ele monta as rotas
@Component({
  selector: 'app-driver-home',
  standalone: true,
  imports: [Skeleton, Footer, TrailHeader],
  templateUrl: './driver-home.html',
  styleUrls: ['../../../shared/folha.css', './driver-home.css'],
})
export class DriverHome implements OnInit {
  private router = inject(Router);
  private routeService = inject(RouteService);
  private tripService = inject(TripService);

  protected rotas = signal<routeWithStops[]>([]);
  protected carregando = signal(true);
  protected erroCarregar = signal(false);
  // status da viagem de hoje de cada rota (null = ainda nao tem)
  protected hoje = signal<Record<string, TripStatus | null>>({});

  protected readonly data = this.dataDeHoje();
  private readonly diaDeHoje = DIA_DE_HOJE[new Date().getDay()];

  ngOnInit() {
    this.carregar();
  }

  carregar() {
    this.carregando.set(true);
    this.erroCarregar.set(false);
    this.routeService.getMyRoutes({ active: true, size: 50 }).subscribe({
      next: (pagina) => {
        const rotas = pagina.content ?? [];
        this.rotas.set(rotas);
        this.carregando.set(false);
        this.carregarHoje(rotas);
      },
      error: () => {
        this.erroCarregar.set(true);
        this.carregando.set(false);
      },
    });
  }

  // o back nao tem "viagens de hoje do motorista": olha o today de cada rota (404 = nao tem)
  private carregarHoje(rotas: routeWithStops[]) {
    if (!rotas.length) return;
    forkJoin(
      rotas.map((r) =>
        this.tripService.getToday(r.id).pipe(
          map((trip) => [r.id, trip?.status ?? null] as const),
          catchError(() => of([r.id, null] as const)),
        ),
      ),
    ).subscribe((pares) => this.hoje.set(Object.fromEntries(pares)));
  }

  protected situacao(rota: routeWithStops): Situacao {
    const status = this.hoje()[rota.id];
    if (viagemAtiva(status)) return 'aberta';
    if (status === 'FINISHED') return 'feita';
    return 'livre';
  }

  // alunos diferentes que tem embarque ou desembarque hoje
  protected alunosHoje(rota: routeWithStops): number {
    const ids = rota.stops.flatMap((p) => p.students.filter((a) => a.weekdays.includes(this.diaDeHoje)).map((a) => a.studentId));
    return new Set(ids).size;
  }

  protected textoAlunos(rota: routeWithStops): string {
    const n = this.alunosHoje(rota);
    if (!n) return 'Nenhum aluno hoje';
    return n === 1 ? '1 aluno hoje' : `${n} alunos hoje`;
  }

  protected textoPontos(rota: routeWithStops): string {
    const n = rota.stops.length;
    return n === 1 ? '1 ponto' : `${n} pontos`;
  }

  // do primeiro ao ultimo ponto, pelo bairro (ou rua); meio = bolinhas das paradas do caminho (ate 3)
  protected trajeto(rota: routeWithStops): { de: string; ate: string; meio: number[] } | null {
    const pontos = [...rota.stops].sort((a, b) => a.orderIndex - b.orderIndex);
    if (!pontos.length) return null;
    const lugar = (p: routeStop) => p.address.neighborhood || p.address.street;
    const meio = Math.min(3, Math.max(0, pontos.length - 2));
    return { de: lugar(pontos[0]), ate: lugar(pontos[pontos.length - 1]), meio: Array.from({ length: meio }, (_, i) => i) };
  }

  protected van(rota: routeWithStops): string | null {
    const v = rota.vehicle;
    if (!v) return null;
    return [v.plate, v.model].filter(Boolean).join(' · ');
  }

  abrirRota(rota: routeWithStops) {
    // viagem aberta: direto pro mapa, como o rodape faz
    if (this.situacao(rota) === 'aberta') this.router.navigate(['/driver-route', rota.id], { queryParams: { continuar: 1 } });
    else this.router.navigate(['/driver-route', rota.id]);
  }

  // sem rotas (ou voltar): a home, onde ele cria e monta as rotas
  irParaRotas() {
    this.router.navigate(['/driver-home']);
  }

  // "Quinta, 10 de novembro"
  private dataDeHoje(): string {
    const texto = new Intl.DateTimeFormat('pt-BR', { weekday: 'long', day: 'numeric', month: 'long' }).format(new Date());
    const semFeira = texto.replace('-feira', '');
    return semFeira.charAt(0).toUpperCase() + semFeira.slice(1);
  }
}
