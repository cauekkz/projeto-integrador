import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpContext } from '@angular/common/http';
import { Observable, Subject, catchError, forkJoin, map, of, switchMap } from 'rxjs';
import { SKIP_ERROR_POPUP } from '../interceptor/http-context';
import { AuthService } from './auth.service';
import { API_URL, wsUrl } from './api';
import { OccurrenceType, tripResponse, tripStudent, tripUpdate, viagemAtiva } from './dto/trip/trip.dto';
import { RouteService } from './route.service';
import { Page } from './dto/page.dto';
import { tripHistoryFilter, tripHistoryItem, tripOccurrence } from './dto/group/group.dto';

// codigo de fechamento do backend: quem conectou nao pode ver essa viagem
export const WS_SEM_ACESSO = 1003;

// conexao com o WebSocket /trip/{tripId}: recebe o trip-update e (motorista) manda a posicao.
// cai a rede: reconecta sozinho com um token valido; o estado chega de novo ao conectar
export class TripSocket {
  readonly atualizacoes = new Subject<tripUpdate>();
  // fechou de vez (1003 = sem acesso); a tela decide o que fazer
  readonly fechou = new Subject<number>();

  private socket?: WebSocket;
  private reconexao?: ReturnType<typeof setTimeout>;
  private encerrado = false;

  constructor(
    readonly tripId: string,
    private auth: AuthService,
  ) {
    this.abrir();
  }

  enviarPosicao(latitude: number, longitude: number) {
    if (this.socket?.readyState !== WebSocket.OPEN) return;
    this.socket.send(JSON.stringify({ type: 'position', latitude, longitude }));
  }

  fechar() {
    this.encerrado = true;
    clearTimeout(this.reconexao);
    this.socket?.close();
    this.socket = undefined;
    this.atualizacoes.complete();
    this.fechou.complete();
  }

  private abrir() {
    // o WebSocket so confere o token na abertura: pega um valido (renovando se venceu)
    this.auth.getValidToken().subscribe({
      next: (token) => {
        if (this.encerrado || !token) return;
        const socket = new WebSocket(`${wsUrl('/trip/' + this.tripId)}?token=${encodeURIComponent(token)}`);
        this.socket = socket;
        socket.onmessage = (evento: MessageEvent) => {
          try {
            const dados = JSON.parse(evento.data);
            if (dados?.type === 'trip-update') this.atualizacoes.next(dados as tripUpdate);
          } catch (erro) {
            console.error('Erro ao ler o trip-update:', erro);
          }
        };
        socket.onclose = (evento: CloseEvent) => {
          if (this.socket === socket) this.socket = undefined;
          if (this.encerrado) return;
          if (evento.code === WS_SEM_ACESSO) {
            this.fechou.next(evento.code);
            return;
          }
          this.reconexao = setTimeout(() => this.abrir(), 3000);
        };
      },
      error: () => {
        if (!this.encerrado) this.reconexao = setTimeout(() => this.abrir(), 5000);
      },
    });
  }
}

// viagem do dia (docs/FLUXO_TRIP.md, secao 4)
@Injectable({
  providedIn: 'root',
})
export class TripService {
  private apiUrl = `${API_URL}/trip`;
  private http = inject(HttpClient);
  private auth = inject(AuthService);
  private routeService = inject(RouteService);

  // 404 = ainda nao tem viagem hoje (a tela trata, sem popup)
  getToday(routeId: string) {
    return this.http.get<tripResponse>(`${this.apiUrl}/route/${routeId}/today`, {
      context: new HttpContext().set(SKIP_ERROR_POPUP, true),
    });
  }

  // 400 (pendencia, sem van...) vai pro popup global; restart=true cancela a de hoje e cria outra (id novo)
  start(routeId: string, posicao: { latitude: number; longitude: number; vehicleId?: string }, restart = false) {
    return this.http.post<tripResponse>(`${this.apiUrl}/start/${routeId}`, posicao, {
      params: restart ? { restart: true } : {},
    });
  }

  // embarcou / desceu / ausente: o 409 (longe do ponto, ja marcado) a tela mostra como aviso
  board(tripId: string, studentId: string) {
    return this.http.put<tripStudent>(`${this.apiUrl}/${tripId}/student/${studentId}/board`, null, {
      context: new HttpContext().set(SKIP_ERROR_POPUP, true),
    });
  }

  alight(tripId: string, studentId: string) {
    return this.http.put<tripStudent>(`${this.apiUrl}/${tripId}/student/${studentId}/alight`, null, {
      context: new HttpContext().set(SKIP_ERROR_POPUP, true),
    });
  }

  absent(tripId: string, studentId: string) {
    return this.http.put<tripResponse>(`${this.apiUrl}/${tripId}/student/${studentId}/absent`, null, {
      context: new HttpContext().set(SKIP_ERROR_POPUP, true),
    });
  }

  finish(tripId: string) {
    return this.http.post<tripResponse>(`${this.apiUrl}/${tripId}/finish`, null);
  }

  // os responsaveis de todos os alunos da viagem sao avisados
  // historico por aluno (motorista: das viagens dele; responsavel: dos filhos). 400 = periodo invertido
  getHistory(filtro: tripHistoryFilter) {
    const params: Record<string, string | number> = { page: filtro.page ?? 0, size: filtro.size ?? 20 };
    if (filtro.studentId) params['studentId'] = filtro.studentId;
    if (filtro.routeId) params['routeId'] = filtro.routeId;
    if (filtro.from) params['from'] = filtro.from;
    if (filtro.to) params['to'] = filtro.to;
    return this.http.get<Page<tripHistoryItem>>(`${this.apiUrl}/history`, {
      params,
      context: new HttpContext().set(SKIP_ERROR_POPUP, true),
    });
  }

  getOccurrences(tripId: string) {
    return this.http.get<tripOccurrence[]>(`${this.apiUrl}/${tripId}/occurrences`, {
      context: new HttpContext().set(SKIP_ERROR_POPUP, true),
    });
  }

  addOccurrence(tripId: string, type: OccurrenceType, description: string) {
    return this.http.post(`${this.apiUrl}/${tripId}/occurrences`, { type, description });
  }

  // a rota com viagem aberta hoje (o back nao tem essa chamada: olha o "today" de cada rota ativa). null = nenhuma
  rotaComViagemAberta(): Observable<string | null> {
    return this.routeService.getMyRoutes({ active: true, size: 50 }).pipe(
      switchMap((pagina) => {
        const rotas = pagina.content ?? [];
        if (!rotas.length) return of(null);
        return forkJoin(
          rotas.map((r) =>
            this.getToday(r.id).pipe(
              map((trip) => (viagemAtiva(trip?.status) ? r.id : null)),
              catchError(() => of(null)),
            ),
          ),
        ).pipe(map((ids) => ids.find((id) => !!id) ?? null));
      }),
      catchError(() => of(null)),
    );
  }

  conectar(tripId: string): TripSocket {
    return new TripSocket(tripId, this.auth);
  }
}
