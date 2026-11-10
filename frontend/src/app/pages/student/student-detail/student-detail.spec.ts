import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ActivatedRoute, Router, convertToParamMap, provideRouter } from '@angular/router';
import { describe, it, expect, afterEach, vi } from 'vitest';
import { PopUpService } from '../../../shared/pop-up';
import { AuthService } from '../../../services/auth.service';
import { StudentDetail } from './student-detail';
import { ConfirmacaoService } from '../../../shared/confirmacao/confirmacao.service';

const LUCAS = { id: 'lucas', name: 'Lucas Pereira', photoKey: null };
const MARINA = { id: 'marina', name: 'Marina Pereira', photoKey: null };
const ENDERECO = (id: string, street: string) => ({
  id, street, zipCode: '04213000', city: 'São Paulo', neighborhood: 'Ipiranga', number: 2026, state: 'SP', latitude: null, longitude: null,
});
const CASA = { id: 'sa-1', label: 'Casa', address: ENDERECO('a-1', 'R. Libertadores') };
const EU_ADMIN = { responsibleId: 'eu', name: 'Andreas Pereira', relationType: 'FINANCIAL', admin: true };
const CARLA = { responsibleId: 'carla', name: 'Carla Pereira', relationType: 'WITHDRAWAL', admin: false };

