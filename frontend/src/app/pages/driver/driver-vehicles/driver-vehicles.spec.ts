import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { describe, it, expect, afterEach, vi } from 'vitest';
import { PopUpService } from '../../../shared/pop-up';
import { DriverVehicles, formatarCrlv } from './driver-vehicles';
import { DriverPix } from '../driver-pix/driver-pix';

const VAN = {
  id: 'v-1',
  plate: 'ABC1D23',
  renavam: null,
  chassis: null,
  model: 'SPRINTER',
  year: 2022,
  manufactureYear: 2021,
  color: 'PRATA',
  fuel: null,
  category: null,
  capacity: 15,
  licensingYear: 2026,
  status: 'ACTIVE',
  linkedSince: '2026-01-10',
};

describe('Motorista preparado (vans e chave Pix)', () => {
  let http: HttpTestingController;
  let popUp: { show: ReturnType<typeof vi.fn>; showError: ReturnType<typeof vi.fn> };

  function configurar() {
    popUp = { show: vi.fn(), showError: vi.fn() };
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: PopUpService, useValue: popUp },
      ],
    });
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

  describe('Minhas vans', () => {
    function criar(vans: object[]) {
      configurar();
      const fixture = TestBed.createComponent(DriverVehicles);
      fixture.detectChanges();
      http.expectOne('/api/vehicle/my-vehicles').flush(vans);
      fixture.detectChanges();
      return fixture;
    }

    it('formata o texto do CRLV', () => {
      configurar();
      expect(formatarCrlv('SPRINTER 415 CDI')).toBe('Sprinter 415 Cdi');
      expect(formatarCrlv(null)).toBe('');
    });

    it('lista as vans como no Figma', () => {
      const texto: string = criar([VAN]).nativeElement.textContent;
      expect(texto).toContain('ABC1D23');
      expect(texto).toContain('Ativa');
      expect(texto).toContain('Sprinter · 2022 · Prata');
      expect(texto).toContain('15 lugares · Licenciamento 2026 · Em uso desde 10/01/2026');
      expect(texto).toContain('Parar de usar');
    });

    it('sem vans mostra o vazio', () => {
      const texto: string = criar([]).nativeElement.textContent;
      expect(texto).toContain('Você ainda não tem vans');
      expect(texto).toContain('Adicionar van');
    });

    it('adicionar: só aceita PDF e manda multipart documentPdf', () => {
      const fixture = criar([]);
      const tela = fixture.componentInstance as any;
      tela.abrirAdicionar();

      const imagem = new File(['x'], 'crlv.png', { type: 'image/png' });
      tela.selecionarCrlv({ target: { files: [imagem], value: '' } });
      expect(popUp.show).toHaveBeenCalledWith('Envie o CRLV digital em PDF', 'error', 'Dados inválidos');

      const pdf = new File(['%PDF'], 'crlv.pdf', { type: 'application/pdf' });
      tela.selecionarCrlv({ target: { files: [pdf], value: '' } });
      tela.enviar();
      tela.enviar();

      const req = http.expectOne('/api/vehicle/add-vehicle');
      expect(req.request.method).toBe('POST');
      expect((req.request.body as FormData).get('documentPdf')).toBe(pdf);
      req.flush(VAN);
      fixture.detectChanges();

      expect(tela.adicionando()).toBe(false);
      expect(fixture.nativeElement.textContent).toContain('ABC1D23');
    });

    it('422 deixa a tela aberta (o popup global mostra o texto do backend)', () => {
      const fixture = criar([]);
      const tela = fixture.componentInstance as any;
      tela.abrirAdicionar();
      tela.selecionarCrlv({ target: { files: [new File(['%PDF'], 'c.pdf', { type: 'application/pdf' })], value: '' } });
      tela.enviar();
      http
        .expectOne('/api/vehicle/add-vehicle')
        .flush({ message: 'O licenciamento da van está vencido' }, { status: 422, statusText: 'Unprocessable' });
      expect(tela.adicionando()).toBe(true);
      expect(tela.enviando()).toBe(false);
    });

    it('parar de usar confirma e manda PUT unlink', () => {
      const fixture = criar([VAN]);
      const tela = fixture.componentInstance as any;
      tela.confirmarParar(VAN);
      fixture.detectChanges();
      expect(fixture.nativeElement.textContent).toContain('A van ABC1D23 sai da sua lista de vans.');

      tela.pararDeUsar();
      const req = http.expectOne('/api/vehicle/v-1/unlink');
      expect(req.request.method).toBe('PUT');
      req.flush(null, { status: 204, statusText: 'No Content' });
      fixture.detectChanges();
      expect(fixture.nativeElement.textContent).toContain('Você ainda não tem vans');
    });
  });

  describe('Chave Pix', () => {
    function criar(chave: object) {
      configurar();
      const fixture = TestBed.createComponent(DriverPix);
      fixture.detectChanges();
      http.expectOne('/api/driver/pix-key').flush(chave);
      fixture.detectChanges();
      return fixture;
    }

    it('sem chave abre o formulário e explica na tela (sem pop-up por cima)', () => {
      const fixture = criar({ type: null, key: null });
      expect(popUp.show).not.toHaveBeenCalled();
      const texto: string = fixture.nativeElement.textContent;
      expect(texto).toContain('O app não recebe dinheiro');
      expect(texto).toContain('Tipo de chave');
      expect(texto).toContain('Aleatória');
      expect(texto).toContain('Salvar chave');
    });

    it('salva com PUT e mostra a chave que voltou (normalizada)', () => {
      const fixture = criar({ type: null, key: null });
      const tela = fixture.componentInstance as any;
      tela.escolherTipo('EMAIL');
      tela.valor = '  Gustavo@Email.com ';
      tela.salvar();

      const req = http.expectOne('/api/driver/pix-key');
      expect(req.request.method).toBe('PUT');
      expect(req.request.body).toEqual({ type: 'EMAIL', key: 'Gustavo@Email.com' });
      req.flush({ type: 'EMAIL', key: 'gustavo@email.com' });
      fixture.detectChanges();

      const texto: string = fixture.nativeElement.textContent;
      expect(texto).toContain('E-mail');
      expect(texto).toContain('gustavo@email.com');
      expect(texto).toContain('Trocar chave');
    });

    it('400 mantém o formulário (o popup global mostra a mensagem)', () => {
      const fixture = criar({ type: null, key: null });
      const tela = fixture.componentInstance as any;
      tela.valor = '123';
      tela.salvar();
      http
        .expectOne('/api/driver/pix-key')
        .flush({ message: 'Chave Pix inválida para o tipo CPF' }, { status: 400, statusText: 'Bad Request' });
      expect(tela.editando()).toBe(true);
    });

    it('com chave: remover confirma e manda DELETE', () => {
      const fixture = criar({ type: 'EMAIL', key: 'gustavo@email.com' });
      const tela = fixture.componentInstance as any;
      expect(fixture.nativeElement.textContent).toContain('Os responsáveis veem esta chave nas parcelas pendentes e atrasadas.');

      tela.abrirRemover();
      fixture.detectChanges();
      expect(fixture.nativeElement.textContent).toContain('Remover a chave Pix?');

      tela.remover();
      const req = http.expectOne('/api/driver/pix-key');
      expect(req.request.method).toBe('DELETE');
      req.flush(null, { status: 204, statusText: 'No Content' });
      fixture.detectChanges();
      expect(fixture.nativeElement.textContent).toContain('Salvar chave');
    });
  });
});
