// ponte React -> Angular do mapa da viagem: desenha com o componente Map do mapcn (MapLibre + CARTO).
// so e carregado por import() no TripMap, entao React + MapLibre ficam num pedaco separado do app.
// claro/escuro: o mapcn le o data-theme do <html> (ThemeService) e troca o fundo sozinho
import type * as MapLibreGL from 'maplibre-gl';
import { useEffect, useId, useRef, useState } from 'react';
import { createRoot } from 'react-dom/client';
import { Map, MapMarker, MapRoute, MarkerContent, useMap } from '../../../../shared/mapcn/map';

export interface PontoDoMapa {
  id: string;
  latitude: number;
  longitude: number;
  tipo: 'casa' | 'escola';
  alvo: boolean;
}

export interface PropsMapaViagem {
  van: { latitude: number; longitude: number } | null;
  // [longitude, latitude], como vem do backend
  caminho: number[][] | null;
  pontos: PontoDoMapa[];
  rotuloAlvo: string;
}

type Ponto = [number, number];

// centro inicial (Sao Paulo) ate a viagem chegar
const CENTRO_INICIAL: Ponto = [-46.63, -23.55];
const MARGEM = 48;

// todos os pontos cabendo na tela (um so: aproxima nele)
function enquadrar(map: MapLibreGL.Map, marcos: Ponto[], animar: boolean) {
  if (!marcos.length) return;
  if (marcos.length === 1) {
    if (animar) map.flyTo({ center: marcos[0], zoom: 16, duration: 600 });
    else map.jumpTo({ center: marcos[0], zoom: 16 });
    return;
  }
  let [oeste, sul] = marcos[0];
  let [leste, norte] = marcos[0];
  for (const [lng, lat] of marcos) {
    oeste = Math.min(oeste, lng);
    leste = Math.max(leste, lng);
    sul = Math.min(sul, lat);
    norte = Math.max(norte, lat);
  }
  map.fitBounds(
    [
      [oeste, sul],
      [leste, norte],
    ],
    { padding: MARGEM, maxZoom: 17, duration: animar ? 600 : 0 },
  );
}

// enquadra quando muda o proximo ponto (ou na primeira vez); o motorista pode mexer no zoom no meio.
// passa o mapa pronto pra ponte (o botao de centralizar do Angular usa)
function Camera({ props, aoCarregar }: { props: PropsMapaViagem; aoCarregar: (map: MapLibreGL.Map | null) => void }) {
  const { map, isLoaded } = useMap();

  useEffect(() => aoCarregar(map && isLoaded ? map : null), [map, isLoaded, aoCarregar]);
  const ultimoAlvo = useRef<string | null | undefined>(undefined);
  const { van, caminho, pontos } = props;

  useEffect(() => {
    if (!map || !isLoaded) return;
    const alvo = pontos.find((p) => p.alvo);
    const idAlvo = alvo?.id ?? null;
    if (idAlvo === ultimoAlvo.current) return;
    ultimoAlvo.current = idAlvo;
    const marcos: Ponto[] = [];
    if (van) marcos.push([van.longitude, van.latitude]);
    if (alvo) marcos.push([alvo.longitude, alvo.latitude]);
    else {
      marcos.push(...((caminho ?? []) as Ponto[]));
      marcos.push(...pontos.map((p) => [p.longitude, p.latitude] as Ponto));
    }
    enquadrar(map, marcos, false);
  }, [map, isLoaded, van, caminho, pontos]);

  return null;
}

// rumo (graus, 0 = norte, sentido horario) de um ponto pro outro
function rumo([lng1, lat1]: Ponto, [lng2, lat2]: Ponto): number {
  const rad = Math.PI / 180;
  const y = Math.sin((lng2 - lng1) * rad) * Math.cos(lat2 * rad);
  const x = Math.cos(lat1 * rad) * Math.sin(lat2 * rad) - Math.sin(lat1 * rad) * Math.cos(lat2 * rad) * Math.cos((lng2 - lng1) * rad);
  return (Math.atan2(y, x) / rad + 360) % 360;
}

// distancia aproximada em metros (so pra saber se andou o bastante)
function metros([lng1, lat1]: Ponto, [lng2, lat2]: Ponto): number {
  const k = 111_320;
  const dx = (lng2 - lng1) * k * Math.cos(((lat1 + lat2) / 2) * (Math.PI / 180));
  const dy = (lat2 - lat1) * k;
  return Math.hypot(dx, dy);
}

