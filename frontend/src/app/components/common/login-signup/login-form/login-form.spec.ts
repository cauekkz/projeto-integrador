import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { describe, it, expect, beforeEach, afterEach } from 'vitest';

import { LoginForm } from './login-form';

describe('LoginForm', () => {
  let component: LoginForm;
  let fixture: ComponentFixture<LoginForm>;
  let http: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [LoginForm],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();

    fixture = TestBed.createComponent(LoginForm);
    component = fixture.componentInstance;
    http = TestBed.inject(HttpTestingController);
    await fixture.whenStable();
  });

  afterEach(() => http.verify());

  function botao(texto: string) {
    return [...fixture.nativeElement.querySelectorAll('button')].find((b: HTMLButtonElement) => b.textContent?.includes(texto)) as HTMLButtonElement;
  }

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('"Entrar" manda o login uma vez só', async () => {
    fixture.componentRef.setInput('tipoUsuario', 'responsavel');
    fixture.detectChanges();
    component.enteredCPF = '355.231.801-14';
    component.enteredPass = 'Senha@123';
    botao('Entrar').click();
    const reqs = http.match('/api/auth/login');
    expect(reqs.length).toBe(1);
    reqs[0].flush({}, { status: 401, statusText: 'Unauthorized' });
  });

  it('"Esqueceu sua senha?" não envia o formulário de login', () => {
    fixture.componentRef.setInput('tipoUsuario', 'responsavel');
    fixture.detectChanges();
    botao('Esqueceu sua senha?').click();
    http.expectNone('/api/auth/login');
    expect(component.cpfInvalido).toBe(false);
    expect(component.mostrarForgotPassword).toBe(true);
  });
});
