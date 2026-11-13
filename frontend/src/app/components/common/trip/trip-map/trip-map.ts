import { AfterViewInit, Component, ElementRef, OnDestroy, effect, input, viewChild } from '@angular/core';
import { GlisseVidro } from '../../../../shared/glisse-vidro/glisse-vidro';
import type { PropsMapaViagem } from './trip-map.react';

export interface PontoNoMapa {
  id: string;
  latitude: number;
  longitude: number;
  tipo: 'casa' | 'escola';
  // o proximo ponto ganha o rotulo "Próximo ponto"
  alvo: boolean;
}

type MapaMontado = ReturnType<typeof import('./trip-map.react').montarMapaViagem>;

// mapa da viagem: o componente Map do mapcn (React + MapLibre, fundo CARTO claro/escuro conforme o tema do app)
// montado aqui dentro pela ponte trip-map.react.tsx. Mostra a van, a linha do trajeto e os pontos.
// o path do backend vem [longitude, latitude], o mesmo formato do MapLibre
@Component({
  selector: 'app-trip-map',
  standalone: true,
  imports: [GlisseVidro],
  template: `
    <div #mapa class="mapa" role="img" aria-label="Mapa da viagem"></div>
    <button type="button" class="localizar" [attr.aria-label]="van() ? 'Centralizar no motorista' : 'Ver a rota inteira'" [attr.title]="van() ? 'Centralizar no motorista' : 'Ver a rota inteira'" (click)="focar()">
      <app-glisse-vidro />
      <i class="bi bi-crosshair" aria-hidden="true"></i>
    </button>
  `,
  styleUrl: './trip-map.css',
})
export class TripMap implements AfterViewInit, OnDestroy {
  readonly van = input<{ latitude: number; longitude: number } | null>(null);
  readonly caminho = input<number[][] | null>(null);
  readonly pontos = input<PontoNoMapa[]>([]);
  readonly rotuloAlvo = input('Próximo ponto');

  private elemento = viewChild.required<ElementRef<HTMLElement>>('mapa');
  private mapa?: MapaMontado;
  private destruido = false;

  constructor() {
    // le os dados SEMPRE (antes de olhar o mapa): assim o effect fica inscrito nos inputs
    // mesmo quando a viagem chega antes do mapa terminar de abrir
    effect(() => {
      const props = this.props();
      this.mapa?.atualizar(props);
    });
  }

  private props(): PropsMapaViagem {
    return { van: this.van(), caminho: this.caminho(), pontos: this.pontos(), rotuloAlvo: this.rotuloAlvo() };
  }

  async ngAfterViewInit() {
    try {
      // React + MapLibre so baixam nas telas de viagem
      const { montarMapaViagem } = await import('./trip-map.react');
      if (this.destruido) return;
      this.mapa = montarMapaViagem(this.elemento().nativeElement);
      this.mapa.atualizar(this.props());
    } catch (erro) {
      console.error('Não foi possível abrir o mapa:', erro);
    }
  }

  // botao de foco: vai ate o motorista (a van). Sem posicao da van ainda, mostra a rota inteira
  focar() {
    this.mapa?.focar();
  }

  ngOnDestroy() {
    this.destruido = true;
    this.mapa?.destruir();
  }
}
