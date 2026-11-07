import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { Subject } from 'rxjs';
import { describe, it, expect, afterEach, vi } from 'vitest';
import { PopUpService } from '../../../shared/pop-up';
import { TripService } from '../../../services/trip.service';
import { DriverRoute } from './driver-route';
import { DriverHome } from '../driver-home/driver-home';
import { horaDe, minutosAte, tarefaDoAluno, tripUpdate } from '../../../services/dto/trip/trip.dto';

const ROTA = { id: 'r-1', name: 'Ida manhã', driverName: 'G', vehicle: { id: 'v-1', plate: 'ABC1D23', model: 'SPRINTER', color: 'PRATA', capacity: 15 } };
const VAN = { id: 'v-1', plate: 'ABC1D23', model: 'SPRINTER', color: 'PRATA', capacity: 15, status: 'ACTIVE' };

const LUCAS = { studentId: 'lucas', studentName: 'Lucas Pereira', locationType: 'HOME', boardedAt: null, deliveredAt: null, addressLabel: 'Casa', photoKey: null };
const ANA = { studentId: 'ana', studentName: 'Ana Martins', locationType: 'SCHOOL', boardedAt: null, deliveredAt: null, addressLabel: null, photoKey: null };

function atualizacao(parcial: Partial<tripUpdate> = {}): tripUpdate {
  return {
    type: 'trip-update', tripId: 't-1', routeName: 'Ida manhã', status: 'STARTED', following: true, mode: 'DRIVER',
    latitude: -23.55, longitude: -46.63, positionAt: 1, targetStopId: 'p-2', etaSeconds: 420,
    path: [[-46.63, -23.55], [-46.62, -23.54]], students: [],
    stops: [
      { routeStopId: 'p-2', orderIndex: 2, latitude: -23.54, longitude: -46.62, done: false, pathIndex: 1, etaSeconds: 420,
        street: 'R. Libertadores', number: 2026, neighborhood: 'Ipiranga', schoolIds: [], schoolNames: [],
        students: [{ ...LUCAS, action: 'BOARDING', status: 'PENDING' }] },
      { routeStopId: 'p-3', orderIndex: 3, latitude: -23.53, longitude: -46.61, done: false, pathIndex: null, etaSeconds: 900,
        street: 'R. Bom Pastor', number: 2400, neighborhood: 'Ipiranga', schoolIds: ['e'], schoolNames: ['Fatec Ipiranga'],
        students: [{ ...LUCAS, locationType: 'SCHOOL', addressLabel: null, action: 'ALIGHTING', status: 'PENDING' }] },
    ],
    ...parcial,
  } as tripUpdate;
}

