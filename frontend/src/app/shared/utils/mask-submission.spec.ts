import { describe, it, expect, vi } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { of } from 'rxjs';
import { AuthService } from '../../services/auth.service';
import { LoginForm } from '../../components/login-signup/login-form/login-form';
import { SignupResponsible } from '../../components/login-signup/signup-responsible/signup-responsible';
import { SignupDriver } from '../../components/login-signup/signup-driver/signup-driver';
import { Router } from '@angular/router';
import { PopUpService } from '../pop-up';
import { RegisterService } from '../../services/register.service';
import { AddStudent } from '../../components/student/add-student/add-student';
import { StudentService } from '../../services/student.service';

describe('Mask Submission & API Integration', () => {
  describe('LoginForm', () => {
    it('should submit unmasked CPF to AuthService while keeping formatted CPF in UI', () => {
      const authServiceMock = {
        login: vi.fn().mockReturnValue(of({ token: 'mock-token', expiresIn: 3600 })),
        getHomeRoute: vi.fn().mockReturnValue('/responsible-home'),
        logout: vi.fn(),
      };
      const routerMock = { navigate: vi.fn() };
      const popUpMock = { show: vi.fn() };

      TestBed.configureTestingModule({
        providers: [
          { provide: AuthService, useValue: authServiceMock },
          { provide: Router, useValue: routerMock },
          { provide: PopUpService, useValue: popUpMock },
        ],
      });

      const fixture = TestBed.createComponent(LoginForm);
      const component = fixture.componentInstance;

      // User types CPF with mask
      component.enteredCPF = '123.456.789-00';
      component.enteredPass = 'minhasenha123';

      component.login();

      // UI state must preserve mask
      expect(component.enteredCPF).toBe('123.456.789-00');

      // AuthService must receive unmasked CPF
      expect(authServiceMock.login).toHaveBeenCalledWith({
        cpf: '12345678900',
        passwordHash: 'minhasenha123',
      });
    });

    it('should preserve leading zeroes in CPF during login', () => {
      const authServiceMock = {
        login: vi.fn().mockReturnValue(of({ token: 'mock-token', expiresIn: 3600 })),
        getHomeRoute: vi.fn().mockReturnValue('/responsible-home'),
        logout: vi.fn(),
      };
      const routerMock = { navigate: vi.fn() };
      const popUpMock = { show: vi.fn() };

      TestBed.configureTestingModule({
        providers: [
          { provide: AuthService, useValue: authServiceMock },
          { provide: Router, useValue: routerMock },
          { provide: PopUpService, useValue: popUpMock },
        ],
      });

      const fixture = TestBed.createComponent(LoginForm);
      const component = fixture.componentInstance;

      component.enteredCPF = '012.345.678-90';
      component.enteredPass = 'senha123';

      component.login();

      expect(authServiceMock.login).toHaveBeenCalledWith({
        cpf: '01234567890',
        passwordHash: 'senha123',
      });
    });
  });

  describe('SignupResponsible', () => {
    it('should submit unmasked CPF and phone while preserving visual values and other fields', () => {
      const authServiceMock = {
        createUser: vi.fn().mockReturnValue(of({ id: 'resp-1' })),
      };
      const routerMock = { navigate: vi.fn() };
      const popUpMock = { show: vi.fn() };

      TestBed.configureTestingModule({
        providers: [
          { provide: AuthService, useValue: authServiceMock },
          { provide: Router, useValue: routerMock },
          { provide: PopUpService, useValue: popUpMock },
        ],
      });

      const fixture = TestBed.createComponent(SignupResponsible);
      const component = fixture.componentInstance;

      component.enteredName = 'Maria da Silva';
      component.enteredCpf = '123.456.789-00';
      component.enteredEmail = 'maria@example.com';
      component.enteredPhone = '(11) 98765-4321';
      component.enteredPass = 'senhaSegura1';
      component.enteredConfirmPass = 'senhaSegura1';

      component.signup();

      // Masked values preserved for display
      expect(component.enteredCpf).toBe('123.456.789-00');
      expect(component.enteredPhone).toBe('(11) 98765-4321');

      // Request payload sent with unmasked numbers
      expect(authServiceMock.createUser).toHaveBeenCalledWith({
        name: 'Maria da Silva',
        cpf: '12345678900',
        email: 'maria@example.com',
        phone: '11987654321',
        password: 'senhaSegura1',
        confirmPassword: 'senhaSegura1',
      });
    });
  });

  describe('SignupDriver', () => {
    it('should submit unmasked phone while preserving visual value in SignupDriver', () => {
      const authServiceMock = {
        createDriver: vi.fn().mockReturnValue(of('ok')),
      };
      const routerMock = { navigate: vi.fn() };
      const popUpMock = { show: vi.fn() };
      const registerMock = { selectedFile: new File([''], 'cnh.pdf') };

      TestBed.configureTestingModule({
        providers: [
          { provide: AuthService, useValue: authServiceMock },
          { provide: Router, useValue: routerMock },
          { provide: PopUpService, useValue: popUpMock },
          { provide: RegisterService, useValue: registerMock },
        ],
      });

      const fixture = TestBed.createComponent(SignupDriver);
      const component = fixture.componentInstance;

      component.enteredName = 'Carlos Santos';
      component.enteredEmail = 'carlos@example.com';
      component.enteredPhone = '(11) 99887-6655';
      component.enteredPass = 'driverPass123';
      component.enteredConfirmPass = 'driverPass123';

      component.signup();

      expect(component.enteredPhone).toBe('(11) 99887-6655');

      expect(authServiceMock.createDriver).toHaveBeenCalledWith({
        documentPdf: registerMock.selectedFile,
        name: 'Carlos Santos',
        email: 'carlos@example.com',
        password: 'driverPass123',
        confirmPassword: 'driverPass123',
        phone: '11998876655',
      });
    });
  });

  describe('AddStudent', () => {
    it('should format birthDate to ISO YYYY-MM-DD on submission while keeping DD/MM/YYYY in UI', () => {
      const studentServiceMock = {
        createStudent: vi.fn().mockReturnValue(of({ student: { id: 'stud-1' } })),
        generateStudentLink: vi.fn().mockReturnValue(of('CODE123')),
      };
      const routerMock = { navigate: vi.fn() };

      TestBed.configureTestingModule({
        providers: [
          { provide: StudentService, useValue: studentServiceMock },
          { provide: Router, useValue: routerMock },
        ],
      });

      const fixture = TestBed.createComponent(AddStudent);
      const component = fixture.componentInstance;

      component.enteredName = 'Lucas Silva';
      component.enteredBirthdate = '15/03/2012';
      component.enteredObservacoes = 'Alergia a amendoim';
      component.enteredRelationType = 'FILHO';

      component.pronto();

      expect(component.enteredBirthdate).toBe('15/03/2012');
      expect(studentServiceMock.createStudent).toHaveBeenCalledWith({
        name: 'Lucas Silva',
        notes: 'Alergia a amendoim',
        birthDate: '2012-03-15',
        relationType: 'FILHO',
      });
    });
  });

  describe('AuthService HTTP Payload Verification', () => {
    let service: AuthService;
    let httpMock: HttpTestingController;

    beforeEach(() => {
      TestBed.configureTestingModule({
        providers: [
          AuthService,
          provideHttpClient(),
          provideHttpClientTesting(),
        ],
      });

      service = TestBed.inject(AuthService);
      httpMock = TestBed.inject(HttpTestingController);
    });

    afterEach(() => {
      httpMock.verify();
    });

    it('should clean CPF mask before POST /auth/login request', () => {
      service.login({
        cpf: '123.456.789-00',
        passwordHash: 'pass123',
      }).subscribe();

      const req = httpMock.expectOne('http://localhost:9090/api/auth/login');
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual({
        cpf: '12345678900',
        passwordHash: 'pass123',
      });
      req.flush({ token: 'jwt', expiresIn: 3600 });
    });

    it('should clean CPF and phone masks before POST /responsible/auth/signup request', () => {
      service.createUser({
        name: 'João Souza',
        email: 'joao@example.com',
        cpf: '123.456.789-00',
        phone: '(11) 98765-4321',
        password: 'pass',
        confirmPassword: 'pass',
      }).subscribe();

      const req = httpMock.expectOne('http://localhost:9090/api/responsible/auth/signup');
      expect(req.request.method).toBe('POST');
      expect(req.request.body.cpf).toBe('12345678900');
      expect(req.request.body.phone).toBe('11987654321');
      expect(req.request.body.name).toBe('João Souza');
      expect(req.request.body.email).toBe('joao@example.com');
      req.flush({});
    });

    it('should clean phone mask before POST /driver/auth/signup request with FormData', () => {
      service.createDriver({
        documentPdf: new File([''], 'doc.pdf'),
        name: 'Motorista Silva',
        email: 'motorista@example.com',
        password: 'pass',
        confirmPassword: 'pass',
        phone: '(11) 91234-5678',
      }).subscribe();

      const req = httpMock.expectOne('http://localhost:9090/api/driver/auth/signup');
      expect(req.request.method).toBe('POST');
      expect(req.request.body instanceof FormData).toBe(true);
      const formData = req.request.body as FormData;
      expect(formData.get('phone')).toBe('11912345678');
      expect(formData.get('name')).toBe('Motorista Silva');
      req.flush('ok');
    });
  });
});
