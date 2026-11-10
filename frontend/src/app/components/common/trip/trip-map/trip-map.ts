import { AfterViewInit, Component, ElementRef, OnDestroy, effect, input, viewChild } from '@angular/core';
import * as L from 'leaflet';

export interface PontoNoMapa {
  id: string;
  latitude: number;
  longitude: number;
  tipo: 'casa' | 'escola';
  // o proximo ponto ganha o rotulo "Próximo ponto"
  alvo: boolean;
}

const ICONE_VAN = L.divIcon({
  className: 'marcador-van',
  html: '<span><i class="bi bi-bus-front-fill"></i></span>',
  iconSize: [36, 36],
  iconAnchor: [18, 18],
});

function iconePonto(ponto: PontoNoMapa, rotulo: string) {
  const icone = ponto.tipo === 'escola' ? 'bi-mortarboard-fill' : 'bi-house-door-fill';
  const texto = ponto.alvo && rotulo ? `<em>${rotulo}</em>` : '';
  return L.divIcon({
    className: 'marcador-ponto' + (ponto.alvo ? ' alvo' : ''),
    html: `<span><i class="bi ${icone}"></i></span>${texto}`,
    iconSize: [32, 32],
    iconAnchor: [16, 16],
  });
}

// fundo claro estilo app de corrida (OpenFreeMap Positron, vetorial e sem chave)
const ESTILO_DO_MAPA = 'https://tiles.openfreemap.org/styles/positron';
const CREDITOS = '© <a href="https://openfreemap.org" target="_blank" rel="noopener">OpenFreeMap</a> © <a href="https://www.openstreetmap.org/copyright" target="_blank" rel="noopener">OpenStreetMap</a>';

// o fundo vetorial desenha com WebGL; sem ele, usa o OpenStreetMap comum
function temWebGL(): boolean {
  try {
    const canvas = document.createElement('canvas');
    return !!(canvas.getContext('webgl2') || canvas.getContext('webgl'));
  } catch {
    return false;
  }
}