describe('Filho em detalhe (/student/:id)', () => {
  let http: HttpTestingController;
  let popUp: { show: ReturnType<typeof vi.fn>; showError: ReturnType<typeof vi.fn> };

  afterEach(() => {
    if (!http) return;
    for (const req of http.match(() => true)) if (!req.cancelled) req.flush({});
    http.verify();
    TestBed.resetTestingModule();
  });

  function criar(dados: { responsaveis: object[]; rotas?: object[]; faltas?: object[]; enderecos?: object[] }) {
    popUp = { show: vi.fn(), showError: vi.fn() };
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: PopUpService, useValue: popUp },
        { provide: AuthService, useValue: { getUserIdFromToken: () => 'eu' } },
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: convertToParamMap({ studentId: 'lucas' }) } } },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    const fixture = TestBed.createComponent(StudentDetail);
    fixture.detectChanges();
    http.expectOne((r) => r.url === '/api/student/my-children').flush({ content: [LUCAS, MARINA], last: true });
    http.expectOne('/api/student/lucas/addresses').flush(dados.enderecos ?? [CASA]);
    http.expectOne('/api/student/lucas/responsibles').flush(dados.responsaveis);
    http.expectOne('/api/student/lucas/routes').flush(dados.rotas ?? []);
    http.expectOne('/api/student/lucas/absences').flush(dados.faltas ?? []);
    fixture.detectChanges();
    return fixture;
  }

  function botao(fixture: { nativeElement: HTMLElement }, texto: string) {
    return [...fixture.nativeElement.querySelectorAll('button')].find((b) => b.textContent?.trim() === texto) as HTMLElement | undefined;
  }

  function naFolha(fixture: { nativeElement: HTMLElement }, texto: string) {
    return [...fixture.nativeElement.querySelectorAll('.folha button')].find((b) => b.textContent?.trim() === texto) as HTMLElement;
  }

  it('admin: endereços com lixeira, responsáveis com "Passar o admin" e "Remover"', () => {
    const fixture = criar({ responsaveis: [CARLA, EU_ADMIN] });
    const texto = fixture.nativeElement.textContent;
    expect(texto).toContain('Você é o admin');
    expect(texto).toContain('R. Libertadores, 2026 · Ipiranga, São Paulo');
    expect(texto).toContain('Andreas Pereira (você)');
    expect(texto).toContain('Retirada');
    expect(fixture.nativeElement.querySelector('.lixeira')).not.toBeNull();
    expect(botao(fixture, 'Deixar de ser responsável')).toBeUndefined();
    expect(texto).toContain('Lucas ainda não está em nenhuma rota');

    // remover a Carla: 409 mantem a folha e mostra o aviso
    botao(fixture, 'Remover')!.click();
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Remover Carla Pereira?');
    naFolha(fixture, 'Remover').click();
    http.expectOne({ method: 'DELETE', url: '/api/student/lucas/responsibles/carla' })
      .flush({ message: 'Esse responsável tem contrato ou proposta em andamento com este aluno: cancele antes' }, { status: 409, statusText: 'Conflict' });
    expect(popUp.show).toHaveBeenCalledWith(expect.stringContaining('cancele antes'), 'warning', 'Não foi possível concluir');
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Remover Carla Pereira?');

    // passar o admin
    botao(fixture, 'Voltar')!.click();
    fixture.detectChanges();
    botao(fixture, 'Passar o admin')!.click();
    fixture.detectChanges();
    naFolha(fixture, 'Passar o admin').click();
    http.expectOne({ method: 'PUT', url: '/api/student/lucas/responsibles/carla/admin' }).flush(null, { status: 204, statusText: 'No Content' });
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).not.toContain('Você é o admin');
    expect(botao(fixture, 'Deixar de ser responsável')).toBeTruthy();
  });

  it('não admin: sem lixeira e pode deixar de ser responsável', () => {
    const fixture = criar({ responsaveis: [{ ...EU_ADMIN, responsibleId: 'andreas' }, { ...CARLA, responsibleId: 'eu' }] });
    expect(fixture.nativeElement.textContent).toContain('Quem cuida dos endereços e dos responsáveis é o admin.');
    expect(fixture.nativeElement.querySelector('.lixeira')).toBeNull();
    expect(botao(fixture, 'Adicionar')).toBeUndefined();

    const navegar = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    botao(fixture, 'Deixar de ser responsável')!.click();
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Deixar de ser responsável de Lucas?');
    const confirmar = [...fixture.nativeElement.querySelectorAll('.folha button')].find((b) => (b as HTMLElement).textContent?.trim() === 'Deixar de ser responsável') as HTMLElement;
    confirmar.click();
    http.expectOne({ method: 'DELETE', url: '/api/student/lucas/responsibles/eu' }).flush(null, { status: 204, statusText: 'No Content' });
    expect(navegar).toHaveBeenCalledWith(['/dependentes']);
  });

  it('rotas, avisar falta (409) e "vai sim"', async () => {
    const fixture = criar({
      responsaveis: [EU_ADMIN],
      rotas: [{ id: 'r-1', name: 'Ida manhã' }, { id: 'r-2', name: 'Volta tarde' }],
      faltas: [{ id: 'f-1', studentId: 'lucas', routeId: 'r-1', routeName: 'Ida manhã', date: '2026-11-10' }],
    });
    expect(fixture.nativeElement.textContent).toContain('10/11 · Ida manhã');

    fixture.nativeElement.querySelectorAll('.cartao .acao-titulo')[1].click(); // "Não vai" da Volta tarde
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Avisar falta');
    expect(fixture.nativeElement.querySelector('.opcao.marcada')?.textContent).toContain('Volta tarde');
    botao(fixture, 'Confirmar falta')!.click();
    const req = http.expectOne({ method: 'POST', url: '/api/student/lucas/absences' });
    expect(req.request.body.routeId).toBe('r-2');
    req.flush({ message: 'Falta já avisada para esse dia nessa rota' }, { status: 409, statusText: 'Conflict' });
    expect(popUp.show).toHaveBeenCalledWith('Falta já avisada para esse dia nessa rota', 'warning', 'Não foi possível concluir');

    botao(fixture, 'Cancelar')!.click();
    fixture.detectChanges();
    const pergunta = vi.spyOn(TestBed.inject(ConfirmacaoService), 'perguntar').mockResolvedValue(true);
    botao(fixture, 'Lucas vai sim')!.click();
    await new Promise((r) => setTimeout(r)); // espera a resposta do pop-up de confirmacao
    expect(pergunta).toHaveBeenCalledWith(expect.objectContaining({ titulo: 'Desfazer a falta de Lucas?' }));
    http.expectOne({ method: 'DELETE', url: '/api/student/lucas/absences/f-1' }).flush(null, { status: 204, statusText: 'No Content' });
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).not.toContain('Faltas avisadas');
  });

  it('novo endereço: usa o da família ou digita', async () => {
    const fixture = criar({ responsaveis: [EU_ADMIN] });
    botao(fixture, 'Adicionar')!.click();
    http.expectOne((r) => r.url === '/api/student/my-children').flush({ content: [LUCAS, MARINA], last: true });
    http.expectOne('/api/student/marina/addresses').flush([CASA, { id: 'sa-9', label: 'Casa', address: ENDERECO('a-9', 'Av. Paulista') }]);
    fixture.detectChanges();
    // a casa que o Lucas ja tem nao aparece
    const opcoes = fixture.nativeElement.querySelectorAll('.tela-cheia .opcao');
    expect(opcoes.length).toBe(1);
    expect(opcoes[0].textContent).toContain('Casa de Marina');

    await fixture.whenStable();
    const apelido = fixture.nativeElement.querySelector('#apelido') as HTMLInputElement;
    apelido.value = 'Casa da avó';
    apelido.dispatchEvent(new Event('input'));
    await fixture.whenStable();
    botao(fixture, 'Adicionar endereço')!.click();
    expect(popUp.show).not.toHaveBeenCalled();
    const req = http.expectOne({ method: 'POST', url: '/api/student/lucas/addresses' });
    expect(req.request.body).toEqual({ label: 'Casa da avó', addressId: 'a-9' });
    req.flush({ id: 'sa-2', label: 'Casa da avó', address: ENDERECO('a-9', 'Av. Paulista') });
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.tela-cheia')).toBeNull();
    expect(fixture.nativeElement.textContent).toContain('Casa da avó');
  });

  it('403 ao abrir volta pros dependentes', () => {
    popUp = { show: vi.fn(), showError: vi.fn() };
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(), provideHttpClientTesting(), provideRouter([]),
        { provide: PopUpService, useValue: popUp },
        { provide: AuthService, useValue: { getUserIdFromToken: () => 'eu' } },
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: convertToParamMap({ studentId: 'lucas' }) } } },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    const navegar = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    const fixture = TestBed.createComponent(StudentDetail);
    fixture.detectChanges();
    http.expectOne('/api/student/lucas/addresses').flush({}, { status: 403, statusText: 'Forbidden' });
    expect(navegar).toHaveBeenCalledWith(['/dependentes']);
    expect(popUp.show).toHaveBeenCalledWith(expect.any(String), 'warning', 'Sem acesso');
  });
});
