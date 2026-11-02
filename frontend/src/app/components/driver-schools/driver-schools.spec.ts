import { TestBed } from '@angular/core/testing';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Router } from '@angular/router';
import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { authInterceptor } from '../../interceptor/auth.interceptor';
import { PopUpService } from '../../shared/pop-up';
import { DriverSchools } from './driver-schools';

const API = 'http://localhost:9090/api';

function fakeToken(payload: object) {
  const base64 = (obj: object) => btoa(JSON.stringify(obj)).replace(/=+$/, '');
  return `${base64({ alg: 'HS256' })}.${base64(payload)}.assinatura`;
}

describe('DriverSchools (escolas no perfil do motorista)', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    localStorage.setItem('token', fakeToken({ sub: 'driver-1', roles: ['ROLE_DRIVER'] }));
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
        { provide: Router, useValue: { navigate: vi.fn() } },
        { provide: PopUpService, useValue: { show: vi.fn(), showError: vi.fn() } },
      ],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
    localStorage.clear();
    TestBed.resetTestingModule();
  });

  function abrir() {
    const fixture = TestBed.createComponent(DriverSchools);
    fixture.detectChanges();
    http.expectOne(`${API}/school/my-schools`).flush([
      {
        id: 'escola-1',
        name: 'Fatec Ipiranga',
        phone: null,
        email: null,
        address: { street: 'Rua A', zipCode: '01000000', city: 'São Paulo', neighborhood: 'Ipiranga', number: 10, state: 'SP' },
      },
    ]);
    fixture.detectChanges();
    return fixture;
  }

  it('lista as escolas do motorista', () => {
    const fixture = abrir();
    expect(fixture.nativeElement.textContent).toContain('Fatec Ipiranga');
    expect(fixture.nativeElement.textContent).toContain('Rua A, 10');
  });

  it('valida e envia no formato do SchoolRequestDTO, depois recarrega a lista', () => {
    const fixture = abrir();
    const comp = fixture.componentInstance as any;

    comp.abrirFormulario();
    comp.salvar();
    expect(comp.erro()).toContain('nome');
    http.expectNone(`${API}/school/add-school`);

    comp.form = {
      name: 'Escola Nova',
      phone: '(11) 98888-7777',
      email: 'escola@exemplo.com',
      zipCode: '01310-100',
      street: 'Av. Paulista',
      number: '1000',
      neighborhood: 'Bela Vista',
      city: 'São Paulo',
      state: 'sp',
    };
    comp.salvar();

    const req = http.expectOne(`${API}/school/add-school`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({
      name: 'Escola Nova',
      phone: '11988887777',
      email: 'escola@exemplo.com',
      addressRequestDTO: {
        street: 'Av. Paulista',
        zipCode: '01310100',
        city: 'São Paulo',
        neighborhood: 'Bela Vista',
        number: 1000,
        state: 'SP',
      },
    });
    req.flush({});

    http.expectOne(`${API}/school/my-schools`).flush([]);
    expect(comp.formularioAberto()).toBe(false);
  });

  it('409 avisa que o motorista já atende a escola', () => {
    const fixture = abrir();
    const comp = fixture.componentInstance as any;
    comp.abrirFormulario();
    comp.form = { ...comp.form, name: 'Fatec Ipiranga', zipCode: '01000000', street: 'Rua A', city: 'São Paulo' };
    comp.salvar();

    http.expectOne(`${API}/school/add-school`).flush(null, { status: 409, statusText: 'Conflict' });
    expect(comp.erro()).toContain('já atende');
  });
});
