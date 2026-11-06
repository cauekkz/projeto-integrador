import { Component, OnInit, inject, signal } from '@angular/core';
import { Location } from '@angular/common';
import { ActivatedRoute } from '@angular/router';
import { Header } from '../../shared/header/header';
import { TripService } from '../../services/trip.service';
import { tripOccurrence } from '../../services/dto/group/group.dto';
import { TIPOS_DE_OCORRENCIA, diaEMes } from '../../services/dto/trip/trip.dto';

// Figma "Ambos · Ocorrências da viagem" (1058:1808). ?data= e ?rota= vem do cartao do historico
@Component({
  selector: 'app-trip-occurrences',
  standalone: true,
  imports: [Header],
  template: `
    <div class="pagina">
      <app-header titulo="Ocorrências" (voltarEvent)="voltar()" />
      <main class="conteudo">
        @if (subtitulo) {
          <p class="subtitulo">{{ subtitulo }}</p>
        }
        @if (carregando()) {
          <p class="aviso" role="status">Carregando ocorrências...</p>
        } @else if (erro()) {
          <p class="aviso erro" role="alert">Não foi possível carregar as ocorrências.</p>
        } @else {
          @for (o of ocorrencias(); track o.id) {
            <article class="cartao" animate.enter="entrar-fade">
              <div class="topo">
                <strong>{{ tipo(o) }}</strong>
                <small>{{ o.dateTime.slice(11, 16) }}</small>
              </div>
              @if (o.description) {
                <p>{{ o.description }}</p>
              }
              <small>Registrada por {{ o.driverName }}</small>
            </article>
          } @empty {
            <p class="aviso">Nenhuma ocorrência nesta viagem.</p>
          }
        }
      </main>
    </div>
  `,
  styleUrls: ['../../shared/folha.css'],
  styles: `
    .subtitulo { font-size: 13px; color: #62748e; }
    .cartao { display: flex; flex-direction: column; gap: 4px; padding: 12px 14px; border: 1px solid #e2e8f0; border-radius: 14px; }
    .topo { display: flex; justify-content: space-between; align-items: center; }
    .topo strong { font-size: 14px; font-weight: 600; color: #0f172a; }
    .cartao p { font-size: 13px; color: #314158; }
    .cartao small { font-size: 12px; color: #62748e; }
  `,
})
export class TripOccurrences implements OnInit {
  private route = inject(ActivatedRoute);
  private location = inject(Location);
  private tripService = inject(TripService);

  protected carregando = signal(true);
  protected erro = signal(false);
  protected ocorrencias = signal<tripOccurrence[]>([]);
  protected subtitulo = '';

  ngOnInit() {
    const tripId = this.route.snapshot.paramMap.get('tripId') ?? '';
    const data = this.route.snapshot.queryParamMap.get('data');
    const rota = this.route.snapshot.queryParamMap.get('rota');
    this.subtitulo = [data ? diaEMes(data) : '', rota ?? ''].filter(Boolean).join(' · ');
    this.tripService.getOccurrences(tripId).subscribe({
      next: (lista) => {
        this.ocorrencias.set([...lista].sort((a, b) => a.dateTime.localeCompare(b.dateTime)));
        this.carregando.set(false);
      },
      error: () => {
        this.carregando.set(false);
        this.erro.set(true);
      },
    });
  }

  tipo(o: tripOccurrence) {
    return TIPOS_DE_OCORRENCIA.find((t) => t.tipo === o.type)?.texto ?? o.type;
  }

  voltar() {
    this.location.back();
  }
}
