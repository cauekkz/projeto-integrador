import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ActivatedRoute, Router, convertToParamMap } from '@angular/router';
import { describe, it, expect, afterEach, vi } from 'vitest';
import { PopUpService } from '../../shared/pop-up';
import { ContractDraftService } from '../driver-contract/contract-draft.service';
import { ContractView } from './contract-view';

function fakeToken(payload: object) {
  const base64 = (obj: object) => btoa(JSON.stringify(obj)).replace(/=+$/, '');
  return `${base64({ alg: 'HS256' })}.${base64(payload)}.assinatura`;
}

const CONTRATO = {
  contractId: 'c-1',
  userDriverContractId: 'udc-1',
  status: 'ACTIVE',
  periodicity: 'MONTHLY',
  value: 350,
  startDate: '2026-01-01',
  endDate: '2026-12-31',
  studentId: 'aluno-1',
  studentName: 'Lucas Pereira',
  schoolId: 'escola-1',
  schoolName: 'Fatec Ipiranga',
  schoolAddress: { street: 'R. Bom Pastor', number: 2400, neighborhood: null, city: null, state: null, zipCode: null },
  driverId: 'driver-1',
  driverName: 'Gustavo Gómez',
  responsibleId: 'resp-1',
  responsibleName: 'Andreas Pereira',
  documentFileKey: null,
  documentType: 'TRANSPORT_CONTRACT',
  documentStatus: 'SIGNED',
  signatures: [
    { userId: 'driver-1', name: 'Gustavo Gómez', signedAt: '2026-01-20T10:00:00', valid: true },
    { userId: 'resp-1', name: 'Andreas Pereira', signedAt: '2026-01-21T10:00:00', valid: true },
  ],
  cancellations: [] as any[],
  overduePayments: 0,
  studentResponsibles: [
    { responsibleId: 'resp-1', name: 'Andreas Pereira', relationType: 'FINANCIAL', admin: true },
    { responsibleId: 'resp-2', name: 'Carla Pereira', relationType: 'LEGAL', admin: false },
  ],
};

const PEDIDO = {
  id: 'p-1',
  status: 'PENDING',
  unilateral: false,
  reason: 'mudança de escola',
  requestedById: 'resp-1',
  requestedByName: 'Andreas Pereira',
  requestedAt: '2026-02-13T10:00:00',
  effectiveDate: '2026-03-15',
  decidedById: null,
  decidedByName: null,
  decidedAt: null,
};

const vazio = { content: [], totalElements: 0, totalPages: 0, last: true };

