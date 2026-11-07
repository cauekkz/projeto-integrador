import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ActivatedRoute, Router, convertToParamMap, provideRouter } from '@angular/router';
import { describe, it, expect, afterEach, vi } from 'vitest';
import { PopUpService } from '../../../shared/pop-up';
import { DriverProfile } from './driver-profile';
import { ResponsibleProfile } from '../../driver/responsible-profile/responsible-profile';
import { driverProfile, responsibleProfile } from '../../../services/dto/profile/profile.dto';

const PERFIL: driverProfile = {
  driverId: 'd-1', name: 'Gustavo Gómez', photoKey: null, memberSince: '2025-01-10',
  schools: [{ schoolId: 's-1', name: 'Fatec Ipiranga' }, { schoolId: 's-2', name: 'Colégio Bandeirantes' }],
  vehicles: [{ model: 'Sprinter', color: 'Prata', plate: 'ABC1D23' }],
  ratingAverage: 4.6, ratingCount: 12, myRating: null,
  recentRatings: [{ score: 5, comment: 'Sempre no horário', date: '2026-10-10T10:00:00', responsibleName: 'Carla' }],
};

describe('Perfis e avaliação', () => {
  let http: HttpTestingController;
  let popUp: { show: ReturnType<typeof vi.fn>; showError: ReturnType<typeof vi.fn> };

  function configurar(param: Record<string, string>) {
    popUp = { show: vi.fn(), showError: vi.fn() };
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(), provideHttpClientTesting(), provideRouter([]),
        { provide: PopUpService, useValue: popUp },
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: convertToParamMap(param) } } },
      ],
    });
    http = TestBed.inject(HttpTestingController);
  }

  afterEach(() => {
    if (!http) return;
    for (const req of http.match(() => true)) if (!req.cancelled) req.flush({});
    http.verify();
    TestBed.resetTestingModule();
  });

  function botao(fixture: { nativeElement: HTMLElement }, texto: string) {
    return [...fixture.nativeElement.querySelectorAll('button')].find((b) => b.textContent?.trim() === texto) as HTMLElement | undefined;
  }

  describe('/driver-profile/:driverId', () => {
    function criar(perfil: driverProfile) {
      configurar({ driverId: 'd-1' });
      const fixture = TestBed.createComponent(DriverProfile);
      fixture.detectChanges();
      http.expectOne('/api/driver/d-1/profile').flush(perfil);
      fixture.detectChanges();
      return fixture;
    }

    it('mostra nota, escolas, van sem lotação e avalia (403 com aviso)', async () => {
      const fixture = criar(PERFIL);
      const texto = fixture.nativeElement.textContent;
      expect(texto).toContain('No VanRoute desde 10/01/2025');
      expect(texto).toContain('4,6');
      expect(texto).toContain('12 avaliações');
      expect(texto).toContain('Colégio Bandeirantes');
      expect(texto).toContain('ABC1D23');
      expect(texto).toContain('Sempre no horário');

      botao(fixture, 'Avaliar motorista')!.click();
      fixture.detectChanges();
      await fixture.whenStable();
      fixture.nativeElement.querySelectorAll('.escolher-nota button')[3].click();
      fixture.detectChanges();
      botao(fixture, 'Enviar avaliação')!.click();
      const req = http.expectOne({ method: 'PUT', url: '/api/driver/d-1/rating' });
      expect(req.request.body).toEqual({ score: 4 });
      req.flush({ message: 'Só avalia quem tem ou já teve contrato com esse motorista' }, { status: 403, statusText: 'Forbidden' });
      expect(popUp.show).toHaveBeenCalledWith('Só avalia quem tem ou já teve contrato com esse motorista', 'error', 'Sem permissão');
      fixture.detectChanges();
      expect(fixture.nativeElement.querySelector('.tela-cheia')).not.toBeNull();
    });

    it('já avaliei: "Sua avaliação", editar e apagar', () => {
      const minha = { score: 5, comment: 'Muito pontual', date: '2026-10-15T10:00:00', responsibleName: 'Andreas' };
      const fixture = criar({ ...PERFIL, myRating: minha, recentRatings: [minha, ...PERFIL.recentRatings] });
      expect(fixture.nativeElement.textContent).toContain('Sua avaliação');
      expect(fixture.nativeElement.querySelectorAll('.avaliacao').length).toBe(2);

      botao(fixture, 'Editar avaliação')!.click();
      fixture.detectChanges();
      botao(fixture, 'Apagar avaliação')!.click();
      fixture.detectChanges();
      expect(fixture.nativeElement.textContent).toContain('Apagar sua avaliação?');
      const confirmar = [...fixture.nativeElement.querySelectorAll('.folha button')].find((b) => (b as HTMLElement).textContent?.trim() === 'Apagar avaliação') as HTMLElement;
      confirmar.click();
      http.expectOne({ method: 'DELETE', url: '/api/driver/d-1/rating' }).flush(null, { status: 204, statusText: 'No Content' });
      http.expectOne('/api/driver/d-1/profile').flush({ ...PERFIL, ratingCount: 0, ratingAverage: null, recentRatings: [] });
      fixture.detectChanges();
      expect(fixture.nativeElement.textContent).toContain('Ainda sem avaliações');
    });

    it('403: perfil indisponível com Voltar', () => {
      configurar({ driverId: 'd-1' });
      const fixture = TestBed.createComponent(DriverProfile);
      fixture.detectChanges();
      http.expectOne('/api/driver/d-1/profile').flush({}, { status: 403, statusText: 'Forbidden' });
      fixture.detectChanges();
      expect(popUp.show).toHaveBeenCalledWith(expect.stringContaining('com quem conversa ou tem contrato'), 'error', 'Perfil indisponível');
      expect(botao(fixture, 'Voltar')).toBeTruthy();
    });
  });

  describe('/responsible-profile/:responsibleId', () => {
    const RESP: responsibleProfile = {
      id: 'r-1', name: 'Andreas Pereira', photoKey: null, phone: '11999999999', financialStatus: 'UP_TO_DATE', overduePayments: 0,
      students: [{ studentId: 'lucas', studentName: 'Lucas Pereira', contractId: 'c-1', contractStatus: 'ACTIVE', relationType: 'FINANCIAL', admin: true, photoKey: null }],
    };

    it('em dia e com atraso (Ver parcelas abre as atrasadas)', () => {
      configurar({ responsibleId: 'r-1' });
      const fixture = TestBed.createComponent(ResponsibleProfile);
      fixture.detectChanges();
      http.expectOne('/api/responsible/r-1/profile').flush({ ...RESP, financialStatus: 'OVERDUE', overduePayments: 1 });
      fixture.detectChanges();
      const texto = fixture.nativeElement.textContent;
      expect(texto).toContain('Situação financeira com você');
      expect(texto).toContain('1 parcela atrasada');
      expect(texto).toContain('Financeiro · Admin');
      expect(texto).toContain('Ativo');
      expect(popUp.show).toHaveBeenCalledWith('Conta só as parcelas dos contratos com você.', 'error', '1 parcela atrasada');

      const navegar = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
      botao(fixture, 'Ver parcelas')!.click();
      expect(navegar).toHaveBeenCalledWith(['/payments'], { queryParams: { status: 'OVERDUE' } });
      (fixture.nativeElement.querySelector('.contrato') as HTMLElement).click();
      expect(navegar).toHaveBeenCalledWith(['/contract', 'c-1']);
    });
  });
});
