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

const API = 'http://localhost:9090/api';

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
    });
  });
});