// pra onde a van esta indo: a direcao da rua no comeco do caminho (primeiro trecho de uns 10 m).
// usa o proprio caminho e nao a van, porque o GPS costuma ficar uns metros fora da linha
function rumoPeloCaminho(caminho: Ponto[]): number | null {
  const inicio = caminho[0];
  if (!inicio) return null;
  for (const ponto of caminho) {
    if (metros(inicio, ponto) >= 10) return rumo(inicio, ponto);
  }
  return null;
}

// van escolar vista de cima (frente pra cima), no formato da referencia do chefe: capo arredondado,
// para-brisa, retrovisores, tres janelas no teto e as janelas laterais. Cores pelos tokens do app
// (trip-map.css): a lataria usa --cor-texto, entao fica escura no tema claro e branca no escuro
function VanDeCima() {
  // ids unicos dos gradientes (dois mapas na tela nao se misturam)
  const id = useId().replace(/:/g, '');
  const lataria = `van-lataria-${id}`;
  const teto = `van-teto-${id}`;
  const capo = `van-capo-${id}`;
  return (
    <svg className="van-3d" viewBox="0 0 48 100" width="26" height="54" role="img" aria-label="Van escolar">
      <defs>
        {/* lataria: laterais mais escuras, como luz vindo de cima */}
        <linearGradient id={lataria} x1="0" x2="1" y1="0" y2="0">
          <stop offset="0" className="van-c-lateral" />
          <stop offset="0.2" className="van-c-corpo" />
          <stop offset="0.8" className="van-c-corpo" />
          <stop offset="1" className="van-c-lateral" />
        </linearGradient>
        {/* teto: brilho no meio, cai pras bordas */}
        <linearGradient id={teto} x1="0" x2="1" y1="0" y2="0">
          <stop offset="0" className="van-c-corpo" />
          <stop offset="0.5" className="van-c-brilho" />
          <stop offset="1" className="van-c-corpo" />
        </linearGradient>
        {/* capo: mais claro na frente */}
        <linearGradient id={capo} x1="0" x2="0" y1="0" y2="1">
          <stop offset="0" className="van-c-brilho" />
          <stop offset="1" className="van-c-corpo" />
        </linearGradient>
      </defs>

      {/* retrovisores */}
      <path className="van-detalhe" d="M5 25.5 L1.6 23 Q0.4 21.9 2 21.2 L7.5 22.6 Z" />
      <path className="van-detalhe" d="M43 25.5 L46.4 23 Q47.6 21.9 46 21.2 L40.5 22.6 Z" />

      {/* lataria */}
      <path className="van-contorno" fill={`url(#${lataria})`} d="M10 4 Q24 0 38 4 Q43 6 43 14 L43 92 Q43 98 37 98 L11 98 Q5 98 5 92 L5 14 Q5 6 10 4 Z" />

      {/* capo e farois */}
      <path fill={`url(#${capo})`} d="M9 7 Q24 3 39 7 Q41 10 41 17 L7 17 Q7 10 9 7 Z" />
      <rect className="van-farol" x="9.5" y="5.2" width="6" height="2.2" rx="1.1" />
      <rect className="van-farol" x="32.5" y="5.2" width="6" height="2.2" rx="1.1" />

      {/* para-brisa com reflexo */}
      <path className="van-vidro" d="M7.5 18 L40.5 18 Q41 22 38.5 25.5 L9.5 25.5 Q7 22 7.5 18 Z" />
      <path className="van-reflexo" d="M11 19.2 L20 19.2 L16 24.2 L10.6 24.2 Q9.6 22 11 19.2 Z" />

      {/* teto */}
      <rect fill={`url(#${teto})`} className="van-contorno-suave" x="9" y="27" width="30" height="67" rx="3.5" />

      {/* janelas do teto */}
      <rect className="van-vidro" x="17" y="35" width="14" height="8" rx="1.6" />
      <rect className="van-vidro" x="17" y="55" width="14" height="8" rx="1.6" />
      <rect className="van-vidro" x="17" y="75" width="14" height="8" rx="1.6" />

      {/* janelas laterais */}
      <rect className="van-vidro" x="5.6" y="30" width="2.6" height="13" rx="1" />
      <rect className="van-vidro" x="5.6" y="46" width="2.6" height="13" rx="1" />
      <rect className="van-vidro" x="5.6" y="62" width="2.6" height="13" rx="1" />
      <rect className="van-vidro" x="5.6" y="78" width="2.6" height="13" rx="1" />
      <rect className="van-vidro" x="39.8" y="30" width="2.6" height="13" rx="1" />
      <rect className="van-vidro" x="39.8" y="46" width="2.6" height="13" rx="1" />
      <rect className="van-vidro" x="39.8" y="62" width="2.6" height="13" rx="1" />
      <rect className="van-vidro" x="39.8" y="78" width="2.6" height="13" rx="1" />

      {/* lanternas */}
      <rect className="van-lanterna" x="8" y="95.2" width="6" height="2" rx="1" />
      <rect className="van-lanterna" x="34" y="95.2" width="6" height="2" rx="1" />
    </svg>
  );
}