describe('Viagem do motorista', () => {
  let http: HttpTestingController;
  let popUp: { show: ReturnType<typeof vi.fn>; showError: ReturnType<typeof vi.fn> };

  function configurar(routeId?: string, consulta: Record<string, string> = {}) {
    popUp = { show: vi.fn(), showError: vi.fn() };
    const providers: unknown[] = [provideHttpClient(), provideHttpClientTesting(), provideRouter([]), { provide: PopUpService, useValue: popUp }];
    if (routeId) providers.push({ provide: ActivatedRoute, useValue: { snapshot: { paramMap: convertToParamMap({ routeId }), queryParamMap: convertToParamMap(consulta) } } });
    TestBed.configureTestingModule({ providers: providers as never[] });
    http = TestBed.inject(HttpTestingController);
  }

  function responderResto() {
    for (const req of http.match(() => true)) {
      if (req.cancelled) continue;
      if (req.request.url.includes('unread-count')) req.flush({ count: 0 });
      else if (req.request.url.startsWith('/api/chats')) req.flush({ content: [], last: true, totalPages: 0 });
      else req.flush({});
    }
  }

  afterEach(() => {
    if (!http) return;
    responderResto();
    http.verify();
    TestBed.resetTestingModule();
    vi.unstubAllGlobals();
  });

  it('textos da viagem', () => {
    configurar();
    expect(tarefaDoAluno({ ...LUCAS, action: 'BOARDING', status: 'PENDING' } as never)).toBe('Buscar em casa');
    expect(tarefaDoAluno({ ...ANA, action: 'ALIGHTING', status: 'BOARDED' } as never)).toBe('Descer na escola');
    expect(tarefaDoAluno({ ...LUCAS, addressLabel: 'Natação', action: 'ALIGHTING', status: 'BOARDED' } as never)).toBe('Deixar em Natação');
    expect(horaDe('2026-11-05T07:32:10')).toBe('07:32');
    expect(minutosAte(420)).toBe(7);
    expect(minutosAte(20)).toBe(1);
  });

  it('home: lista as rotas com o topo de sempre', () => {
    configurar();
    const fixture = TestBed.createComponent(DriverHome);
    fixture.detectChanges();
    http.expectOne((r) => r.url === '/api/route/get-all-routes-and-stops').flush({ content: [{ ...ROTA, stops: [] }], last: true });
    fixture.detectChanges();
    const texto: string = fixture.nativeElement.textContent;
    expect(texto).toContain('Qual rota você vai fazer agora?');
    expect(texto).toContain('Ida manhã');
    expect(texto).toContain('Motorista');
  });

  describe('/driver-route/:routeId', () => {
    let socket: { atualizacoes: Subject<tripUpdate>; fechou: Subject<number>; enviarPosicao: ReturnType<typeof vi.fn>; fechar: ReturnType<typeof vi.fn> };

    function criar(hoje: object | null, consulta: Record<string, string> = {}) {
      configurar('r-1', consulta);
      socket = { atualizacoes: new Subject(), fechou: new Subject(), enviarPosicao: vi.fn(), fechar: vi.fn() };
      vi.spyOn(TestBed.inject(TripService), 'conectar').mockReturnValue(socket as never);
      const fixture = TestBed.createComponent(DriverRoute);
      fixture.detectChanges();
      http.expectOne('/api/route/r-1').flush(ROTA);
      http.expectOne('/api/route/r-1/stops').flush([]);
      const today = http.expectOne('/api/trip/route/r-1/today');
      if (hoje) today.flush(hoje);
      else today.flush({ message: 'not found' }, { status: 404, statusText: 'Not Found' });
      fixture.detectChanges();
      return fixture;
    }

    function gps(sucesso: boolean) {
      vi.stubGlobal('navigator', {
        ...navigator,
        geolocation: {
          getCurrentPosition: (ok: (p: object) => void, falha: () => void) =>
            sucesso ? ok({ coords: { latitude: -23.5, longitude: -46.6 } }) : falha(),
          watchPosition: () => 1,
          clearWatch: () => {},
        },
      });
    }

    it('sem viagem hoje: escolhe a van e inicia', async () => {
      const fixture = criar(null);
      http.expectOne('/api/vehicle/my-vehicles').flush([VAN]);
      fixture.detectChanges();
      expect(popUp.show).toHaveBeenCalledWith(expect.stringContaining('não manda a localização'), 'info', 'Deixe esta tela aberta');
      expect(fixture.nativeElement.textContent).toContain('ABC1D23 · Sprinter');

      gps(true);
      await fixture.componentInstance.iniciar();
      const req = http.expectOne('/api/trip/start/r-1');
      expect(req.request.body).toEqual({ latitude: -23.5, longitude: -46.6, vehicleId: 'v-1' });
      expect(req.request.params.has('restart')).toBe(false);
      req.flush({ id: 't-1', status: 'STARTED' });
      expect(TestBed.inject(TripService).conectar).toHaveBeenCalledWith('t-1');
    });

    it('localização negada não chama o backend', async () => {
      const fixture = criar(null);
      http.expectOne('/api/vehicle/my-vehicles').flush([VAN]);
      gps(false);
      await fixture.componentInstance.iniciar();
      expect(popUp.show).toHaveBeenCalledWith('Permita o uso da localização para iniciar a rota.', 'error', 'Sem acesso à localização');
      http.expectNone('/api/trip/start/r-1');
    });

    it('viagem aberta: continuar ou recomeçar do zero', async () => {
      const fixture = criar({ id: 't-1', status: 'IN_PROGRESS' });
      expect(popUp.show).toHaveBeenCalledWith(expect.any(String), 'warning', 'Esta rota já está em andamento');
      expect(fixture.nativeElement.textContent).toContain('Recomeçar do zero');

      gps(true);
      await fixture.componentInstance.iniciar(true);
      const req = http.expectOne((r) => r.url === '/api/trip/start/r-1');
      expect(req.request.params.get('restart')).toBe('true');
      req.flush({ id: 't-2', status: 'STARTED' });
      expect(TestBed.inject(TripService).conectar).toHaveBeenCalledWith('t-2');
    });

    it('veio do rodapé: abre direto o mapa e encerra a rota', () => {
      const fixture = criar({ id: 't-1', status: 'IN_PROGRESS' }, { continuar: '1' });
      expect(TestBed.inject(TripService).conectar).toHaveBeenCalledWith('t-1');
      expect(popUp.show).not.toHaveBeenCalledWith(expect.any(String), 'warning', 'Esta rota já está em andamento');
      socket.atualizacoes.next(atualizacao());
      fixture.detectChanges();
      const encerrar = [...fixture.nativeElement.querySelectorAll('button')].find((b: HTMLElement) => b.textContent?.trim() === 'Encerrar rota') as HTMLElement;
      encerrar.click();
      fixture.detectChanges();
      expect(fixture.nativeElement.textContent).toContain('Encerrar a rota agora?');
      (fixture.componentInstance as unknown as { encerrar(): void }).encerrar();
      http.expectOne('/api/trip/t-1/finish').flush({ id: 't-1', status: 'FINISHED' });
      expect(popUp.show).toHaveBeenCalledWith(expect.any(String), 'success', 'Rota de hoje finalizada');
    });

    it('finalizada hoje', () => {
      criar({ id: 't-1', status: 'FINISHED' });
      expect(popUp.show).toHaveBeenCalledWith(expect.any(String), 'success', 'Rota de hoje finalizada');
    });

    it('em viagem: próximo ponto, embarcou e o 409 de longe do ponto', () => {
      const fixture = criar({ id: 't-1', status: 'IN_PROGRESS' });
      fixture.componentInstance.continuar();
      socket.atualizacoes.next(atualizacao());
      fixture.detectChanges();
      const texto: string = fixture.nativeElement.textContent;
      expect(texto).toContain('Próximo ponto em 7 min');
      expect(texto).toContain('R. Libertadores, 2026');
      expect(texto).toContain('Buscar em casa');
      expect(texto).toContain('Aguardando');
      expect(texto).toContain('Próximos pontos');

      const embarcou = [...fixture.nativeElement.querySelectorAll('button')].find((b: HTMLElement) => b.textContent?.trim() === 'Embarcou') as HTMLElement;
      embarcou.click();
      http.expectOne('/api/trip/t-1/student/lucas/board').flush(
        { message: 'A van está a 3,2 km do ponto do aluno: chegue a menos de 1,0 km' },
        { status: 409, statusText: 'Conflict' },
      );
      expect(popUp.show).toHaveBeenCalledWith('A van está a 3,2 km do ponto do aluno: chegue a menos de 1,0 km', 'warning', 'Não foi possível concluir');
    });

    it('no ponto da escola mostra "Desceu"', () => {
      const fixture = criar({ id: 't-1', status: 'IN_PROGRESS' });
      fixture.componentInstance.continuar();
      const base = atualizacao();
      socket.atualizacoes.next({
        ...base,
        targetStopId: 'p-3',
        stops: [
          { ...base.stops![0], done: true, students: [{ ...base.stops![0].students[0], status: 'BOARDED' }] },
          { ...base.stops![1], students: [{ ...base.stops![1].students[0], status: 'BOARDED' }] },
        ],
      });
      fixture.detectChanges();
      expect(fixture.nativeElement.textContent).toContain('Fatec Ipiranga');
      expect(fixture.nativeElement.textContent).toContain('Descer na escola');
      const desceu = [...fixture.nativeElement.querySelectorAll('button')].find((b: HTMLElement) => b.textContent?.trim() === 'Desceu') as HTMLElement;
      desceu.click();
      http.expectOne('/api/trip/t-1/student/lucas/alight').flush({ studentId: 'lucas', status: 'DELIVERED' });
    });

    it('todos atendidos: lista final e finalizar', () => {
      const fixture = criar({ id: 't-1', status: 'IN_PROGRESS' });
      fixture.componentInstance.continuar();
      const base = atualizacao();
      socket.atualizacoes.next({
        ...base,
        targetStopId: null,
        etaSeconds: null,
        stops: base.stops!.map((p) => ({
          ...p, done: true,
          students: p.students.map((a) => ({ ...a, status: 'DELIVERED' as const, deliveredAt: '2026-11-05T07:32:00' })),
        })),
      });
      fixture.detectChanges();
      expect(fixture.nativeElement.textContent).toContain('Entregue às 07:32');
      fixture.componentInstance.finalizar();
      http.expectOne('/api/trip/t-1/finish').flush({ id: 't-1', status: 'FINISHED' });
      expect(socket.fechar).toHaveBeenCalled();
      expect(popUp.show).toHaveBeenCalledWith(expect.any(String), 'success', 'Rota de hoje finalizada');
    });

    it('registrar ocorrência', () => {
      const fixture = criar({ id: 't-1', status: 'IN_PROGRESS' });
      fixture.componentInstance.continuar();
      const comp = fixture.componentInstance as unknown as { abrirOcorrencia(): void; registrar(): void; descricao: string; tipoOcorrencia: { set(t: string): void } };
      comp.abrirOcorrencia();
      comp.registrar();
      expect(popUp.show).toHaveBeenCalledWith('Conte o que aconteceu', 'warning', 'Campos Incompletos');
      comp.tipoOcorrencia.set('VEHICLE');
      comp.descricao = ' Pneu furado ';
      comp.registrar();
      const req = http.expectOne('/api/trip/t-1/occurrences');
      expect(req.request.body).toEqual({ type: 'VEHICLE', description: 'Pneu furado' });
      req.flush({});
    });
  });
});
