import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { describe, it, expect, beforeEach, afterEach } from 'vitest';

import { SignupDriver } from './signup-driver';
import { RegisterService } from '../../../../services/register.service';

describe('SignupDriver', () => {
  let component: SignupDriver;
  let fixture: ComponentFixture<SignupDriver>;
  let httpMock: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [SignupDriver],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();

    fixture = TestBed.createComponent(SignupDriver);
    component = fixture.componentInstance;
    httpMock = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
    httpMock.expectOne('/api/terms').flush({ version: '3', content: 'Termos' });
    await fixture.whenStable();
  });

  afterEach(() => httpMock.verify());

  function preencher() {
    component.enteredEmail = 'gustavo@email.com';
    component.enteredPhone = '(11) 99999-9999';
    component.enteredPass = 'Senha@123';
    component.enteredConfirmPass = 'Senha@123';
    TestBed.inject(RegisterService).selectedFile = new File(['%PDF'], 'cnh.pdf', { type: 'application/pdf' });
  }

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('não tem campo de nome (o nome vem da CNH)', () => {
    expect(fixture.nativeElement.querySelector('input[name="name"]')).toBeNull();
  });

  it('sem aceitar os termos não envia', () => {
    preencher();
    component.signup();
    httpMock.expectNone('/api/driver/auth/signup');
  });

  it('manda a versão dos termos e não manda nome', () => {
    preencher();
    component.versaoTermos = '3';
    component.signup();

    const req = httpMock.expectOne('/api/driver/auth/signup');
    const corpo = req.request.body as FormData;
    expect(corpo.get('acceptedTermsVersion')).toBe('3');
    expect(corpo.has('name')).toBe(false);
    req.flush('Motorista criado com sucesso');
  });
});