// mapa da viagem (Leaflet + fundo vetorial do OpenFreeMap): a van, a linha do trajeto e os pontos.
// o path do backend vem [longitude, latitude]; aqui inverte pro Leaflet
@Component({
  selector: 'app-trip-map',
  standalone: true,
  template: `
    <div #mapa class="mapa" role="img" aria-label="Mapa da viagem"></div>
    <button type="button" class="localizar" [attr.aria-label]="van() ? 'Centralizar no motorista' : 'Ver a rota inteira'" [attr.title]="van() ? 'Centralizar no motorista' : 'Ver a rota inteira'" (click)="focar()">
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
  private mapa?: L.Map;
  private marcadorVan?: L.Marker;
  private linha?: L.Polyline;
  private borda?: L.Polyline;
  private camadaPontos = L.layerGroup();
  private observador?: ResizeObserver;
  // enquadra so quando muda o alvo (ou na primeira vez): o motorista pode mexer no zoom
  private ultimoAlvo: string | null | undefined = undefined;
  // so redesenha os pinos quando eles mudam (cada trip-update chega a cada 3 s)
  private chavePontos = '';
  // a van desliza ate a posicao nova em vez de pular
  private animacao = 0;

  constructor() {
    effect(() => this.desenhar(this.van(), this.caminho(), this.pontos()));
  }

  ngAfterViewInit() {
    try {
      this.mapa = L.map(this.elemento().nativeElement, { zoomControl: false, attributionControl: true, zoomAnimation: true, fadeAnimation: true, markerZoomAnimation: true }).setView(
        [-23.55, -46.63],
        13,
      );
      this.colocarFundo(this.mapa);
      this.camadaPontos.addTo(this.mapa);
      // o mapa muda de tamanho ao expandir: o Leaflet precisa recalcular
      if (typeof ResizeObserver !== 'undefined') {
        this.observador = new ResizeObserver(() => this.mapa?.invalidateSize());
        this.observador.observe(this.elemento().nativeElement);
      }
      this.desenhar(this.van(), this.caminho(), this.pontos());
    } catch (erro) {
      console.error('Não foi possível abrir o mapa:', erro);
    }
  }

  // fundo vetorial (precisa de WebGL); a biblioteca so carrega nas telas de viagem.
  // sem WebGL (celular antigo) ou se falhar, cai pro OpenStreetMap comum
  private async colocarFundo(mapa: L.Map) {
    if (!temWebGL()) {
      this.fundoSimples(mapa, 'o navegador não tem WebGL');
      return;
    }
    let camada: L.Layer | null = null;
    try {
      const { maplibreGL } = await import('@maplibre/maplibre-gl-leaflet');
      if (this.mapa !== mapa) return;
      const vetorial = maplibreGL({ style: ESTILO_DO_MAPA, attributionControl: false });
      camada = vetorial;
      vetorial.addTo(mapa);
      mapa.attributionControl.addAttribution(CREDITOS);

      // erro ou demora pra carregar o estilo: troca pelo OpenStreetMap (o mapa nunca fica vazio)
      const gl = vetorial.getMaplibreMap();
      let carregou = false;
      const desistir = (motivo: string) => {
        if (carregou || this.mapa !== mapa || !camada) return;
        carregou = true;
        mapa.removeLayer(camada);
        mapa.attributionControl.removeAttribution(CREDITOS);
        camada = null;
        this.fundoSimples(mapa, motivo);
      };
      gl.once('load', () => {
        carregou = true;
        console.info('[mapa] fundo vetorial (OpenFreeMap) carregado');
      });
      gl.once('error', (e: { error?: unknown }) => desistir('erro no mapa vetorial: ' + String(e?.error ?? e)));
      setTimeout(() => desistir('o mapa vetorial não carregou em 6 s'), 6000);
    } catch (erro) {
      if (camada && this.mapa === mapa) mapa.removeLayer(camada);
      this.fundoSimples(mapa, 'falhou ao abrir o mapa vetorial: ' + String(erro));
    }
  }

  // OpenStreetMap comum (quadradinhos), o que ja funcionava antes
  private fundoSimples(mapa: L.Map, motivo: string) {
    if (this.mapa !== mapa) return;
    console.warn('[mapa] usando o OpenStreetMap:', motivo);
    L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', { maxZoom: 19, attribution: '© OpenStreetMap' }).addTo(mapa);
  }

  // botao de foco: vai ate o motorista (a van). Sem posicao da van ainda, mostra a rota inteira
  focar() {
    const mapa = this.mapa;
    if (!mapa) return;
    const van = this.van();
    if (van) {
      mapa.flyTo([van.latitude, van.longitude], 17, { duration: 0.6 });
      return;
    }
    this.enquadrarTudo();
  }

  // a linha e os pontos cabendo na tela
  private enquadrarTudo() {
    const mapa = this.mapa;
    if (!mapa) return;
    const van = this.van();
    const marcos: L.LatLngTuple[] = (this.caminho() ?? []).map(([lng, lat]) => [lat, lng] as L.LatLngTuple);
    if (van) marcos.push([van.latitude, van.longitude]);
    marcos.push(...this.pontos().map((p) => [p.latitude, p.longitude] as L.LatLngTuple));
    if (!marcos.length) return;
    if (marcos.length === 1) mapa.flyTo(marcos[0], 16, { duration: 0.6 });
    else mapa.flyToBounds(L.latLngBounds(marcos), { padding: [48, 48], maxZoom: 17, duration: 0.6 });
  }

  ngOnDestroy() {
    cancelAnimationFrame(this.animacao);
    this.observador?.disconnect();
    this.mapa?.remove();
  }

  private desenhar(van: { latitude: number; longitude: number } | null, caminho: number[][] | null, pontos: PontoNoMapa[]) {
    const mapa = this.mapa;
    if (!mapa) return;

    if (van) {
      const posicao: L.LatLngExpression = [van.latitude, van.longitude];
      if (this.marcadorVan) this.deslizarVan(van);
      else this.marcadorVan = L.marker(posicao, { icon: ICONE_VAN, zIndexOffset: 1000 }).addTo(mapa);
    } else if (this.marcadorVan) {
      this.marcadorVan.remove();
      this.marcadorVan = undefined;
    }

    const linha = (caminho ?? []).map(([lng, lat]) => [lat, lng] as L.LatLngTuple);
    // linha azul com borda branca por baixo (como nos apps de corrida)
    if (linha.length > 1) {
      if (this.linha) {
        this.borda?.setLatLngs(linha);
        this.linha.setLatLngs(linha);
      } else {
        this.borda = L.polyline(linha, { color: '#ffffff', weight: 9, opacity: 1, lineCap: 'round', lineJoin: 'round' }).addTo(mapa);
        this.linha = L.polyline(linha, { color: '#0c66db', weight: 5, opacity: 1, lineCap: 'round', lineJoin: 'round' }).addTo(mapa);
      }
    } else if (this.linha) {
      this.borda?.remove();
      this.linha.remove();
      this.borda = undefined;
      this.linha = undefined;
    }

    const chave = this.rotuloAlvo() + '|' + pontos.map((p) => `${p.id}:${p.latitude}:${p.longitude}:${p.tipo}:${p.alvo}`).join(';');
    if (chave !== this.chavePontos) {
      this.chavePontos = chave;
      this.camadaPontos.clearLayers();
      for (const ponto of pontos) {
        L.marker([ponto.latitude, ponto.longitude], { icon: iconePonto(ponto, this.rotuloAlvo()) }).addTo(this.camadaPontos);
      }
    }

    const alvo = pontos.find((p) => p.alvo)?.id ?? null;
    if (alvo !== this.ultimoAlvo) {
      this.ultimoAlvo = alvo;
      this.enquadrar(van, linha, pontos);
    }
  }

  // ~1 s de animacao da posicao atual ate a nova (o GPS manda a cada 3 s)
  private deslizarVan(destino: { latitude: number; longitude: number }) {
    const marcador = this.marcadorVan;
    if (!marcador) return;
    cancelAnimationFrame(this.animacao);
    const origem = marcador.getLatLng();
    const inicio = performance.now();
    const passo = (agora: number) => {
      const t = Math.min(1, (agora - inicio) / 1000);
      const suave = t * (2 - t);
      marcador.setLatLng([
        origem.lat + (destino.latitude - origem.lat) * suave,
        origem.lng + (destino.longitude - origem.lng) * suave,
      ]);
      if (t < 1) this.animacao = requestAnimationFrame(passo);
    };
    this.animacao = requestAnimationFrame(passo);
  }

  private enquadrar(van: { latitude: number; longitude: number } | null, linha: L.LatLngTuple[], pontos: PontoNoMapa[]) {
    const alvo = pontos.find((p) => p.alvo);
    const marcos: L.LatLngTuple[] = [];
    if (van) marcos.push([van.latitude, van.longitude]);
    if (alvo) marcos.push([alvo.latitude, alvo.longitude]);
    if (!alvo) marcos.push(...linha, ...pontos.map((p) => [p.latitude, p.longitude] as L.LatLngTuple));
    if (!marcos.length) return;
    if (marcos.length === 1) {
      this.mapa?.setView(marcos[0], 16);
      return;
    }
    this.mapa?.fitBounds(L.latLngBounds(marcos), { padding: [48, 48], maxZoom: 17 });
  }
}