describe('ContractView (detalhe do contrato)', () => {
  let http: HttpTestingController;
  let popUp: { show: ReturnType<typeof vi.fn>; showError: ReturnType<typeof vi.fn> };
  let router: { navigate: ReturnType<typeof vi.fn> };

  function criar(motorista: boolean, contrato: object = CONTRATO, chatId: string | null = null) {
    localStorage.setItem(
      'token',
      fakeToken({ sub: motorista ? 'driver-1' : 'resp-1', roles: [motorista ? 'ROLE_DRIVER' : 'ROLE_RESPONSIBLE'] }),
    );
    popUp = { show: vi.fn(), showError: vi.fn() };
    router = { navigate: vi.fn() };
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: Router, useValue: router },
        { provide: PopUpService, useValue: popUp },
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: {
              paramMap: convertToParamMap({ contractId: 'c-1' }),
              queryParamMap: convertToParamMap(chatId ? { chatId } : {}),
            },
          },
        },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    const fixture = TestBed.createComponent(ContractView);
    fixture.detectChanges();
    http.expectOne('/api/contract/c-1').flush(contrato);
    fixture.detectChanges();
    return fixture;
  }

  // header (usuario logado) e checagem de renovacao
  function responderResto() {
    for (const req of http.match(() => true)) {
      if (req.request.url.includes('my-contracts') || req.request.url.startsWith('/api/chats?')) req.flush(vazio);
      else if (req.request.url.includes('unread-count')) req.flush({ count: 0 });
      else if (req.request.url.includes('/users/me')) req.flush({ name: 'X' });
      else req.flush(null);
    }
  }

  afterEach(() => {
    responderResto();
    http.verify();
    localStorage.clear();
    TestBed.resetTestingModule();
  });

  it('mostra status, escola com endereço, assinaturas e responsáveis do aluno', () => {
    const fixture = criar(false);
    responderResto();
    fixture.detectChanges();
    const texto: string = fixture.nativeElement.textContent;
    expect(texto).toContain('Ativo');
    expect(texto).toContain('Fatec Ipiranga · R. Bom Pastor, 2400');
    expect(texto).toContain('Gustavo Gómez assinou em 20/01');
    expect(texto).toContain('Andreas Pereira · Financeiro · Admin');
    expect(texto).toContain('Carla Pereira · Legal');
    expect(texto).toContain('Cancelar contrato');
    // responsavel nao renova
    expect(texto).not.toContain('Renovar');
  });

  it('agendado mostra "Agendado — começa em"', () => {
    const fixture = criar(false, { ...CONTRATO, status: 'SCHEDULED', startDate: '2026-02-01' });
    expect(fixture.nativeElement.textContent).toContain('Agendado — começa em 01/02.');
  });

  it('assinatura inválida avisa "Termos alterados depois da assinatura"', () => {
    const fixture = criar(false, {
      ...CONTRATO,
      status: 'INACTIVE',
      signatures: [{ userId: 'resp-1', name: 'Andreas Pereira', signedAt: '2026-01-21T10:00:00', valid: false }],
    });
    const texto: string = fixture.nativeElement.textContent;
    expect(texto).toContain('Termos alterados depois da assinatura');
    expect(texto).not.toContain('Cancelar contrato');
  });

  it('pedir cancelamento: aviso "Como funciona", motivo obrigatório e POST com o motivo', () => {
    const fixture = criar(false);
    const tela = fixture.componentInstance as any;

    tela.abrirCancelamento();
    expect(popUp.show).toHaveBeenCalledWith(expect.stringContaining('cancelado em 30 dias'), 'warning', 'Como funciona');

    tela.motivo = '   ';
    tela.pedirCancelamento();
    expect(popUp.show).toHaveBeenCalledWith('Informe o motivo do cancelamento', 'error', 'Dados inválidos');
    http.expectNone('/api/contract/c-1/cancellation');

    tela.motivo = 'Mudança de escola';
    tela.pedirCancelamento();
    const req = http.expectOne('/api/contract/c-1/cancellation');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ reason: 'Mudança de escola' });
    req.flush({ ...PEDIDO, requestedById: 'resp-1' });

    expect(popUp.show).toHaveBeenCalledWith(
      'Cancela em 15/03. Até lá o contrato continua valendo.',
      'warning',
      'Você pediu o cancelamento',
    );
    http.expectOne('/api/contract/c-1').flush({ ...CONTRATO, cancellations: [PEDIDO] });
  });

  it('cancelamento unilateral avisa que foi cancelado na hora', () => {
    const fixture = criar(true);
    responderResto();
    const tela = fixture.componentInstance as any;
    tela.motivo = 'parcelas em atraso';
    tela.pedirCancelamento();
    http
      .expectOne('/api/contract/c-1/cancellation')
      .flush({ ...PEDIDO, status: 'ACCEPTED', unilateral: true, requestedById: 'driver-1' });
    expect(popUp.show).toHaveBeenCalledWith(expect.stringContaining('30 dias ou mais'), 'warning', 'Contrato cancelado na hora');
    http.expectOne('/api/contract/c-1').flush({ ...CONTRATO, status: 'CANCELED' });
  });

  it('pedido da outra parte: aviso com o motivo e "Aceitar e cancelar agora"', () => {
    const fixture = criar(true, { ...CONTRATO, cancellations: [PEDIDO] });
    const texto: string = fixture.nativeElement.textContent;
    expect(popUp.show).toHaveBeenCalledWith(
      'Motivo: mudança de escola. Cancela sozinho em 15/03, ou agora se você aceitar.',
      'warning',
      'Andreas Pereira pediu o cancelamento',
    );
    expect(texto).toContain('Cancela em 15/03');
    expect(texto).toContain('Aceitar e cancelar agora');
    expect(texto).not.toContain('Cancelar contrato');

    (fixture.componentInstance as any).aceitarCancelamento();
    const req = http.expectOne('/api/contract/c-1/cancellation/accept');
    expect(req.request.method).toBe('PUT');
    req.flush({ ...PEDIDO, status: 'ACCEPTED' });
    http.expectOne('/api/contract/c-1').flush({ ...CONTRATO, status: 'CANCELED' });
  });

  it('pedido meu: "Desistir do cancelamento" manda DELETE', () => {
    const fixture = criar(false, { ...CONTRATO, cancellations: [PEDIDO] });
    expect(fixture.nativeElement.textContent).toContain('Desistir do cancelamento');

    (fixture.componentInstance as any).desistirCancelamento();
    const req = http.expectOne('/api/contract/c-1/cancellation');
    expect(req.request.method).toBe('DELETE');
    req.flush({ ...PEDIDO, status: 'WITHDRAWN' });
    http.expectOne('/api/contract/c-1').flush(CONTRATO);
  });

  it('motorista renova: acha o chat do responsável e abre a proposta com início = fim + 1', () => {
    const fixture = criar(true);
    for (const req of http.match((r) => r.url.includes('my-contracts'))) req.flush(vazio);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Renovar');

    (fixture.componentInstance as any).renovar();
    http
      .expectOne((r) => r.url === '/api/chats?page=0&size=50')
      .flush({ content: [{ id: 'chat-9', otherUserId: 'resp-1', otherUserName: 'Andreas' }], last: true, totalPages: 1 });

    const draft = TestBed.inject(ContractDraftService);
    expect(draft.chatId()).toBe('chat-9');
    expect(draft.studentId()).toBe('aluno-1');
    expect(draft.dataInicio()).toBe('01/01/2027');
    expect(draft.valorCentavos()).toBe(35000);
    expect(draft.renovacao()).toBe('Lucas Pereira');
    expect(router.navigate).toHaveBeenCalledWith(['/driver-contract', 'chat-9']);
  });

  it('não oferece renovar se já existe contrato pendente ou agendado pro aluno', () => {
    const fixture = criar(true);
    const reqs = http.match((r) => r.url.includes('my-contracts'));
    reqs[0].flush({ ...vazio, totalElements: 1 });
    reqs[1].flush(vazio);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).not.toContain('Renovar');
  });
});
