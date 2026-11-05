import { TestBed } from '@angular/core/testing';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Router } from '@angular/router';
import { describe, it, expect, afterEach, vi } from 'vitest';
import { authInterceptor } from '../../../interceptor/auth.interceptor';
import { PopUpService } from '../../../shared/pop-up';
import { ContractProposalCard } from './contract-proposal-card';

const API = '/api';

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

  it('motorista vê "Proposta enviada" e pode retirar, sem o botão de decisão', () => {
    const fixture = criar(false);
    const texto = fixture.nativeElement.textContent;
    expect(texto).not.toContain('Decisão');
    expect(texto).toContain('Proposta enviada');
    expect(texto).toContain('Você ainda pode retirar');
    expect(texto).toContain('Retirar proposta');
  });

  it('retirar chama o PUT withdraw e o card vira "Proposta Retirada"', () => {
    const fixture = criar(false);
    const card = fixture.componentInstance as any;

    card.abrirRetirada();
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Isso não tem volta');

    card.retirar();
    card.retirar();
    const req = http.expectOne(`${API}/chats/chat-1/contracts/c-1/withdraw`);
    expect(req.request.method).toBe('PUT');
    req.flush(null);
    fixture.detectChanges();

    const texto = fixture.nativeElement.textContent;
    expect(texto).toContain('Proposta Retirada');
    expect(texto).toContain('Retirada');
    expect(texto).not.toContain('Retirar proposta');
  });

  it('aceitar chama o PUT do chat e recarrega: agendado mostra a data de início', () => {
    const fixture = criar(true);
    const card = fixture.componentInstance as any;

    card.abrirDecisao();
    card.decidir(true);
    // clique repetido nao manda outro PUT
    card.decidir(true);

    const req = http.expectOne(`${API}/chats/chat-1/contracts/c-1/accept`);
    expect(req.request.method).toBe('PUT');
    req.flush(null);
    http.expectOne(`${API}/contract/c-1`).flush({ ...CONTRATO, status: 'SCHEDULED' });
    fixture.detectChanges();

    const texto = fixture.nativeElement.textContent;
    expect(texto).toContain('Proposta aceita');
    expect(texto).toContain('Agendado: começa em 01/02.');
    expect(texto).toContain('Aceita');
    expect(texto).not.toContain('Decisão');
  });

  it('409 vai pro popup com a mensagem do backend e recarrega o contrato', () => {
    const fixture = criar(true);
    const card = fixture.componentInstance as any;

    card.decidir(true);
    http
      .expectOne(`${API}/chats/chat-1/contracts/c-1/accept`)
      .flush({ message: 'A data de início desta proposta já passou' }, { status: 409, statusText: 'Conflict' });
    expect(popUp.showError).toHaveBeenCalled();

    http.expectOne(`${API}/contract/c-1`).flush({ ...CONTRATO, status: 'CANCELED' });
    expect(card.status()).toBe('CANCELED');
  });

  it('REJECTION "Proposta Retirada" pelo chat mostra a proposta retirada', () => {
    const fixture = criar(true);
    fixture.componentRef.setInput('decisao', 'WITHDRAWN');
    fixture.detectChanges();
    const texto = fixture.nativeElement.textContent;
    expect(texto).toContain('O motorista retirou esta proposta');
    expect(texto).not.toContain('Decisão');
  });

  it('APPROVAL pelo chat recarrega o contrato pra saber o status real', () => {
    const fixture = criar(true);
    fixture.componentRef.setInput('decisao', 'ACTIVE');
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Proposta aceita');
    http.expectOne(`${API}/contract/c-1`).flush({ ...CONTRATO, status: 'ACTIVE' });
  });
});
