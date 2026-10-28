import { TestBed } from '@angular/core/testing';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ActivatedRoute, ActivatedRouteSnapshot, Router, RouterStateSnapshot, UrlTree, convertToParamMap } from '@angular/router';
import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { authInterceptor } from '../../interceptor/auth.interceptor';
import { roleGuard } from '../../interceptor/route.interceptor';
import { PopUpService } from '../../shared/pop-up';
import { ChatWebSocketService } from '../../services/chat.service';
import { ChatDetails } from '../../components/chat/chat-details/chat-details';
import { routes } from '../../app.routes';
import { DriverContractPage } from './driver-contract-page';
import { ContractDraftService } from './contract-draft.service';
import { ContractAttachment } from '../../components/driver-contract/contract-attachment/contract-attachment';

const API = 'http://localhost:9090/api';

function fakeToken(payload: object) {
  const base64 = (obj: object) => btoa(JSON.stringify(obj)).replace(/=+$/, '');
  return `${base64({ alg: 'HS256' })}.${base64(payload)}.assinatura`;
}

// deixa o async/await do envio terminar depois do flush
const esperar = () => new Promise((resolve) => setTimeout(resolve));

describe('Criação de contrato pelo motorista', () => {
  let http: HttpTestingController;
  let router: { navigate: ReturnType<typeof vi.fn>; createUrlTree: ReturnType<typeof vi.fn>; getCurrentNavigation: () => null };
  let popUp: { show: ReturnType<typeof vi.fn>; showError: ReturnType<typeof vi.fn> };

  function setup(role: string, paramMap: Record<string, string> = { chatId: 'chat-1' }) {
    localStorage.setItem('token', fakeToken({ sub: 'driver-1', roles: [role] }));
    router = {
      navigate: vi.fn(),
      createUrlTree: vi.fn((commands: string[]) => ({ commands }) as unknown as UrlTree),
      getCurrentNavigation: () => null,
    };
    popUp = { show: vi.fn(), showError: vi.fn() };

    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
        { provide: Router, useValue: router },
        { provide: PopUpService, useValue: popUp },
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: convertToParamMap(paramMap), queryParamMap: convertToParamMap({}) } } },
      ],
    });
    http = TestBed.inject(HttpTestingController);
  }

  afterEach(() => {
    http.verify();
    localStorage.clear();
    TestBed.resetTestingModule();
  });

  // abre a pagina e responde as 3 cargas iniciais com dados da API
  function abrirPagina() {
    const fixture = TestBed.createComponent(DriverContractPage);
    fixture.detectChanges();

    http.expectOne(`${API}/chats?page=0&size=50`).flush({
      content: [{ id: 'chat-1', otherUserId: 'resp-1', otherUserName: 'Maria Souza', otherUserPhotoKey: null, createdAt: '' }],
      number: 0,
      totalPages: 1,
      last: true,
    });
    http.expectOne(`${API}/chats/chat-1/students`).flush([{ id: 'aluno-1', name: 'João Souza' }]);
    http.expectOne(`${API}/school/my-schools`).flush([
      { id: 'escola-1', name: 'Escola Central', phone: null, email: null, address: null },
      { id: 'escola-2', name: 'Escola Norte', phone: null, email: null, address: null },
    ]);
    fixture.detectChanges();

    const page = fixture.componentInstance as any;
    const draft = TestBed.inject(ContractDraftService);
    return { fixture, page, draft };
  }

  function preencherTudo(draft: ContractDraftService) {
    draft.studentId.set('aluno-1');
    draft.schoolId.set('escola-1');
    draft.periodicity.set('MONTHLY');
    draft.valorCentavos.set(35000);
    draft.dataInicio.set('01/02/2027');
    draft.dataFim.set('30/06/2027');
  }

  describe('ContractDraftService (validação)', () => {
    beforeEach(() => setup('ROLE_DRIVER'));

    it('exige dependente e escola que existam nas listas da API', () => {
      const draft = TestBed.inject(ContractDraftService);
      draft.alunos.set([{ id: 'aluno-1', name: 'João' }]);
      draft.studentId.set('id-inventado');
      expect(draft.errosAluno().aluno).toBeTruthy();
      expect(draft.errosAluno().escola).toBeTruthy();
    });

    it('valida valor, datas inexistentes e término antes do início', () => {
      const draft = TestBed.inject(ContractDraftService);
      draft.valorCentavos.set(0);
      draft.dataInicio.set('31/02/2027');
      draft.dataFim.set('01/01/2027');
      let erros = draft.errosContrato();
      expect(erros.valor).toBeTruthy();
      expect(erros.dataInicio).toBeTruthy();

      draft.valorCentavos.set(100);
      draft.dataInicio.set('01/02/2027');
      erros = draft.errosContrato();
      expect(erros.valor).toBeUndefined();
      expect(erros.dataFim).toContain('anterior');

      draft.dataFim.set('01/02/2027');
      expect(draft.errosContrato()).toEqual({});
    });
  });

  describe('DriverContractPage', () => {
    beforeEach(() => setup('ROLE_DRIVER'));

    it('carrega responsável do chat, dependentes e escolas reais', () => {
      const { draft, fixture } = abrirPagina();
      expect(draft.responsavel()).toEqual({ id: 'resp-1', nome: 'Maria Souza', fotoUrl: null });
      expect(fixture.nativeElement.textContent).toContain('Maria Souza');
      expect(fixture.nativeElement.textContent).toContain('Dados do aluno');
    });

    it('não avança sem preencher e preserva os dados ao voltar', () => {
      const { page, draft } = abrirPagina();

      page.avancar();
      expect(draft.etapa()).toBe('aluno');
      expect(draft.mostrarErros()).toBe(true);

      draft.studentId.set('aluno-1');
      draft.schoolId.set('escola-1');
      page.avancar();
      expect(draft.etapa()).toBe('contrato');

      page.avancar();
      expect(draft.etapa()).toBe('contrato');

      page.voltar();
      expect(draft.etapa()).toBe('aluno');
      expect(draft.studentId()).toBe('aluno-1');
    });

    it('envia a proposta sem PDF no formato do ContractProposalRequestDTO e volta pro chat', async () => {
      const { page, draft } = abrirPagina();
      preencherTudo(draft);
      draft.etapa.set('midia');

      page.avancar();
      // clique repetido durante o envio nao gera outro POST
      page.avancar();

      const req = http.expectOne(`${API}/chats/chat-1/contract`);
      expect(req.request.method).toBe('POST');
      expect(req.request.headers.get('Authorization')).toMatch(/^Bearer /);
      expect(req.request.body).toEqual({
        responsibleId: 'resp-1',
        studentId: 'aluno-1',
        schoolId: 'escola-1',
        contract: { periodicity: 'MONTHLY', value: 350, startDate: '2027-02-01', endDate: '2027-06-30' },
        document: { url: null, type: 'TRANSPORT_CONTRACT' },
      });
      req.flush({ userDriverContractId: 'udc-1', contract: { contractId: 'c-1', status: 'PENDING' } }, { status: 201, statusText: 'Created' });
      await esperar();

      expect(popUp.show).toHaveBeenCalledWith(expect.stringContaining('Maria Souza'), 'success', 'Contrato enviado');
      expect(router.navigate).toHaveBeenCalledWith(['/chat-details', 'chat-1'], expect.anything());
      expect(draft.chatId()).toBe('');
    });

    it('com PDF: sobe antes, manda o fileKey e não sobe de novo ao tentar outra vez depois de um erro', async () => {
      const upload = vi.spyOn(TestBed.inject(ChatWebSocketService), 'uploadAttachment').mockResolvedValue('arquivo-123.pdf');
      const { page, draft } = abrirPagina();
      preencherTudo(draft);
      draft.pdf.set(new File(['%PDF'], 'contrato.pdf', { type: 'application/pdf' }));
      draft.etapa.set('midia');

      page.avancar();
      await esperar();
      let req = http.expectOne(`${API}/chats/chat-1/contract`);
      expect(req.request.body.document).toEqual({ url: 'arquivo-123.pdf', type: 'TRANSPORT_CONTRACT' });
      req.flush({ message: 'conflito' }, { status: 409, statusText: 'Conflict' });
      await esperar();

      expect(page.erroEnvio()).toContain('pendente ou ativo');
      expect(draft.studentId()).toBe('aluno-1');
      expect(router.navigate).not.toHaveBeenCalled();
      // erro tratado na tela, sem popup global duplicado
      expect(popUp.showError).not.toHaveBeenCalled();

      page.avancar();
      await esperar();
      req = http.expectOne(`${API}/chats/chat-1/contract`);
      req.flush({ userDriverContractId: 'udc-1', contract: { contractId: 'c-1', status: 'PENDING' } });
      await esperar();

      expect(upload).toHaveBeenCalledTimes(1);
    });

    it('não cria o contrato se o upload do PDF falhar', async () => {
      vi.spyOn(TestBed.inject(ChatWebSocketService), 'uploadAttachment').mockRejectedValue(new Error('falhou'));
      const { page, draft } = abrirPagina();
      preencherTudo(draft);
      draft.pdf.set(new File(['%PDF'], 'contrato.pdf', { type: 'application/pdf' }));
      draft.etapa.set('midia');

      page.avancar();
      await esperar();

      http.expectNone(`${API}/chats/chat-1/contract`);
      expect(page.erroEnvio()).toContain('PDF');
    });
  });

  describe('ContractAttachment', () => {
    beforeEach(() => setup('ROLE_DRIVER'));

    it('aceita só PDF e permite remover', () => {
      const fixture = TestBed.createComponent(ContractAttachment);
      const comp = fixture.componentInstance as any;
      const draft = TestBed.inject(ContractDraftService);
      const evento = (file: File) => ({ target: { files: [file], value: 'x' } }) as unknown as Event;

      comp.selecionar(evento(new File(['x'], 'foto.png', { type: 'image/png' })));
      expect(draft.pdf()).toBeNull();
      expect(comp.erro()).toContain('PDF');

      const pdf = new File(['%PDF'], 'contrato.pdf', { type: 'application/pdf' });
      comp.selecionar(evento(pdf));
      expect(draft.pdf()).toBe(pdf);

      fixture.detectChanges();
      expect(fixture.nativeElement.textContent).toContain('contrato.pdf');

      comp.remover();
      expect(draft.pdf()).toBeNull();
    });
  });

  describe('Permissões', () => {
    it('a rota driver-contract só aceita ROLE_DRIVER', () => {
      const rota = routes.find((r) => r.path === 'driver-contract/:chatId');
      expect(rota?.data?.['rolesPermitted']).toEqual(['ROLE_DRIVER']);
      expect(rota?.canActivate).toContain(roleGuard);
    });

    it('responsável que digita a URL é mandado pra home dele', () => {
      setup('ROLE_RESPONSIBLE');
      const rota = { data: { rolesPermitted: ['ROLE_DRIVER'] } } as unknown as ActivatedRouteSnapshot;
      const resultado = TestBed.runInInjectionContext(() => roleGuard(rota, {} as RouterStateSnapshot));
      expect(resultado).not.toBe(true);
      expect(router.createUrlTree).toHaveBeenCalledWith(['/responsible-home']);
    });

    it('motorista vê "Criar contrato" no chat e é levado pro fluxo', () => {
      setup('ROLE_DRIVER', {});
      const fixture = TestBed.createComponent(ChatDetails);
      fixture.detectChanges();
      const comp = fixture.componentInstance;

      comp.showAttachmentMenu = true;
      fixture.changeDetectorRef.markForCheck();
      fixture.detectChanges();
      expect(fixture.nativeElement.textContent).toContain('Criar contrato');

      comp.chatId = 'chat-1';
      comp.criarContrato();
      expect(router.navigate).toHaveBeenCalledWith(['/driver-contract', 'chat-1']);
    });

    it('chat mostra a PROPOSAL como card e usa APPROVAL/REJECTION como decisão', () => {
      setup('ROLE_RESPONSIBLE', {});
      const fixture = TestBed.createComponent(ChatDetails);
      fixture.detectChanges();
      const comp = fixture.componentInstance as any;
      comp.chatId = 'chat-1';

      const base = { senderId: 'driver-1', attachmentUrl: null, attachmentType: null };
      comp.adicionarMensagens([
        { ...base, id: 'm1', content: 'Proposta de Contrato', sentAt: '2027-01-01T10:00:00', messageType: 'PROPOSAL',
          payload: '{"userDriverContractId": "udc-1", "contractId": "c-1"}' },
        { ...base, id: 'm2', content: 'Contrato Aceito', sentAt: '2027-01-01T11:00:00', messageType: 'APPROVAL',
          payload: '{"contractId": "c-1"}' },
      ]);
      fixture.detectChanges();

      expect(comp.decisoesContrato.get('c-1')).toBe('ACTIVE');
      expect(fixture.nativeElement.querySelector('app-contract-proposal-card')).not.toBeNull();
      expect(fixture.nativeElement.querySelector('.msg-sistema').textContent).toContain('Contrato Aceito');
      http.expectOne(`${API}/contract/c-1`).flush(null, { status: 500, statusText: 'erro' });
    });

    it('responsável não vê "Criar contrato" no chat', () => {
      setup('ROLE_RESPONSIBLE', {});
      const fixture = TestBed.createComponent(ChatDetails);
      fixture.detectChanges();
      const comp = fixture.componentInstance;

      comp.showAttachmentMenu = true;
      fixture.changeDetectorRef.markForCheck();
      fixture.detectChanges();
      expect(fixture.nativeElement.textContent).not.toContain('Criar contrato');
      expect(fixture.nativeElement.textContent).toContain('Enviar mídia');

      comp.chatId = 'chat-1';
      comp.criarContrato();
      expect(router.navigate).not.toHaveBeenCalled();
    });
  });
});
