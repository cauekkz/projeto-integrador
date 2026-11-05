import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ActivatedRoute, Router, convertToParamMap } from '@angular/router';
import { describe, it, expect, afterEach, vi } from 'vitest';
import { PopUpService } from '../../shared/pop-up';
import { Payments } from './payments';

function fakeToken(payload: object) {
  const base64 = (obj: object) => btoa(JSON.stringify(obj)).replace(/=+$/, '');
  return `${base64({ alg: 'HS256' })}.${base64(payload)}.assinatura`;
}

const PARCELA = {
  id: 'p-1',
  contractId: 'c-1',
  studentId: 'aluno-1',
  studentName: 'Lucas Pereira',
  driverId: 'driver-1',
  driverName: 'Gustavo Gómez',
  responsibleId: 'resp-1',
  responsibleName: 'Andreas Pereira',
  value: 350,
  dueDate: '2026-10-10',
  status: 'OVERDUE',
  paymentMethod: null,
  paidAt: null,
  driverPixKeyType: 'EMAIL',
  driverPixKey: 'gustavo@email.com',
};

const pagina = (content: object[]) => ({ content, last: true, totalPages: 1, totalElements: content.length });

describe('Payments (parcelas)', () => {
  let http: HttpTestingController;
  let popUp: { show: ReturnType<typeof vi.fn>; showError: ReturnType<typeof vi.fn> };
  let router: { navigate: ReturnType<typeof vi.fn>; url: string; events: any };

  function criar(motorista: boolean, parcelas: object[], contractId: string | null = null) {
    localStorage.setItem('token', fakeToken({ sub: 'x', roles: [motorista ? 'ROLE_DRIVER' : 'ROLE_RESPONSIBLE'] }));
    popUp = { show: vi.fn(), showError: vi.fn() };
    router = { navigate: vi.fn(), url: '/payments', events: { subscribe: () => ({ unsubscribe() {} }), pipe: () => ({ subscribe: () => ({ unsubscribe() {} }) }) } };
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: Router, useValue: router },
        { provide: PopUpService, useValue: popUp },
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { queryParamMap: convertToParamMap(contractId ? { contractId } : {}) } },
        },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    const fixture = TestBed.createComponent(Payments);
    fixture.detectChanges();
    http.expectOne((r) => r.url === '/api/payments').flush(pagina(parcelas));
    fixture.detectChanges();
    return fixture;
  }

  // header/footer (usuario, notificacoes) e as opcoes do filtro (my-contracts)
  function responderResto() {
    for (const req of http.match(() => true)) {
      if (req.request.url.includes('unread-count')) req.flush({ count: 0 });
      else if (req.request.url.startsWith('/api/chats') || req.request.url.includes('my-contracts')) req.flush(pagina([]));
      else req.flush({});
    }
  }

  afterEach(() => {
    responderResto();
    http.verify();
    localStorage.clear();
    TestBed.resetTestingModule();
  });

  it('motorista: cartão com responsável, "Venceu em", Recebi e Dispensar', () => {
    const texto: string = criar(true, [PARCELA]).nativeElement.textContent;
    expect(texto).toContain('Atrasada');
    expect(texto).toContain('Responsável: Andreas Pereira');
    expect(texto).toContain('Venceu em 10/10');
    expect(texto).toContain('Recebi');
    expect(texto).toContain('Dispensar');
    expect(texto).not.toContain('Copiar chave Pix');
  });

  it('aba filtra no backend pelo status', () => {
    const fixture = criar(true, []);
    (fixture.componentInstance as any).trocarAba('PAID');
    const req = http.expectOne((r) => r.url === '/api/payments');
    expect(req.request.params.get('status')).toBe('PAID');
    req.flush(pagina([{ ...PARCELA, status: 'PAID', paymentMethod: 'PIX', paidAt: '2026-11-05T10:00:00' }]));
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Paga em 05/11 · Pix');
  });

  it('Recebi pergunta o método e manda PUT paid', () => {
    const fixture = criar(true, [PARCELA]);
    const tela = fixture.componentInstance as any;
    tela.abrirRecebi(PARCELA);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('a baixa não tem volta');

    tela.metodo.set('CASH');
    tela.confirmarRecebimento();
    tela.confirmarRecebimento();
    const req = http.expectOne('/api/payments/p-1/paid');
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual({ method: 'CASH' });
    req.flush({ ...PARCELA, status: 'PAID', paymentMethod: 'CASH', paidAt: '2026-11-05T10:00:00' });
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Paga em 05/11 · Dinheiro');
  });

  it('409 no Recebi fecha a folha e recarrega a lista', () => {
    const fixture = criar(true, [PARCELA]);
    const tela = fixture.componentInstance as any;
    tela.abrirRecebi(PARCELA);
    tela.confirmarRecebimento();
    http
      .expectOne('/api/payments/p-1/paid')
      .flush({ message: 'Essa parcela já foi paga ou cancelada' }, { status: 409, statusText: 'Conflict' });
    expect(tela.recebendo()).toBeNull();
    http.expectOne((r) => r.url === '/api/payments').flush(pagina([{ ...PARCELA, status: 'PAID' }]));
  });

  it('Dispensar confirma e manda PUT cancel', () => {
    const fixture = criar(true, [PARCELA]);
    const tela = fixture.componentInstance as any;
    tela.abrirDispensar(PARCELA);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('O responsável é avisado e isso não tem volta.');
    tela.confirmarDispensa();
    const req = http.expectOne('/api/payments/p-1/cancel');
    expect(req.request.method).toBe('PUT');
    req.flush({ ...PARCELA, status: 'CANCELED' });
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Dispensada');
  });

  it('responsável: chave Pix do motorista, copiar e sem baixa', async () => {
    const writeText = vi.fn().mockResolvedValue(undefined);
    Object.defineProperty(navigator, 'clipboard', { value: { writeText }, configurable: true });
    const fixture = criar(false, [PARCELA]);
    const texto: string = fixture.nativeElement.textContent;
    expect(texto).toContain('Motorista: Gustavo Gómez');
    expect(texto).toContain('Chave Pix · E-mail');
    expect(texto).toContain('gustavo@email.com');
    expect(texto).not.toContain('Recebi');

    await (fixture.componentInstance as any).copiarChave(PARCELA);
    expect(writeText).toHaveBeenCalledWith('gustavo@email.com');
    expect(popUp.show).toHaveBeenCalledWith(expect.stringContaining('Quem confirma o pagamento é o motorista'), 'success', 'Chave Pix copiada');
  });

  it('responsável sem chave Pix do motorista vê o aviso', () => {
    const texto: string = criar(false, [{ ...PARCELA, status: 'PENDING', driverPixKey: null, driverPixKeyType: null }])
      .nativeElement.textContent;
    expect(texto).toContain('O motorista ainda não cadastrou a chave Pix.');
    expect(texto).toContain('Vence em 10/10');
    expect(texto).toContain('Avisar no chat');
  });

  it('Avisar no chat abre a conversa com o motorista', () => {
    const fixture = criar(false, [PARCELA]);
    (fixture.componentInstance as any).avisarNoChat(PARCELA);
    http
      .expectOne('/api/chats?page=0&size=50')
      .flush({ content: [{ id: 'chat-7', otherUserId: 'driver-1' }], last: true, totalPages: 1 });
    expect(router.navigate).toHaveBeenCalledWith(['/chat-details', 'chat-7']);
  });

  it('?contractId= filtra pelo contrato, esconde as abas e avisa', () => {
    const fixture = criar(true, [PARCELA], 'c-1');
    expect(fixture.nativeElement.textContent).not.toContain('Pendentes');
    expect(popUp.show).toHaveBeenCalledWith(
      'Parcela vencida de contrato cancelado continua aberta e pode receber baixa.',
      'info',
      'Parcelas do contrato de Lucas Pereira',
    );
  });

  it('vazio do responsável explica quem vê as parcelas', () => {
    expect(criar(false, []).nativeElement.textContent).toContain('Só quem assinou o contrato vê as parcelas dele.');
  });
});
