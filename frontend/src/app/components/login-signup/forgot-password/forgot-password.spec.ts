import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { describe, it, expect, beforeEach, afterEach } from 'vitest';

import { ForgotPassword } from './forgot-password';

const API = 'http://localhost:9090/api';

describe('ForgotPassword', () => {
  let component: ForgotPassword;
  let fixture: ComponentFixture<ForgotPassword>;
  let http: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ForgotPassword],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();

    fixture = TestBed.createComponent(ForgotPassword);
    component = fixture.componentInstance;
    http = TestBed.inject(HttpTestingController);
    await fixture.whenStable();
  });

  afterEach(() => http.verify());

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('pede o código pelo e-mail (rota pública) e troca a senha com ele', () => {
    let voltou = false;
    component.voltarEvent.subscribe(() => (voltou = true));

    component.enteredEmail = ' ana@example.com ';
    component.enviarEmail();
    const envio = http.expectOne(`${API}/auth/forgot-password/send-code?email=ana%40example.com`);
    expect(envio.request.method).toBe('POST');
    envio.flush('ok');
    expect(component.etapa).toBe(2);

    component.enviarCodigo('123456');
    expect(component.etapa).toBe(3);

    component.enteredNewPass = 'Nova@1234';
    component.enteredConfirmPass = 'Nova@1234';
    component.novaSenha();

    const troca = http.expectOne(`${API}/auth/forgot-password`);
    expect(troca.request.method).toBe('POST');
    expect(troca.request.body).toEqual({
      email: 'ana@example.com',
      code: '123456',
      password: 'Nova@1234',
      confirmPassword: 'Nova@1234',
    });
    troca.flush('Senha alterada com sucesso!');
    expect(voltou).toBe(true);
  });

  it('não chama o backend com senha fraca ou diferente da confirmação', () => {
    component.enteredEmail = 'ana@example.com';
    component.enteredCode = '123456';
    component.etapa = 3;

    component.enteredNewPass = 'fraca';
    component.enteredConfirmPass = 'fraca';
    component.novaSenha();
    expect(component.passFraca).toBe(true);

    component.enteredNewPass = 'Nova@1234';
    component.enteredConfirmPass = 'Outra@1234';
    component.novaSenha();
    expect(component.passwordsDontMatch).toBe(true);

    http.expectNone(`${API}/auth/forgot-password`);
  });
});