// van 3D (vista de cima, como nos apps de corrida): desliza ate a posicao nova (~1 s, o GPS manda a cada 3 s)
// e gira pra direcao do motorista. Sem caminho, usa a direcao em que ela andou
function Van({ latitude, longitude, caminho }: { latitude: number; longitude: number; caminho: Ponto[] }) {
  const [estado, setEstado] = useState<{ p: Ponto; angulo: number }>({ p: [longitude, latitude], angulo: 0 });
  const atual = useRef(estado);

  useEffect(() => {
    const origem = atual.current;
    const destino: Ponto = [longitude, latitude];
    const andou = metros(origem.p, destino);
    const alvoAngulo = rumoPeloCaminho(caminho) ?? (andou >= 3 ? rumo(origem.p, destino) : origem.angulo);
    // gira pelo lado mais curto (de 350 pra 10 sao 20 graus, nao 340)
    const giro = ((alvoAngulo - origem.angulo + 540) % 360) - 180;
    if (andou === 0 && Math.abs(giro) < 0.5) return;
    const inicio = performance.now();
    let quadro = 0;
    const passo = (agora: number) => {
      const t = Math.min(1, (agora - inicio) / 1000);
      const suave = t * (2 - t);
      const novo = {
        p: [origem.p[0] + (destino[0] - origem.p[0]) * suave, origem.p[1] + (destino[1] - origem.p[1]) * suave] as Ponto,
        angulo: (origem.angulo + giro * suave + 360) % 360,
      };
      atual.current = novo;
      setEstado(novo);
      if (t < 1) quadro = requestAnimationFrame(passo);
    };
    quadro = requestAnimationFrame(passo);
    return () => cancelAnimationFrame(quadro);
  }, [latitude, longitude, caminho]);

  return (
    <MapMarker longitude={estado.p[0]} latitude={estado.p[1]} rotation={estado.angulo} rotationAlignment="map" pitchAlignment="map">
      <MarkerContent>
        <VanDeCima />
      </MarkerContent>
    </MapMarker>
  );
}

// setas brancas ao longo da linha mostrando o sentido da viagem (o caminho vai da van pros pontos).
// camada de simbolos do MapLibre presa na linha; volta sozinha quando o tema troca o estilo do mapa
const IMAGEM_SETA = 'vanroute-seta-rota';

function desenharSeta(): ImageData {
  const lado = 24;
  const canvas = document.createElement('canvas');
  canvas.width = lado;
  canvas.height = lado;
  const ctx = canvas.getContext('2d')!;
  // chevron apontando pra direita: o MapLibre alinha a direita da imagem com o sentido da linha
  ctx.strokeStyle = '#ffffff';
  ctx.lineWidth = 3.4;
  ctx.lineCap = 'round';
  ctx.lineJoin = 'round';
  ctx.beginPath();
  ctx.moveTo(8, 6);
  ctx.lineTo(15, 12);
  ctx.lineTo(8, 18);
  ctx.stroke();
  return ctx.getImageData(0, 0, lado, lado);
}

