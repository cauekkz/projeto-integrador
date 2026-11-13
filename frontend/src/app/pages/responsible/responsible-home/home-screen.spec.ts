import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ActivatedRoute, Router, convertToParamMap, provideRouter } from '@angular/router';
import { BehaviorSubject, Subject } from 'rxjs';
import { describe, it, expect, afterEach, vi } from 'vitest';
import { PopUpService } from '../../../shared/pop-up';
import { TripService } from '../../../services/trip.service';
import { HomeScreen } from './home-screen';
import { Footer } from '../../../shared/footer/footer';
import { ResponsibleRoute, textoDaVanDaViagem } from '../responsible-route/responsible-route';
import { dataLocal, studentTripToday, tripUpdate } from '../../../services/dto/trip/trip.dto';
import { ConfirmacaoService } from '../../../shared/confirmacao/confirmacao.service';

const LUCAS = { id: 'lucas', name: 'Lucas Pereira', photoKey: null };

const VIAGEM: studentTripToday = {
  tripId: 't-1', routeId: 'r-1', routeName: 'Ida manhã', tripStatus: 'STARTED', boardingStatus: 'PENDING',
  driverId: 'd-1', driverName: 'Gustavo Gómez', driverPhotoKey: null, driverRating: 4.6,
  vehiclePlate: 'ABC1D23', vehicleModel: 'M.BENZ/SPRINTER', vehicleColor: 'PRATA',
};

