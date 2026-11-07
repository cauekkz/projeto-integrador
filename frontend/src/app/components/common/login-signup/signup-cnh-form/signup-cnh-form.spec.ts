import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';

import { SignupCnhForm } from './signup-cnh-form';
import { RegisterService } from '../../../../services/register.service';

describe('SignupCnhForm', () => {
  let component: SignupCnhForm;
  let fixture: ComponentFixture<SignupCnhForm>;
  let http: HttpTestingController;
  let pronto: boolean;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [SignupCnhForm],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();

    fixture = TestBed.createComponent(SignupCnhForm);
    component = fixture.componentInstance;
    http = TestBed.inject(HttpTestingController);
    pronto = false;
    component.prontoEvent.subscribe(() => (pronto = true));
    TestBed.inject(RegisterService).selectedFile = new File(['%PDF'], 'cnh.pdf', { type: 'application/pdf' });
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('CNH valida segue pro formulario', () => {
    component.pronto();

    http.expectOne('/api/driver/auth/verify-CNH').flush(null);

    expect(pronto).toBe(true);
  });

  it('CNH recusada fica na tela', () => {
    component.pronto();

    http
      .expectOne('/api/driver/auth/verify-CNH')
      .flush({ message: 'A CNH não passou na auditoria digital.' }, { status: 422, statusText: 'Unprocessable' });

    expect(pronto).toBe(false);
  });
});
