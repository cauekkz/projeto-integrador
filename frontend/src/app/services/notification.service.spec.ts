import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { of } from 'rxjs';
import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { NotificationService } from './notification.service';
import { AuthService } from './auth.service';
import { ChatWebSocketService } from './chat.service';
import { HttpClient } from '@angular/common/http';

// WebSocket falso: guarda as instancias pra o teste fechar com o codigo que quiser
class FakeWebSocket {
  static abertos: FakeWebSocket[] = [];
  onmessage?: (e: MessageEvent) => void;
  onclose?: (e: CloseEvent) => void;
  constructor(public url: string) {
    FakeWebSocket.abertos.push(this);
  }
  close() {}
  fechar(code: number) {
    this.onclose?.({ code } as CloseEvent);
  }
}

describe('NotificationService: reconexão do WebSocket', () => {
  let service: NotificationService;

  beforeEach(() => {
    vi.useFakeTimers();
    FakeWebSocket.abertos = [];
    vi.stubGlobal('WebSocket', FakeWebSocket);

    TestBed.configureTestingModule({
      providers: [
        NotificationService,
        { provide: Router, useValue: { navigate: vi.fn() } },
        { provide: HttpClient, useValue: { get: () => of({ count: 0 }), put: () => of(null) } },
        { provide: ChatWebSocketService, useValue: { getChats: () => of({ content: [] }) } },
        {
          provide: AuthService,
          useValue: {
            getToken: () => 'token',
            getValidToken: () => of('token'),
            getUserIdFromToken: () => null,
          },
        },
      ],
    });
    service = TestBed.inject(NotificationService);
    service.iniciar();
  });

  afterEach(() => {
    vi.useRealTimers();
    vi.unstubAllGlobals();
    TestBed.resetTestingModule();
  });

  it('reconecta depois de uma queda comum (backend reiniciou, rede)', () => {
    expect(FakeWebSocket.abertos).toHaveLength(1);
    FakeWebSocket.abertos[0].fechar(1006);
    vi.advanceTimersByTime(5000);
    expect(FakeWebSocket.abertos).toHaveLength(2);
  });

  it.each([1003, 1008])('não reconecta quando o backend fecha com %i', (code) => {
    FakeWebSocket.abertos[0].fechar(code);
    vi.advanceTimersByTime(60000);
    expect(FakeWebSocket.abertos).toHaveLength(1);
    expect(service.naoLidas()).toBe(0);
  });
});
