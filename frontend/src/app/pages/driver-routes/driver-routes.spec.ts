import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ActivatedRoute, Router, convertToParamMap, provideRouter } from '@angular/router';
import { describe, it, expect, afterEach, vi } from 'vitest';
import { PopUpService } from '../../shared/pop-up';
import { DriverRoutes } from './driver-routes';
import { DriverRouteEditor } from '../driver-route-editor/driver-route-editor';
import { Footer } from '../../shared/footer/footer';
import { resumoDias, textoPendencia } from '../../services/dto/route/route.dto';

const ENDERECO_ANA = {
  id: 'end-ana', street: 'Av. Paulista', zipCode: '01310-100', city: 'São Paulo',
  neighborhood: 'Bela Vista', number: 1000, state: 'SP', latitude: 0, longitude: 0,
};
const ENDERECO_ESCOLA = {
  id: 'end-escola', street: 'R. Bom Pastor', zipCode: '04203-000', city: 'São Paulo',
  neighborhood: 'Ipiranga', number: 2400, state: 'SP', latitude: 0, longitude: 0,
};

const PONTO_CASA = {
  id: 'p-1', orderIndex: 1, address: ENDERECO_ANA, schools: [],
  students: [{ id: 'ss-1', studentId: 'ana', studentName: 'Ana Martins', stopId: 'p-1', locationType: 'HOME', action: 'BOARDING', weekdays: [] }],
};
const PONTO_ESCOLA = {
  id: 'p-2', orderIndex: 2, address: ENDERECO_ESCOLA,
  schools: [{ id: 'sc-1', schoolId: 'esc-1', stopId: 'p-2' }],
  students: [{ id: 'ss-2', studentId: 'ana', studentName: 'Ana Martins', stopId: 'p-2', locationType: 'SCHOOL', action: 'ALIGHTING', weekdays: ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY'] }],
};

const OPCOES = {
  students: [
    { studentId: 'ana', studentName: 'Ana Martins', addresses: [
      { id: 'sa-casa', label: 'Casa', address: ENDERECO_ANA },
      { id: 'sa-natacao', label: 'Natação', address: { ...ENDERECO_ANA, id: 'end-natacao', street: 'R. Haddock Lobo', number: 200 } },
    ] },
  ],
};

const VAN = { id: 'v-1', plate: 'ABC1D23', model: 'SPRINTER', color: 'PRATA', capacity: 15 };

describe('Rotas do motorista', () => {
  let http: HttpTestingController;
  let popUp: { show: ReturnType<typeof vi.fn>; showError: ReturnType<typeof vi.fn> };

  function configurar(routeId?: string) {
    popUp = { show: vi.fn(), showError: vi.fn() };
    const providers: unknown[] = [
      provideHttpClient(),
      provideHttpClientTesting(),
      provideRouter([]),
      { provide: PopUpService, useValue: popUp },
    ];
    if (routeId) {
      providers.push({ provide: ActivatedRoute, useValue: { snapshot: { paramMap: convertToParamMap({ routeId }) } } });
    }
    TestBed.configureTestingModule({ providers: providers as never[] });
    http = TestBed.inject(HttpTestingController);
  }

  // header e footer pedem o usuario logado e as notificacoes
  function responderResto() {
    for (const req of http.match(() => true)) {
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
  });

  it('resume os dias e as pendências como no Figma', () => {
    configurar();
    expect(resumoDias([])).toBe('Todos os dias');
    expect(resumoDias(['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY'])).toBe('Seg a Sex');
    expect(resumoDias(['THURSDAY', 'MONDAY', 'TUESDAY', 'WEDNESDAY'])).toBe('Seg a Qui');
    expect(resumoDias(['MONDAY', 'WEDNESDAY', 'FRIDAY'])).toBe('Seg, Qua e Sex');
    expect(textoPendencia({ studentId: 'l', studentName: 'Lucas Pereira', missingBoarding: ['FRIDAY'], missingAlighting: [] }))
      .toBe('Lucas: falta embarque na sexta');
  });

  describe('lista /driver-routes', () => {
    function criar(rotas: object[]) {
      configurar();
      const fixture = TestBed.createComponent(DriverRoutes);
      fixture.detectChanges();
      const req = http.expectOne((r) => r.url === '/api/route/get-all-routes-and-stops');
      expect(req.request.params.get('active')).toBe('true');
      req.flush({ content: rotas, last: true });
      fixture.detectChanges();
      return fixture;
    }

    it('mostra o cartão com pontos e alunos', () => {
      const texto: string = criar([{ id: 'r-1', name: 'Ida manhã', driverName: 'G', vehicle: null, stops: [PONTO_CASA, PONTO_ESCOLA] }])
        .nativeElement.textContent;
      expect(texto).toContain('Ida manhã');
      expect(texto).toContain('2 pontos');
      expect(texto).toContain('Ativas');
    });

    it('sem rotas mostra o vazio do Figma', () => {
      const texto: string = criar([]).nativeElement.textContent;
      expect(texto).toContain('Você ainda não tem rotas');
      expect(texto).toContain('A ida e a volta são rotas diferentes.');
    });

    it('"Todas" pede active=false', () => {
      const fixture = criar([{ id: 'r-1', name: 'Ida', driverName: 'G', vehicle: null, stops: [] }]);
      const todas = [...fixture.nativeElement.querySelectorAll('.chip')].find((b: HTMLElement) => b.textContent?.includes('Todas')) as HTMLElement;
      todas.click();
      const req = http.expectOne((r) => r.url === '/api/route/get-all-routes-and-stops');
      expect(req.request.params.get('active')).toBe('false');
      req.flush({ content: [], last: true });
    });

    it('nova rota cria e abre o editor', () => {
      const fixture = criar([]);
      const navegar = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
      const comp = fixture.componentInstance as unknown as { abrirNova(): void; criar(): void; nome: string };
      comp.abrirNova();
      comp.nome = '  Ida manhã ';
      comp.criar();
      const req = http.expectOne('/api/route/create-route');
      expect(req.request.body).toEqual({ name: 'Ida manhã' });
      req.flush({ id: 'r-9', name: 'Ida manhã', driverName: 'G', vehicle: null });
      expect(navegar).toHaveBeenCalledWith(['/driver-routes', 'r-9']);
    });

    it('nova rota sem nome avisa e não chama o backend', () => {
      const fixture = criar([]);
      const comp = fixture.componentInstance as unknown as { abrirNova(): void; criar(): void };
      comp.abrirNova();
      comp.criar();
      expect(popUp.show).toHaveBeenCalledWith('Dê um nome para a rota', 'warning', 'Campos Incompletos');
      http.expectNone('/api/route/create-route');
    });
  });

  describe('editor /driver-routes/:routeId', () => {
    function criar(pontos: object[], pendencias: object[], vehicle: object | null = VAN) {
      configurar('r-1');
      const fixture = TestBed.createComponent(DriverRouteEditor);
      fixture.detectChanges();
      http.expectOne('/api/route/r-1').flush({ id: 'r-1', name: 'Ida manhã', driverName: 'G', vehicle });
      http.expectOne('/api/route/r-1/stops').flush(pontos);
      http.expectOne('/api/route/r-1/pending').flush(pendencias);
      http.expectOne('/api/route/stop-options').flush(OPCOES);
      http.expectOne('/api/school/my-schools').flush([{ id: 'esc-1', name: 'Fatec Ipiranga' }]);
      fixture.detectChanges();
      return fixture;
    }

    it('rota vazia: aviso de montar e "Nenhuma van escolhida"', () => {
      const texto: string = criar([], [], null).nativeElement.textContent;
      expect(texto).toContain('Nenhuma van escolhida');
      expect(texto).toContain('Adicionar aluno');
      expect(popUp.show).toHaveBeenCalledWith(expect.stringContaining('Cada aluno precisa'), 'info', 'Monte a rota aluno por aluno');
    });

    it('mostra a van, os pontos, a escola e o apelido do endereço', () => {
      const texto: string = criar([PONTO_CASA, PONTO_ESCOLA], [{ studentId: 'ana', studentName: 'Ana Martins', missingBoarding: [], missingAlighting: ['SATURDAY'] }])
        .nativeElement.textContent;
      expect(texto).toContain('ABC1D23 · Sprinter · Prata');
      expect(texto).toContain('Av. Paulista, 1000');
      expect(texto).toContain('Fatec Ipiranga');
      expect(texto).toContain('Casa · Todos os dias');
      expect(texto).toContain('Escola · Seg a Sex');
      expect(texto).not.toContain('Iniciar rota');
      expect(popUp.show).toHaveBeenCalledWith('Ana: falta desembarque na sábado', 'warning', 'A viagem ainda não pode iniciar');
    });

    it('rota pronta: "Iniciar rota" abre a viagem', () => {
      const fixture = criar([PONTO_CASA, PONTO_ESCOLA], []);
      expect(popUp.show).toHaveBeenCalledWith('Nenhuma pendência. A viagem já pode iniciar.', 'success', 'Rota pronta');
      const navegar = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
      const iniciar = [...fixture.nativeElement.querySelectorAll('button')].find((b: HTMLElement) => b.textContent?.includes('Iniciar rota')) as HTMLElement;
      iniciar.click();
      expect(navegar).toHaveBeenCalledWith(['/driver-route', 'r-1']);
    });

    it('adicionar aluno: embarque na Casa num ponto novo, com os dias e a posição', () => {
      const fixture = criar([PONTO_ESCOLA], [{ studentId: 'ana', studentName: 'Ana Martins', missingBoarding: [], missingAlighting: [] }]);
      const comp = fixture.componentInstance as unknown as { abrirAdicionar(): void };
      comp.abrirAdicionar();
      http.expectOne('/api/route/stop-options').flush(OPCOES);
      fixture.detectChanges();

      const clicar = (texto: string) => {
        const botao = [...fixture.nativeElement.querySelectorAll('app-add-student-to-route button')]
          .find((b: HTMLElement) => b.textContent?.trim().startsWith(texto)) as HTMLElement;
        botao.click();
        fixture.detectChanges();
      };
      clicar('Ana Martins');
      clicar('Continuar');
      expect(fixture.nativeElement.textContent).toContain('Passo 2 de 3');
      clicar('Seg');
      clicar('Qua');
      clicar('Continuar');
      expect(fixture.nativeElement.textContent).toContain('Em qual ponto?');
      clicar('Adicionar à rota');

      const req = http.expectOne('/api/route/r-1/add-student/ana');
      expect(req.request.body).toEqual({
        action: 'BOARDING', locationType: 'HOME', weekdays: ['MONDAY', 'WEDNESDAY'], newStop: true, studentAddressId: 'sa-casa',
      });
      req.flush({ stops: [PONTO_CASA, PONTO_ESCOLA], pending: [] });
      fixture.detectChanges();
      expect(fixture.nativeElement.querySelector('app-add-student-to-route')).toBeNull();
      expect(fixture.nativeElement.textContent).toContain('Av. Paulista, 1000');
    });

    it('desembarque na escola num ponto que já existe manda routeStopId', () => {
      const fixture = criar([PONTO_CASA, PONTO_ESCOLA], []);
      fixture.componentInstance['abrirAdicionar']();
      http.expectOne('/api/route/stop-options').flush(OPCOES);
      fixture.detectChanges();
      const clicar = (texto: string) => {
        const botao = [...fixture.nativeElement.querySelectorAll('app-add-student-to-route button')]
          .find((b: HTMLElement) => b.textContent?.trim().startsWith(texto)) as HTMLElement;
        botao.click();
        fixture.detectChanges();
      };
      clicar('Ana Martins');
      clicar('Continuar');
      clicar('Desembarque');
      clicar('Escola');
      clicar('Continuar');
      clicar('Ponto 2');
      clicar('Adicionar à rota');
      const req = http.expectOne('/api/route/r-1/add-student/ana');
      expect(req.request.body).toEqual({ action: 'ALIGHTING', locationType: 'SCHOOL', weekdays: [], routeStopId: 'p-2' });
      req.flush({ stops: [PONTO_CASA, PONTO_ESCOLA], pending: [] });
    });

    it('apagar ponto com aluno avisa antes, sem chamar o backend', () => {
      const fixture = criar([PONTO_CASA, PONTO_ESCOLA], []);
      const comp = fixture.componentInstance as unknown as { abrirPonto(p: object): void; apagarPonto(): void };
      comp.abrirPonto(PONTO_CASA);
      comp.apagarPonto();
      expect(popUp.show).toHaveBeenCalledWith('Tire antes os alunos e as escolas dele.', 'warning', 'Para apagar este ponto');
      http.expectNone('/api/route/stop/p-1');
    });

    it('trocar posição do ponto chama o swap-order', () => {
      const fixture = criar([PONTO_CASA, PONTO_ESCOLA], []);
      const comp = fixture.componentInstance as unknown as { abrirPonto(p: object): void; salvarPosicao(): void; novaPosicao: { set(n: number): void } };
      comp.abrirPonto(PONTO_CASA);
      comp.novaPosicao.set(2);
      comp.salvarPosicao();
      const req = http.expectOne('/api/route/stop/p-1/swap-order');
      expect(req.request.body).toEqual({ orderIndex: 2 });
      req.flush({ stops: [PONTO_CASA, PONTO_ESCOLA], pending: [] });
    });

    it('ajustar aluno: muda os dias e tira do ponto', () => {
      const fixture = criar([PONTO_CASA, PONTO_ESCOLA], []);
      const comp = fixture.componentInstance as unknown as {
        abrirAluno(a: object, p: object): void; alternarDia(d: string): void; salvarAluno(): void; tirarAluno(): void;
      };
      comp.abrirAluno(PONTO_CASA.students[0], PONTO_CASA);
      comp.alternarDia('FRIDAY');
      comp.salvarAluno();
      const req = http.expectOne('/api/route/student-stop/ss-1/weekdays');
      expect(req.request.body).toEqual({ weekdays: ['FRIDAY'] });
      req.flush({ stops: [PONTO_CASA, PONTO_ESCOLA], pending: [] });

      comp.abrirAluno(PONTO_CASA.students[0], PONTO_CASA);
      comp.tirarAluno();
      http.expectOne((r) => r.method === 'DELETE' && r.url === '/api/route/student-stop/ss-1')
        .flush({ stops: [PONTO_ESCOLA], pending: [] });
    });

    it('trocar a van padrão', () => {
      const fixture = criar([PONTO_CASA], [], null);
      const comp = fixture.componentInstance as unknown as { abrirVan(): void; salvarVan(): void; vanEscolhida: { set(v: string): void } };
      comp.abrirVan();
      http.expectOne('/api/vehicle/my-vehicles').flush([{ ...VAN, status: 'ACTIVE' }]);
      comp.vanEscolhida.set('v-1');
      comp.salvarVan();
      http.expectOne((r) => r.method === 'PUT' && r.url === '/api/route/r-1/set-vehicle/v-1')
        .flush({ id: 'r-1', name: 'Ida manhã', driverName: 'G', vehicle: VAN });
      fixture.detectChanges();
      expect(fixture.nativeElement.textContent).toContain('ABC1D23 · Sprinter · Prata');
    });

    it('rota que ele deixou (403) mostra o aviso', () => {
      configurar('r-1');
      const fixture = TestBed.createComponent(DriverRouteEditor);
      fixture.detectChanges();
      http.expectOne('/api/route/r-1').flush({}, { status: 403, statusText: 'Forbidden' });
      for (const req of http.match((r) => r.url.startsWith('/api/route/') || r.url.startsWith('/api/school'))) {
        if (!req.cancelled) req.flush([]);
      }
      fixture.detectChanges();
      expect(fixture.nativeElement.textContent).toContain('Você não está mais nesta rota.');
    });
  });

  it('o meio do rodapé do motorista: viagem aberta ou escolher a rota', () => {
    configurar();
    const fixture = TestBed.createComponent(Footer);
    fixture.componentInstance.tipoUsuario = 'motorista';
    fixture.detectChanges();
    const navegar = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    // sem viagem aberta: escolher a rota
    fixture.componentInstance.selecionarAba('meio');
    http.expectOne((r) => r.url === '/api/route/get-all-routes-and-stops').flush({ content: [{ id: 'r-1', name: 'Ida', stops: [] }], last: true });
    http.expectOne('/api/trip/route/r-1/today').flush({}, { status: 404, statusText: 'Not Found' });
    expect(navegar).toHaveBeenCalledWith(['/driver-routes']);
    // com viagem aberta: direto pro mapa da viagem
    fixture.componentInstance.selecionarAba('meio');
    http.expectOne((r) => r.url === '/api/route/get-all-routes-and-stops').flush({ content: [{ id: 'r-1', name: 'Ida', stops: [] }], last: true });
    http.expectOne('/api/trip/route/r-1/today').flush({ id: 't-1', status: 'IN_PROGRESS' });
    expect(navegar).toHaveBeenCalledWith(['/driver-route', 'r-1'], { queryParams: { continuar: 1 } });
    expect(fixture.nativeElement.querySelector('.icone-rota')).not.toBeNull();
  });
});
