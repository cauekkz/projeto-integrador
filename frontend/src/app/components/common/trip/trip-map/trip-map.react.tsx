// ponte React -> Angular do mapa da viagem: desenha com o componente Map do mapcn (MapLibre + CARTO).
// so e carregado por import() no TripMap, entao React + MapLibre ficam num pedaco separado do app.
// claro/escuro: o mapcn le o data-theme do <html> (ThemeService) e troca o fundo sozinho
import type * as MapLibreGL from 'maplibre-gl';
import { useEffect, useRef, useState } from 'react';
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

// a van desliza ate a posicao nova (~1 s) em vez de pular; o GPS manda a cada 3 s
function Van({ latitude, longitude }: { latitude: number; longitude: number }) {
  const [posicao, setPosicao] = useState<Ponto>([longitude, latitude]);
  const atual = useRef<Ponto>(posicao);

  useEffect(() => {
    const origem = atual.current;
    const destino: Ponto = [longitude, latitude];
    if (origem[0] === destino[0] && origem[1] === destino[1]) return;
    const inicio = performance.now();
    let quadro = 0;
    const passo = (agora: number) => {
      const t = Math.min(1, (agora - inicio) / 1000);
      const suave = t * (2 - t);
      const p: Ponto = [origem[0] + (destino[0] - origem[0]) * suave, origem[1] + (destino[1] - origem[1]) * suave];
      atual.current = p;
      setPosicao(p);
      if (t < 1) quadro = requestAnimationFrame(passo);
    };
    quadro = requestAnimationFrame(passo);
    return () => cancelAnimationFrame(quadro);
  }, [latitude, longitude]);

  return (
    <MapMarker longitude={posicao[0]} latitude={posicao[1]}>
      <MarkerContent>
        <div className="marcador-van" style={{ zIndex: 2 }}>
          <span>
            <i className="bi bi-bus-front-fill" aria-hidden="true"></i>
          </span>
        </div>
      </MarkerContent>
    </MapMarker>
  );
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
      {props.van && <Van latitude={props.van.latitude} longitude={props.van.longitude} />}
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
