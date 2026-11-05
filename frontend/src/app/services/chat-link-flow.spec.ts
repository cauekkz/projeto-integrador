import { TestBed } from '@angular/core/testing';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ActivatedRoute, Router, convertToParamMap } from '@angular/router';
import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { ChatWebSocketService } from './chat.service';
import { authInterceptor } from '../interceptor/auth.interceptor';
import { PopUpService } from '../shared/pop-up';
import { DriverLinkCode } from '../pages/driver-link-code/driver-link-code';
import { DriverCode } from '../components/driver-code/driver-code';
import { ChatDetails } from '../components/chat/chat-details/chat-details';

const API = '/api';

// jwt fake so com payload (assinatura nao importa pro jwt-decode)
function fakeToken(payload: object) {
  const base64 = (obj: object) => btoa(JSON.stringify(obj)).replace(/=+$/, '');
  return `${base64({ alg: 'HS256' })}.${base64(payload)}.assinatura`;
}

describe('Fluxo de código de conversa (motorista <-> responsável)', () => {
  let http: HttpTestingController;
  let router: { navigate: ReturnType<typeof vi.fn>; getCurrentNavigation: () => null };
  let popUp: { show: ReturnType<typeof vi.fn>; showError: ReturnType<typeof vi.fn> };

  function setup(role: string, extraProviders: any[] = []) {
    localStorage.setItem('token', fakeToken({ sub: '12345678901', roles: [role] }));
    router = { navigate: vi.fn(), getCurrentNavigation: () => null };
    popUp = { show: vi.fn(), showError: vi.fn() };

    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
        { provide: Router, useValue: router },
        { provide: PopUpService, useValue: popUp },
        ...extraProviders,
      ],
    });
    http = TestBed.inject(HttpTestingController);
  }

  afterEach(() => {
    http.verify();
    localStorage.clear();
    TestBed.resetTestingModule();
  });

  describe('ChatWebSocketService', () => {
    beforeEach(() => setup('ROLE_DRIVER'));

    it('consulta o código do motorista logado sem mandar id (o backend usa o jwt)', () => {
      TestBed.inject(ChatWebSocketService).getDriverLinkCode().subscribe();

      const req = http.expectOne(`${API}/driver/link-code`);
      expect(req.request.method).toBe('GET');
      expect(req.request.headers.get('Authorization')).toMatch(/^Bearer /);
      req.flush({ linkCode: 'ABC23XYZ9' });
    });

    it('resgata o código no endpoint existente de invite-code', () => {
      TestBed.inject(ChatWebSocketService).createChatMessage('ABC23XYZ9').subscribe();

      const req = http.expectOne(`${API}/invite-code/redeem`);
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual({ inviteCode: 'ABC23XYZ9' });
      req.flush('chat-uuid');
    });
  });

  describe('DriverLinkCode (tela do motorista)', () => {
    beforeEach(() => setup('ROLE_DRIVER'));

    it('mostra o código real vindo do backend', () => {
      const fixture = TestBed.createComponent(DriverLinkCode);
      fixture.detectChanges();
      expect(fixture.componentInstance.carregando()).toBe(true);

      http.expectOne(`${API}/driver/link-code`).flush({ linkCode: 'ABC23XYZ9' });
      fixture.detectChanges();

      expect(fixture.componentInstance.carregando()).toBe(false);
      expect(fixture.nativeElement.textContent).toContain('ABC-23X-YZ9');
    });

    it('mostra erro e não um código inventado quando o backend falha', () => {
      const fixture = TestBed.createComponent(DriverLinkCode);
      fixture.detectChanges();

      http.expectOne(`${API}/driver/link-code`).flush({ message: 'x' }, { status: 404, statusText: 'Not Found' });
      fixture.detectChanges();

      expect(fixture.componentInstance.codigo()).toBe('');
      expect(fixture.componentInstance.erro()).toContain('Não encontramos');
      // erro tratado na tela, sem popup global duplicado
      expect(popUp.showError).not.toHaveBeenCalled();
    });
  });

  describe('DriverCode (tela existente do responsável)', () => {
    beforeEach(() => setup('ROLE_RESPONSIBLE'));

    it('envia o código em maiúsculas, bloqueia envio duplo e guarda o chat criado', () => {
      const component = TestBed.createComponent(DriverCode).componentInstance;

      component.confirmar('abc23xyz9');
      component.confirmar('abc23xyz9');

      const req = http.expectOne(`${API}/invite-code/redeem`);
      expect(req.request.body).toEqual({ inviteCode: 'ABC23XYZ9' });
      expect(component.enviando()).toBe(true);
      req.flush('chat-uuid');

      expect(component.enviando()).toBe(false);
      expect(component.chatId()).toBe('chat-uuid');

      component.abrirConversa();
      expect(router.navigate).toHaveBeenCalledWith(['/chat-details', 'chat-uuid'], {
        queryParams: { from: 'responsible-home' },
      });
    });

    it('rejeita código inválido do backend sem considerar o motorista vinculado', () => {
      const component = TestBed.createComponent(DriverCode).componentInstance;

      component.confirmar('ZZZZZZZZZ');
      http
        .expectOne(`${API}/invite-code/redeem`)
        .flush({ message: 'Motorista não encontrado' }, { status: 500, statusText: 'Server Error' });

      expect(component.chatId()).toBe('');
      expect(component.erro()).toContain('Código inválido');
      expect(popUp.showError).not.toHaveBeenCalled();
    });

    it('não chama o backend com código em formato errado', () => {
      const component = TestBed.createComponent(DriverCode).componentInstance;

      component.confirmar('ABC-1');

      http.expectNone(`${API}/invite-code/redeem`);
      expect(component.erro()).toContain('9 letras ou números');
    });
  });

  describe('ChatDetails (conversa existente)', () => {
    beforeEach(() =>
      setup('ROLE_RESPONSIBLE', [
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: {
              paramMap: convertToParamMap({ id: 'chat-uuid' }),
              queryParamMap: convertToParamMap({}),
            },
          },
        },
      ]),
    );

    it('marca como "minha" pelo id que vem do /users/me', () => {
      const chatService = TestBed.inject(ChatWebSocketService);
      vi.spyOn(chatService, 'connect').mockImplementation(() => {});
      vi.spyOn(chatService, 'disconnect').mockImplementation(() => {});

      const fixture = TestBed.createComponent(ChatDetails);
      fixture.detectChanges();

      http.expectOne(`${API}/chats?page=0&size=50`).flush({
        content: [{ id: 'chat-uuid', otherUserId: 'driver-uuid', otherUserName: 'Gustavo', createdAt: '' }],
        number: 0,
        totalPages: 1,
        last: true,
      });
      http.expectOne(`${API}/users/me`).flush({ id: 'responsible-uuid', photoKey: null });
      http.expectOne(`${API}/chats/chat-uuid/messages?page=0&size=30`).flush({
        content: [
          { id: 'm1', senderId: 'driver-uuid', content: 'Oi', sentAt: '2026-10-26T10:00:00', attachmentUrl: null, attachmentType: null, payload: null },
          { id: 'm2', senderId: 'responsible-uuid', content: 'Olá', sentAt: '2026-10-26T10:01:00', attachmentUrl: null, attachmentType: null, payload: null },
        ],
        number: 0,
        totalPages: 1,
        last: true,
      });

      const component = fixture.componentInstance;
      expect(component.nomeContato).toBe('Gustavo');
      expect(component.mensagens.map((m) => [m.id, m.minha])).toEqual([
        ['m1', false],
        ['m2', true],
      ]);
      // abriu a conversa: marca as do outro como lidas
      http.expectOne({ method: 'PUT', url: `${API}/chats/chat-uuid/read` }).flush(null, { status: 204, statusText: 'No Content' });
    });

    // abre a conversa com uma mensagem minha ainda nao lida e outra ja lida
    function abrirConversa() {
      const chatService = TestBed.inject(ChatWebSocketService);
      vi.spyOn(chatService, 'connect').mockImplementation(() => {});
      vi.spyOn(chatService, 'disconnect').mockImplementation(() => {});

      const fixture = TestBed.createComponent(ChatDetails);
      fixture.detectChanges();
      http.expectOne(`${API}/chats?page=0&size=50`).flush({ content: [], number: 0, totalPages: 1, last: true });
      http.expectOne(`${API}/users/me`).flush({ id: 'responsible-uuid', photoKey: null });
      http.expectOne(`${API}/chats/chat-uuid/messages?page=0&size=30`).flush({
        content: [
          { id: 'm1', senderId: 'responsible-uuid', content: 'Oi', sentAt: '2026-10-26T10:00:00', attachmentUrl: null, attachmentType: null, payload: null, readAt: '2026-10-26T10:05:00' },
          { id: 'm2', senderId: 'responsible-uuid', content: 'Tudo bem?', sentAt: '2026-10-26T10:01:00', attachmentUrl: null, attachmentType: null, payload: null, readAt: null },
          { id: 'm3', senderId: 'driver-uuid', content: 'Tudo', sentAt: '2026-10-26T10:02:00', attachmentUrl: null, attachmentType: null, payload: null, readAt: null },
        ],
        number: 0,
        totalPages: 1,
        last: true,
      });
      http.expectOne({ method: 'PUT', url: `${API}/chats/chat-uuid/read` }).flush(null, { status: 204, statusText: 'No Content' });
      return { fixture, chatService, component: fixture.componentInstance };
    }

    it('✓✓ pelo readAt e só nas minhas mensagens', () => {
      const { component, fixture } = abrirConversa();
      expect(component.mensagens.map((m) => [m.id, m.lida])).toEqual([
        ['m1', true],
        ['m2', false],
        ['m3', false],
      ]);
      fixture.detectChanges();
      const el: HTMLElement = fixture.nativeElement;
      expect(el.querySelectorAll('.bi-check2-all').length).toBe(1);
      expect(el.querySelectorAll('.bi-check2').length).toBe(1);
    });

    it('evento read do outro marca todas as minhas como lidas', () => {
      const { component, chatService } = abrirConversa();
      (chatService as any).readSubject.next({ type: 'read', chatId: 'chat-uuid', userId: 'driver-uuid', readAt: '2026-10-26T10:10:00' });
      expect(component.mensagens.filter((m) => m.minha).every((m) => m.lida)).toBe(true);
    });

    it('evento read meu (de outra aba) não muda nada', () => {
      const { component, chatService } = abrirConversa();
      (chatService as any).readSubject.next({ type: 'read', chatId: 'chat-uuid', userId: 'responsible-uuid', readAt: '2026-10-26T10:10:00' });
      expect(component.mensagens.find((m) => m.id === 'm2')?.lida).toBe(false);
    });

    it('mensagem do outro com a conversa aberta já é marcada como lida', () => {
      const { chatService } = abrirConversa();
      (chatService as any).messageSubject.next({ id: 'm4', senderId: 'driver-uuid', content: 'Chego 7h', sentAt: '2026-10-26T10:03:00', attachmentUrl: null, attachmentType: null, messageType: 'REGULAR_CHAT', payload: null, readAt: null });
      http.expectOne({ method: 'PUT', url: `${API}/chats/chat-uuid/read` }).flush(null, { status: 204, statusText: 'No Content' });

      // a minha propria mensagem que volta pelo socket nao chama o read
      (chatService as any).messageSubject.next({ id: 'm5', senderId: 'responsible-uuid', content: 'Ok', sentAt: '2026-10-26T10:04:00', attachmentUrl: null, attachmentType: null, messageType: 'REGULAR_CHAT', payload: null, readAt: null });
      http.expectNone({ method: 'PUT', url: `${API}/chats/chat-uuid/read` });
    });
  });

  describe('ChatWebSocketService (socket da conversa)', () => {
    beforeEach(() => setup('ROLE_RESPONSIBLE'));

    it('separa o aviso de leitura das mensagens', () => {
      const sockets: any[] = [];
      vi.stubGlobal('WebSocket', class {
        static OPEN = 1;
        readyState = 0;
        onmessage: ((e: { data: string }) => void) | null = null;
        constructor() { sockets.push(this); }
        close() {}
      });
      localStorage.setItem('token', fakeToken({ sub: 'u', roles: ['ROLE_RESPONSIBLE'], exp: Math.floor(Date.now() / 1000) + 900 }));

      const service = TestBed.inject(ChatWebSocketService);
      const mensagens: unknown[] = [];
      const leituras: unknown[] = [];
      service.messages$.subscribe((m) => mensagens.push(m));
      service.reads$.subscribe((r) => leituras.push(r));
      service.connect('chat-uuid');

      sockets[0].onmessage({ data: JSON.stringify({ type: 'read', chatId: 'chat-uuid', userId: 'd', readAt: '2026-10-26T10:10:00' }) });
      sockets[0].onmessage({ data: JSON.stringify({ id: 'm1', senderId: 'd', content: 'Oi' }) });

      expect(leituras).toHaveLength(1);
      expect(mensagens).toHaveLength(1);
      vi.unstubAllGlobals();
    });
  });
});
