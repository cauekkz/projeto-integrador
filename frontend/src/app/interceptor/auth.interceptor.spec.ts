import { TestBed } from '@angular/core/testing';
import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Router } from '@angular/router';
import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { authInterceptor } from './auth.interceptor';
import { PopUpService } from '../shared/pop-up';

const API = 'http://localhost:9090/api';

// jwt fake so com payload (assinatura nao importa pro jwt-decode)
function fakeToken(payload: object) {
  const base64 = (obj: object) => btoa(JSON.stringify(obj)).replace(/=+$/, '');
  return `${base64({ alg: 'HS256' })}.${base64(payload)}.assinatura`;
}

const agoraEmSegundos = () => Math.floor(Date.now() / 1000);
const tokenValido = (nome = 'valido') => fakeToken({ sub: 'user-uuid', roles: ['ROLE_DRIVER'], exp: agoraEmSegundos() + 900, nome });
const tokenVencido = () => fakeToken({ sub: 'user-uuid', roles: ['ROLE_DRIVER'], exp: agoraEmSegundos() - 60 });

describe('authInterceptor (token de 15 min + refreshToken)', () => {
  let http: HttpClient;
  let httpMock: HttpTestingController;
  let router: { navigate: ReturnType<typeof vi.fn> };
  let popUp: { show: ReturnType<typeof vi.fn>; showError: ReturnType<typeof vi.fn> };

  beforeEach(() => {
    router = { navigate: vi.fn() };
    popUp = { show: vi.fn(), showError: vi.fn() };
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
        { provide: Router, useValue: router },
        { provide: PopUpService, useValue: popUp },
      ],
    });
    http = TestBed.inject(HttpClient);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
    localStorage.clear();
    TestBed.resetTestingModule();
  });

  it('manda o token salvo no Authorization', () => {
    const token = tokenValido();
    localStorage.setItem('token', token);

    http.get(`${API}/users/me`).subscribe();

    expect(httpMock.expectOne(`${API}/users/me`).request.headers.get('Authorization')).toBe(`Bearer ${token}`);
  });

  it('com o token vencido, renova antes e manda a chamada com o token novo', () => {
    localStorage.setItem('token', tokenVencido());
    localStorage.setItem('refreshToken', 'refresh-1');
    const novo = tokenValido('novo');

    http.get(`${API}/users/me`).subscribe();

    const refresh = httpMock.expectOne(`${API}/auth/refresh`);
    expect(refresh.request.body).toEqual({ refreshToken: 'refresh-1' });
    refresh.flush({ token: novo, expiresIn: 900000, refreshToken: 'refresh-2', refreshExpiresIn: 1 });

    expect(httpMock.expectOne(`${API}/users/me`).request.headers.get('Authorization')).toBe(`Bearer ${novo}`);
    expect(localStorage.getItem('token')).toBe(novo);
    expect(localStorage.getItem('refreshToken')).toBe('refresh-2');
  });

  it('deu 401: renova uma vez e repete a chamada, sem popup', () => {
    localStorage.setItem('token', tokenValido());
    localStorage.setItem('refreshToken', 'refresh-1');
    const novo = tokenValido('novo');
    const resposta = vi.fn();

    http.get(`${API}/chats`).subscribe(resposta);

    httpMock.expectOne(`${API}/chats`).flush(null, { status: 401, statusText: 'Unauthorized' });
    httpMock
      .expectOne(`${API}/auth/refresh`)
      .flush({ token: novo, expiresIn: 900000, refreshToken: 'refresh-2', refreshExpiresIn: 1 });
    const repetida = httpMock.expectOne(`${API}/chats`);
    expect(repetida.request.headers.get('Authorization')).toBe(`Bearer ${novo}`);
    repetida.flush({ content: [] });

    expect(resposta).toHaveBeenCalledWith({ content: [] });
    expect(popUp.showError).not.toHaveBeenCalled();
  });

  it('várias chamadas com token vencido fazem um refresh só', () => {
    localStorage.setItem('token', tokenVencido());
    localStorage.setItem('refreshToken', 'refresh-1');
    const novo = tokenValido('novo');

    http.get(`${API}/users/me`).subscribe();
    http.get(`${API}/chats`).subscribe();

    httpMock
      .expectOne(`${API}/auth/refresh`)
      .flush({ token: novo, expiresIn: 900000, refreshToken: 'refresh-2', refreshExpiresIn: 1 });
    httpMock.expectOne(`${API}/users/me`).flush({});
    httpMock.expectOne(`${API}/chats`).flush({});
  });

  it('refresh recusado: apaga os tokens e volta pro inicio', () => {
    localStorage.setItem('token', tokenVencido());
    localStorage.setItem('refreshToken', 'refresh-velho');
    const erro = vi.fn();

    http.get(`${API}/users/me`).subscribe({ error: erro });

    httpMock.expectOne(`${API}/auth/refresh`).flush(null, { status: 401, statusText: 'Unauthorized' });

    expect(erro).toHaveBeenCalled();
    expect(localStorage.getItem('token')).toBeNull();
    expect(localStorage.getItem('refreshToken')).toBeNull();
    expect(router.navigate).toHaveBeenCalledWith(['/']);
    expect(popUp.show).toHaveBeenCalledTimes(1);
  });

  it('401 do login (senha errada) não tenta renovar e mostra o erro', () => {
    localStorage.setItem('refreshToken', 'refresh-1');

    http.post(`${API}/auth/login`, {}).subscribe({ error: () => {} });

    const login = httpMock.expectOne(`${API}/auth/login`);
    expect(login.request.headers.has('Authorization')).toBe(false);
    login.flush({ message: 'Credenciais inválidas.' }, { status: 401, statusText: 'Unauthorized' });

    httpMock.expectNone(`${API}/auth/refresh`);
    expect(popUp.showError).toHaveBeenCalled();
    expect(router.navigate).not.toHaveBeenCalled();
  });
});