describe('Viagem do responsável', () => {
  let http: HttpTestingController;
  let popUp: { show: ReturnType<typeof vi.fn>; showError: ReturnType<typeof vi.fn> };

  function configurar(rota?: { tripId: string; aluno?: string }) {
    popUp = { show: vi.fn(), showError: vi.fn() };
    const providers: unknown[] = [provideHttpClient(), provideHttpClientTesting(), provideRouter([]), { provide: PopUpService, useValue: popUp }];
    if (rota) {
      providers.push({
        provide: ActivatedRoute,
        useValue: {
          snapshot: { queryParamMap: convertToParamMap(rota.aluno ? { aluno: rota.aluno } : {}), paramMap: convertToParamMap({ tripId: rota.tripId }) },
          paramMap: new BehaviorSubject(convertToParamMap({ tripId: rota.tripId })),
        },
      });
    }
    TestBed.configureTestingModule({ providers: providers as never[] });
    http = TestBed.inject(HttpTestingController);
  }

  afterEach(() => {
    if (!http) return;
    for (const req of http.match(() => true)) {
      if (req.cancelled) continue;
      if (req.request.url.includes('unread-count')) req.flush({ count: 0 });
      else if (req.request.url.startsWith('/api/chats')) req.flush({ content: [], last: true, totalPages: 0 });
      // listas (faltas, viagens de hoje, rotas do filho) respondem lista vazia
      else if (/\/(absences|today-trips|routes)$/.test(req.request.url)) req.flush([]);
      else req.flush({});
    }
    http.verify();
    TestBed.resetTestingModule();
  });

  describe('/responsible-home', () => {
    function criar(viagens: studentTripToday[], faltas: object[] = []) {
      configurar();
      const fixture = TestBed.createComponent(HomeScreen);
      fixture.detectChanges();
      http.expectOne((r) => r.url === '/api/student/my-children').flush({ content: [LUCAS], last: true });
      http.expectOne('/api/student/lucas/today-trips').flush(viagens);
      http.expectOne('/api/student/lucas/absences').flush(faltas);
      fixture.detectChanges();
      return fixture;
    }

    function botao(fixture: { nativeElement: HTMLElement }, texto: string) {
      return [...fixture.nativeElement.querySelectorAll('button')].find((b) => b.textContent?.trim() === texto) as HTMLElement | undefined;
    }

    it('van ainda não saiu: não vai hoje', () => {
      const fixture = criar([]);
      expect(fixture.nativeElement.textContent).toContain('A van ainda não saiu');
      expect(botao(fixture, 'Não vai outro dia')).toBeTruthy();

      botao(fixture, 'Não vai hoje')!.click();
      http.expectOne('/api/student/lucas/routes').flush([{ id: 'r-1', name: 'Ida manhã' }]);
      fixture.detectChanges();
      expect(fixture.nativeElement.textContent).toContain('Lucas não vai hoje?');

      botao(fixture, 'Avisar falta')!.click();
      const req = http.expectOne('/api/student/lucas/absences');
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual({ routeId: 'r-1', date: dataLocal() });
      req.flush({ id: 'a-1', studentId: 'lucas', routeId: 'r-1', routeName: 'Ida manhã', date: dataLocal() });
      expect(popUp.show).toHaveBeenCalledWith(expect.stringContaining('não vai hoje'), 'success', 'Falta avisada');
    });

    it('viagem em andamento: acompanhar', () => {
      const fixture = criar([VIAGEM]);
      expect(fixture.nativeElement.textContent).toContain('Ida manhã');
      expect(fixture.nativeElement.textContent).toContain('Aguardando');
      const navegar = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
      botao(fixture, 'Acompanhar')!.click();
      expect(navegar).toHaveBeenCalledWith(['/responsible-route', 't-1'], { queryParams: { aluno: 'lucas' } });
      expect(botao(fixture, 'Não vai hoje')).toBeTruthy();
    });

    it('já embarcou: sem "Não vai hoje"', () => {
      const fixture = criar([{ ...VIAGEM, boardingStatus: 'BOARDED' }]);
      expect(fixture.nativeElement.textContent).toContain('Embarcou');
      expect(botao(fixture, 'Não vai hoje')).toBeUndefined();
    });

    it('falta avisada: desfazer com 409', async () => {
      const fixture = criar([], [{ id: 'a-1', studentId: 'lucas', routeId: 'r-1', routeName: 'Ida manhã', date: dataLocal() }]);
      expect(fixture.nativeElement.textContent).toContain('Falta avisada · Ida manhã · hoje');
      const pergunta = vi.spyOn(TestBed.inject(ConfirmacaoService), 'perguntar').mockResolvedValue(true);
      botao(fixture, 'Lucas vai sim')!.click();
      await new Promise((r) => setTimeout(r)); // espera a resposta do pop-up de confirmacao
      expect(pergunta).toHaveBeenCalledWith(expect.objectContaining({ titulo: 'Desfazer a falta de Lucas?' }));
      http.expectOne('/api/student/lucas/absences/a-1').flush(
        { message: 'A viagem já começou: a falta não pode mais ser desfeita' },
        { status: 409, statusText: 'Conflict' },
      );
      expect(popUp.show).toHaveBeenCalledWith('A viagem já começou: a falta não pode mais ser desfeita', 'warning', 'Não foi possível concluir');
    });

    it('acesso removido', () => {
      configurar();
      const fixture = TestBed.createComponent(HomeScreen);
      fixture.detectChanges();
      http.expectOne((r) => r.url === '/api/student/my-children').flush({ content: [LUCAS], last: true });
      http.expectOne('/api/student/lucas/absences').flush([]);
      http.expectOne('/api/student/lucas/today-trips').flush({}, { status: 403, statusText: 'Forbidden' });
      expect(popUp.show).toHaveBeenCalledWith('Você foi removido dos responsáveis deste filho.', 'warning', 'Você não tem mais acesso a esta viagem');
    });
  });

  describe('/responsible-route/:tripId', () => {
    let socket: { atualizacoes: Subject<tripUpdate>; fechou: Subject<number>; fechar: ReturnType<typeof vi.fn> };

    function criar() {
      configurar({ tripId: 't-1', aluno: 'lucas' });
      socket = { atualizacoes: new Subject(), fechou: new Subject(), fechar: vi.fn() };
      vi.spyOn(TestBed.inject(TripService), 'conectar').mockReturnValue(socket as never);
      const fixture = TestBed.createComponent(ResponsibleRoute);
      fixture.detectChanges();
      http.expectOne('/api/student/lucas/today-trips').flush([VIAGEM]);
      fixture.detectChanges();
      return fixture;
    }

    function atualizacao(parcial: Partial<tripUpdate>): tripUpdate {
      return {
        type: 'trip-update', tripId: 't-1', routeName: 'Ida manhã', status: 'STARTED', following: false, mode: 'STATUS',
        latitude: null, longitude: null, positionAt: null, targetStopId: null, etaSeconds: null, path: null, stops: null,
        students: [{ studentId: 'lucas', studentName: 'Lucas Pereira', action: 'BOARDING', locationType: 'HOME', status: 'PENDING',
          boardedAt: null, deliveredAt: null, addressLabel: 'Casa', photoKey: null }],
        ...parcial,
      } as tripUpdate;
    }

    it('texto da van', () => {
      configurar();
      expect(textoDaVanDaViagem(VIAGEM)).toBe('Sprinter prata · ABC1D23');
    });

    it('STATUS, FOLLOWING e ON_BOARD', () => {
      const fixture = criar();
      // recolhido: so o resumo; a seta pra cima puxa os dados do motorista
      expect(fixture.nativeElement.textContent).not.toContain('Gustavo Gómez');
      expect(fixture.nativeElement.querySelector('app-footer')).not.toBeNull();
      fixture.nativeElement.querySelector('.seta').click();
      fixture.detectChanges();
      expect(fixture.nativeElement.textContent).toContain('Gustavo Gómez');
      expect(fixture.nativeElement.textContent).toContain('4,6');

      socket.atualizacoes.next(atualizacao({}));
      fixture.detectChanges();
      expect(fixture.nativeElement.textContent).toContain('Ainda não é a vez de Lucas');

      socket.atualizacoes.next(atualizacao({ mode: 'FOLLOWING', following: true, latitude: -23.55, longitude: -46.63, etaSeconds: 1320, path: [[-46.63, -23.55], [-46.62, -23.54]] }));
      fixture.detectChanges();
      expect(fixture.nativeElement.textContent).toContain('Chega em 22 min');
      expect(fixture.nativeElement.textContent).toContain('A caminho');

      const base = atualizacao({});
      socket.atualizacoes.next({ ...base, mode: 'ON_BOARD', latitude: -23.55, longitude: -46.63,
        students: [{ ...base.students[0], status: 'BOARDED', boardedAt: '2026-11-05T07:02:00' }] });
      fixture.detectChanges();
      expect(fixture.nativeElement.textContent).toContain('Lucas está na van');
      expect(fixture.nativeElement.textContent).toContain('Embarcou às 07:02');
    });

    it('entregue', () => {
      const fixture = criar();
      const base = atualizacao({});
      socket.atualizacoes.next({ ...base, mode: 'DONE', students: [{ ...base.students[0], status: 'DELIVERED', deliveredAt: '2026-11-05T07:32:00' }] });
      fixture.detectChanges();
      expect(fixture.nativeElement.textContent).toContain('Lucas foi entregue às 07:32');
    });

    it('sem acesso (1003) volta pra home', () => {
      criar();
      const navegar = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
      socket.fechou.next(1003);
      expect(popUp.show).toHaveBeenCalledWith(expect.any(String), 'warning', 'Você não tem mais acesso a esta viagem');
      expect(navegar).toHaveBeenCalledWith(['/responsible-home']);
    });

    it('recomeçada: abre a viagem nova', () => {
      criar();
      const navegar = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
      socket.atualizacoes.next(atualizacao({ status: 'CANCELED' }));
      http.expectOne('/api/student/lucas/today-trips').flush([{ ...VIAGEM, tripId: 't-2' }]);
      expect(navegar).toHaveBeenCalledWith(['/responsible-route', 't-2'], { queryParams: { aluno: 'lucas' }, replaceUrl: true });
    });
  });
  it('a casinha do responsável abre a viagem rodando ou avisa que não tem; o meio abre os filhos', () => {
    configurar();
    const fixture = TestBed.createComponent(Footer);
    fixture.componentInstance.tipoUsuario = 'responsavel';
    fixture.detectChanges();
    const navegar = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);

    fixture.componentInstance.selecionarAba('meio');
    expect(navegar).toHaveBeenCalledWith(['/responsible-home']);

    fixture.componentInstance.clicarHome();
    http.expectOne((r) => r.url === '/api/student/my-children').flush({ content: [LUCAS], last: true });
    http.expectOne('/api/student/lucas/today-trips').flush([]);
    expect(navegar).toHaveBeenCalledWith(['/responsible-route'], { state: { semViagem: true } });

    fixture.componentInstance.clicarHome();
    http.expectOne((r) => r.url === '/api/student/my-children').flush({ content: [LUCAS], last: true });
    http.expectOne('/api/student/lucas/today-trips').flush([VIAGEM]);
    expect(navegar).toHaveBeenCalledWith(['/responsible-route', 't-1'], { queryParams: { aluno: 'lucas' } });
  });
});