function SetasDaRota({ linha }: { linha: Ponto[] }) {
  const { map, isLoaded } = useMap();
  const fonte = 'vanroute-setas-fonte';
  const camada = 'vanroute-setas';

  useEffect(() => {
    if (!map || !isLoaded) return;
    if (!map.hasImage(IMAGEM_SETA)) map.addImage(IMAGEM_SETA, desenharSeta(), { pixelRatio: 2 });
    map.addSource(fonte, { type: 'geojson', data: { type: 'Feature', properties: {}, geometry: { type: 'LineString', coordinates: [] } } });
    map.addLayer({
      id: camada,
      type: 'symbol',
      source: fonte,
      layout: {
        'symbol-placement': 'line',
        'symbol-spacing': 64,
        'icon-image': IMAGEM_SETA,
        'icon-size': 1,
        'icon-allow-overlap': true,
        'icon-ignore-placement': true,
        'icon-rotation-alignment': 'map',
        'icon-keep-upright': false,
      },
    });
    return () => {
      try {
        if (map.getLayer(camada)) map.removeLayer(camada);
        if (map.getSource(fonte)) map.removeSource(fonte);
      } catch {
        // mapa ja destruido
      }
    };
  }, [map, isLoaded]);

  useEffect(() => {
    if (!map || !isLoaded) return;
    const origem = map.getSource(fonte) as MapLibreGL.GeoJSONSource | undefined;
    origem?.setData({ type: 'Feature', properties: {}, geometry: { type: 'LineString', coordinates: linha.length > 1 ? linha : [] } });
  }, [map, isLoaded, linha]);

  return null;
}

// linha azul com borda por baixo (como nos apps de corrida); a borda acompanha o fundo do tema
function Rota({ linha }: { linha: Ponto[] }) {
  const { resolvedTheme } = useMap();
  return (
    <>
      <MapRoute id="rota-borda" coordinates={linha} color={resolvedTheme === 'dark' ? '#1f2937' : '#ffffff'} width={9} opacity={1} interactive={false} />
      <MapRoute id="rota-linha" coordinates={linha} color="#0c66db" width={5} opacity={1} interactive={false} />
    </>
  );
}

function MapaViagem({ props, aoCarregar }: { props: PropsMapaViagem; aoCarregar: (map: MapLibreGL.Map | null) => void }) {
  const linha = (props.caminho ?? []) as Ponto[];
  return (
    <Map center={CENTRO_INICIAL} zoom={13} className="mapa-viagem">
      {linha.length > 1 && <Rota linha={linha} />}
      {linha.length > 1 && <SetasDaRota linha={linha} />}
      {props.pontos.map((ponto) => (
        <MapMarker key={ponto.id} longitude={ponto.longitude} latitude={ponto.latitude}>
          <MarkerContent>
            <div className={'marcador-ponto' + (ponto.alvo ? ' alvo' : '')}>
              <span>
                <i className={'bi ' + (ponto.tipo === 'escola' ? 'bi-mortarboard-fill' : 'bi-house-door-fill')} aria-hidden="true"></i>
              </span>
              {ponto.alvo && props.rotuloAlvo ? <em>{props.rotuloAlvo}</em> : null}
            </div>
          </MarkerContent>
        </MapMarker>
      ))}
      {props.van && <Van latitude={props.van.latitude} longitude={props.van.longitude} caminho={linha} />}
      <Camera props={props} aoCarregar={aoCarregar} />
    </Map>
  );
}

export function montarMapaViagem(alvo: HTMLElement) {
  const raiz = createRoot(alvo);
  let mapa: MapLibreGL.Map | null = null;
  let ultimas: PropsMapaViagem | null = null;
  const aoCarregar = (m: MapLibreGL.Map | null) => {
    mapa = m;
  };

  return {
    atualizar(props: PropsMapaViagem) {
      ultimas = props;
      raiz.render(<MapaViagem props={props} aoCarregar={aoCarregar} />);
    },
    // botao de foco: vai ate o motorista (a van). Sem posicao da van ainda, mostra a rota inteira
    focar() {
      if (!mapa || !ultimas) return;
      const { van, caminho, pontos } = ultimas;
      if (van) {
        mapa.flyTo({ center: [van.longitude, van.latitude], zoom: 17, duration: 600 });
        return;
      }
      enquadrar(mapa, [...((caminho ?? []) as Ponto[]), ...pontos.map((p) => [p.longitude, p.latitude] as Ponto)], true);
    },
    destruir() {
      raiz.unmount();
    },
  };
}
