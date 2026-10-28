import { TestBed } from '@angular/core/testing';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Router } from '@angular/router';
import { describe, it, expect, afterEach, vi } from 'vitest';
import { authInterceptor } from '../../../interceptor/auth.interceptor';
import { PopUpService } from '../../../shared/pop-up';
import { ContractProposalCard } from './contract-proposal-card';

const API = 'http://localhost:9090/api';

function fakeToken(payload: object) {
  const base64 = (obj: object) => btoa(JSON.stringify(obj)).replace(/=+$/, '');
  return `${base64({ alg: 'HS256' })}.${base64(payload)}.assinatura`;
}

const CONTRATO = {
  contractId: 'c-1',
  userDriverContractId: 'udc-1',
  status: 'PENDING',
  periodicity: 'WEEKLY',
  value: 350,
  startDate: '2027-02-01',
  endDate: '2027-06-30',
  studentId: 'aluno-1',
  studentName: 'Lucas Pereira',
  schoolId: 'escola-1',
  schoolName: 'Fatec Ipiranga',
  driverId: 'driver-1',
  driverName: 'Carlos',
  responsibleId: 'resp-1',
  responsibleName: 'Andreas Pereira',
  documentFileKey: null,
  documentType: 'TRANSPORT_CONTRACT',
};

describe('ContractProposalCard (card do contrato no chat)', () => {
  let http: HttpTestingController;
  let popUp: { show: ReturnType<typeof vi.fn>; showError: ReturnType<typeof vi.fn> };

  function criar(podeDecidir: boolean) {
    localStorage.setItem('token', fakeToken({ sub: 'resp-1', roles: [podeDecidir ? 'ROLE_RESPONSIBLE' : 'ROLE_DRIVER'] }));
    popUp = { show: vi.fn(), showError: vi.fn() };
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
        { provide: Router, useValue: { navigate: vi.fn() } },
        { provide: PopUpService, useValue: popUp },
      ],
    });
    http = TestBed.inject(HttpTestingController);

    const fixture = TestBed.createComponent(ContractProposalCard);
    fixture.componentRef.setInput('chatId', 'chat-1');
    fixture.componentRef.setInput('contractId', 'c-1');
    fixture.componentRef.setInput('podeDecidir', podeDecidir);
    fixture.detectChanges();

    http.expectOne(`${API}/contract/c-1`).flush(CONTRATO);
    fixture.detectChanges();
    return fixture;
  }

  afterEach(() => {
    http.verify();
    localStorage.clear();
    TestBed.resetTestingModule();
  });

  it('mostra o resumo real do contrato e a decisão pro responsável', () => {
    const fixture = criar(true);
    const texto = fixture.nativeElement.textContent;
    expect(texto).toContain('Contrato enviado');
    expect(texto).toContain('Lucas Pereira');
    expect(texto).toContain('Fatec Ipiranga · Semanal');
    expect(texto).toContain('Visualizar contrato');
    expect(texto).toContain('Decisão');
  });

  it('motorista não vê o botão de decisão, só o status', () => {
    const fixture = criar(false);
    const texto = fixture.nativeElement.textContent;
    expect(texto).not.toContain('Decisão');
    expect(texto).toContain('Aguardando decisão');
  });

  it('aceitar chama o PUT do chat e atualiza o card', () => {
    const fixture = criar(true);
    const card = fixture.componentInstance as any;

    card.abrirDecisao();
    card.decidir(true);
    // clique repetido nao manda outro PUT
    card.decidir(true);

    const req = http.expectOne(`${API}/chats/chat-1/contracts/c-1/accept`);
    expect(req.request.method).toBe('PUT');
    req.flush(null);
    fixture.detectChanges();

    expect(card.status()).toBe('ACTIVE');
    expect(fixture.nativeElement.textContent).toContain('Contrato aceito');
    expect(fixture.nativeElement.textContent).not.toContain('Decisão');
  });

  it('409 avisa que já foi decidido e recarrega o contrato', () => {
    const fixture = criar(true);
    const card = fixture.componentInstance as any;

    card.decidir(false);
    http.expectOne(`${API}/chats/chat-1/contracts/c-1/reject`).flush(null, { status: 409, statusText: 'Conflict' });
    expect(card.erroDecisao()).toContain('já foi decidido');

    http.expectOne(`${API}/contract/c-1`).flush({ ...CONTRATO, status: 'CANCELED' });
    expect(card.status()).toBe('CANCELED');
  });

  it('decisão que chega pelo chat (APPROVAL) vale mais que o status carregado', () => {
    const fixture = criar(true);
    fixture.componentRef.setInput('decisao', 'ACTIVE');
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Contrato aceito');
  });
});
