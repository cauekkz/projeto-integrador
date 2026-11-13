import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ActivatedRoute, Router, convertToParamMap, provideRouter } from '@angular/router';
import { describe, it, expect, afterEach, vi } from 'vitest';
import { PopUpService } from '../../../shared/pop-up';
import { AuthService } from '../../../services/auth.service';
import { DriverGroups } from './driver-groups';
import { DriverGroup } from '../driver-group/driver-group';
import { TripHistory } from '../../common/trip-history/trip-history';
import { TripOccurrences } from '../../common/trip-occurrences/trip-occurrences';

const GRUPO = {
  id: 'g-1', name: 'Fatec manhã',
  students: [{ studentId: 'lucas', studentName: 'Lucas Pereira', photoKey: null }, { studentId: 'ana', studentName: 'Ana Martins', photoKey: null }],
};

describe('Grupos e histórico', () => {
  let http: HttpTestingController;
  let popUp: { show: ReturnType<typeof vi.fn>; showError: ReturnType<typeof vi.fn> };

  function configurar(opcoes: { param?: Record<string, string>; query?: Record<string, string>; roles?: string[] } = {}) {
    popUp = { show: vi.fn(), showError: vi.fn() };
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(), provideHttpClientTesting(), provideRouter([]),
        { provide: PopUpService, useValue: popUp },
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: convertToParamMap(opcoes.param ?? {}), queryParamMap: convertToParamMap(opcoes.query ?? {}) } } },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    vi.spyOn(TestBed.inject(AuthService), 'getRolesFromToken').mockReturnValue(opcoes.roles ?? ['ROLE_DRIVER']);
  }

  afterEach(() => {
    if (!http) return;
    for (const req of http.match(() => true)) {
      if (req.cancelled) continue;
      if (req.request.url.includes('unread-count')) req.flush({ count: 0 });
      else if (req.request.url.startsWith('/api/chats')) req.flush({ content: [], last: true, totalPages: 0 });
      else req.flush({});
    }
    http.verify();
    TestBed.resetTestingModule();
  });

  function botao(fixture: { nativeElement: HTMLElement }, texto: string, dentro = '') {
    return [...fixture.nativeElement.querySelectorAll(`${dentro} button`)].find((b) => b.textContent?.trim() === texto) as HTMLElement | undefined;
  }

  it('lista de grupos, vazio e novo grupo com 409', async () => {
    configurar();
    const fixture = TestBed.createComponent(DriverGroups);
    fixture.detectChanges();
    http.expectOne('/api/driver/groups').flush([]);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Você ainda não tem grupos');

    botao(fixture, 'Novo grupo')!.click();
    fixture.detectChanges();
    await fixture.whenStable();
    const input = fixture.nativeElement.querySelector('#nome-grupo') as HTMLInputElement;
    input.value = 'Fatec manhã';
    input.dispatchEvent(new Event('input'));
    botao(fixture, 'Criar grupo')!.click();
    const req = http.expectOne({ method: 'POST', url: '/api/driver/groups' });
    expect(req.request.body).toEqual({ name: 'Fatec manhã' });
    req.flush({ message: 'Você já tem um grupo com esse nome' }, { status: 409, statusText: 'Conflict' });
    expect(popUp.show).toHaveBeenCalledWith('Você já tem um grupo com esse nome', 'warning', 'Não foi possível concluir');
  });

  it('grupo: alunos, adicionar, avisar e apagar', () => {
    configurar({ param: { groupId: 'g-1' } });
    const navegar = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    const fixture = TestBed.createComponent(DriverGroup);
    fixture.detectChanges();
    http.expectOne('/api/driver/groups').flush([GRUPO]);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Alunos (2)');
    expect(fixture.nativeElement.textContent).toContain('AM');

    botao(fixture, 'Adicionar')!.click();
    http.expectOne((r) => r.url === '/api/contract/my-contracts').flush({
      content: [{ studentId: 'lucas', studentName: 'Lucas Pereira' }, { studentId: 'marina', studentName: 'Marina Pereira' }], last: true,
    });
    fixture.detectChanges();
    const opcoes = fixture.nativeElement.querySelectorAll('.opcao');
    expect(opcoes.length).toBe(1);
    botao(fixture, 'Adicionar ao grupo')!.click();
    http.expectOne({ method: 'POST', url: '/api/driver/groups/g-1/students/marina' })
      .flush({ message: 'Não existe contrato ativo com esse aluno' }, { status: 403, statusText: 'Forbidden' });
    expect(popUp.show).toHaveBeenCalledWith('Não existe contrato ativo com esse aluno', 'error', 'Sem permissão');

    botao(fixture, 'Voltar', '.folha')!.click();
    fixture.detectChanges();
    botao(fixture, 'Avisar que não vou buscar hoje')!.click();
    fixture.detectChanges();
    botao(fixture, 'Enviar aviso')!.click();
    http.expectOne({ method: 'POST', url: '/api/driver/groups/g-1/notice' }).flush({ notifiedResponsibles: 3 });
    expect(popUp.show).toHaveBeenCalledWith('3 responsáveis avisados.', 'success', 'Aviso enviado');

    fixture.detectChanges();
    botao(fixture, 'Apagar grupo')!.click();
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Apagar o grupo Fatec manhã?');
    botao(fixture, 'Apagar grupo', '.folha')!.click();
    http.expectOne({ method: 'DELETE', url: '/api/driver/groups/g-1' }).flush(null, { status: 204, statusText: 'No Content' });
    expect(navegar).toHaveBeenCalledWith(['/driver-groups']);
  });

  it('histórico: entregue, ausente, fechada pelo sistema e ocorrências', () => {
    configurar();
    const navegar = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    const fixture = TestBed.createComponent(TripHistory);
    fixture.detectChanges();
    const base = { tripStatus: 'FINISHED', startTime: '06:30:00', routeId: 'r-1', driverId: 'd-1', driverName: 'Gustavo', occurrences: 0 };
    http.expectOne((r) => r.url === '/api/trip/history').flush({
      content: [
        { ...base, tripId: 't-1', date: '2026-11-02', endTime: '07:20:00', routeName: 'Ida manhã', studentId: 'lucas', studentName: 'Lucas Pereira',
          boardingStatus: 'DELIVERED', boardingTime: '2026-11-02T06:42:00', unboardingTime: '2026-11-02T07:10:00', occurrences: 1 },
        { ...base, tripId: 't-1', date: '2026-11-02', endTime: '07:20:00', routeName: 'Ida manhã', studentId: 'ana', studentName: 'Ana Martins',
          boardingStatus: 'ABSENT', boardingTime: null, unboardingTime: null },
        { ...base, tripId: 't-2', date: '2026-10-30', endTime: null, routeName: 'Volta tarde', studentId: 'marina', studentName: 'Marina Pereira',
          boardingStatus: 'BOARDED', boardingTime: '2026-10-30T17:05:00', unboardingTime: null },
      ],
      last: true,
    });
    http.expectOne((r) => r.url === '/api/contract/my-contracts').flush({ content: [], last: true });
    http.expectOne((r) => r.url === '/api/route/get-all-routes-and-stops').flush({ content: [], last: true });
    fixture.detectChanges();
    const texto = fixture.nativeElement.textContent;
    expect(texto).toContain('Embarcou às 06:42 · Chegou às 07:10');
    expect(texto).toContain('Não embarcou');
    expect(texto).toContain('Embarcou às 17:05 · Viagem fechada pelo sistema');
    expect(texto).toContain('Entregue');
    expect(texto).toContain('Ausente');

    (fixture.nativeElement.querySelector('.ocorrencias') as HTMLElement).click();
    expect(navegar).toHaveBeenCalledWith(['/trip-history', 't-1', 'ocorrencias'], { queryParams: { data: '2026-11-02', rota: 'Ida manhã' } });

    // período invertido: aviso e a folha continua aberta
    botao(fixture, 'Filtrar')!.click();
    fixture.detectChanges();
    const comp = fixture.componentInstance as unknown as { rascunho: { from: string; to: string }; aplicarFiltro(): void };
    comp.rascunho.from = '2026-11-30';
    comp.rascunho.to = '2026-11-01';
    comp.aplicarFiltro();
    expect(popUp.show).toHaveBeenCalledWith('O fim do período não pode ser antes do início', 'error', 'Dados inválidos');
    expect((fixture.componentInstance as unknown as { filtroAberto(): boolean }).filtroAberto()).toBe(true);
  });

  it('responsável: filtra pelo filho vindo do detalhe; vazio', () => {
    configurar({ roles: ['ROLE_RESPONSIBLE'], query: { studentId: 'lucas' } });
    const fixture = TestBed.createComponent(TripHistory);
    fixture.detectChanges();
    const req = http.expectOne((r) => r.url === '/api/trip/history');
    expect(req.request.params.get('studentId')).toBe('lucas');
    req.flush({ content: [], last: true });
    http.expectOne((r) => r.url === '/api/student/my-children').flush({ content: [], last: true });
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Nenhuma viagem aqui');
    expect(fixture.nativeElement.textContent).toContain('Responsável Histórico');
  });

  it('ocorrências da viagem', () => {
    configurar({ param: { tripId: 't-1' }, query: { data: '2026-11-02', rota: 'Ida manhã' } });
    const fixture = TestBed.createComponent(TripOccurrences);
    fixture.detectChanges();
    http.expectOne('/api/trip/t-1/occurrences').flush([
      { id: 'o-1', tripId: 't-1', type: 'TRAFFIC', description: 'Acidente na marginal', dateTime: '2026-11-02T06:58:00', driverName: 'Gustavo Gómez' },
    ]);
    fixture.detectChanges();
    const texto = fixture.nativeElement.textContent;
    expect(texto).toContain('02/11 · Ida manhã');
    expect(texto).toContain('Trânsito');
    expect(texto).toContain('06:58');
    expect(texto).toContain('Registrada por Gustavo Gómez');
  });
});
