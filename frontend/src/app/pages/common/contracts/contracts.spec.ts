import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { Contracts } from './contracts';
import { AuthService } from '../../../services/auth.service';
import { contractSummaryResponse } from '../../../services/dto/contract/contract.dto';

function contrato(extra: Partial<contractSummaryResponse> = {}): contractSummaryResponse {
  return {
    contractId: 'c-1',
    userDriverContractId: 'udc-1',
    status: 'ACTIVE',
    periodicity: 'WEEKLY',
    value: 350,
    startDate: '2027-02-01',
    endDate: '2027-12-01',
    studentId: 's-1',
    studentName: 'Lucas Pereira',
    schoolId: 'e-1',
    schoolName: 'Fatec Ipiranga',
    driverId: 'd-1',
    driverName: 'Gustavo Gómez',
    responsibleId: 'r-1',
    responsibleName: 'Andreas Pereira',
    pendingCancellation: null,
    overduePayments: 0,
    ...extra,
  };
}

function pagina(content: contractSummaryResponse[], last = true, number = 0) {
  return { content, totalElements: content.length, totalPages: last ? number + 1 : number + 2, number, size: 20, numberOfElements: content.length, first: number === 0, last, empty: !content.length };
}

describe('Contracts (/contracts paginado, uma aba por status)', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [Contracts],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    vi.spyOn(TestBed.inject(AuthService), 'getRolesFromToken').mockReturnValue(['ROLE_DRIVER']);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    // cabecalho e rodape buscam o usuario logado; nao e o que esse teste confere
    http.match((r) => !r.url.includes('/contract/my-contracts')).forEach((r) => r.flush({}));
    http.verify();
    TestBed.resetTestingModule();
  });

  function abrir() {
    const fixture = TestBed.createComponent(Contracts);
    fixture.detectChanges();
    return fixture;
  }

  it('lê o content da página e mostra os avisos do cartão', () => {
    const fixture = abrir();
    const req = http.expectOne((r) => r.url === '/api/contract/my-contracts');
    expect(req.request.params.get('page')).toBe('0');
    expect(req.request.params.get('size')).toBe('20');
    expect(req.request.params.has('status')).toBe(false);

    req.flush(
      pagina([
        contrato({ status: 'SCHEDULED' }),
        contrato({
          contractId: 'c-2',
          studentName: 'Ana Martins',
          overduePayments: 2,
          pendingCancellation: {
            id: 'x', status: 'PENDING', unilateral: false, reason: null, requestedById: 'r-1', requestedByName: 'Andreas',
            requestedAt: '2027-03-01T10:00:00', effectiveDate: '2027-03-15', decidedById: null, decidedByName: null, decidedAt: null,
          },
        }),
      ]),
    );
    fixture.detectChanges();

    const texto = fixture.nativeElement.textContent as string;
    expect(texto).toContain('Agendado');
    expect(texto).toContain('Começa em 01/02');
    expect(texto).toContain('Cancela em 15/03');
    expect(texto).toContain('2 parcelas atrasadas');
    expect(texto).toContain('Responsável: Andreas Pereira');
    // sem foto de van no cartão
    expect(fixture.nativeElement.querySelector('img[src="/van.jpg"]')).toBeNull();
  });

  it('trocar de aba filtra pelo status no backend', () => {
    const fixture = abrir();
    http.expectOne((r) => r.url === '/api/contract/my-contracts').flush(pagina([contrato()]));

    fixture.componentInstance.trocarAba('SCHEDULED');
    const req = http.expectOne((r) => r.url === '/api/contract/my-contracts');
    expect(req.request.params.get('status')).toBe('SCHEDULED');
    expect(req.request.params.get('page')).toBe('0');
    req.flush(pagina([]));
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Nenhum contrato por aqui.');
  });

  it('carrega a próxima página enquanto não for a última', () => {
    const fixture = abrir();
    http.expectOne((r) => r.url === '/api/contract/my-contracts').flush(pagina([contrato()], false));

    fixture.componentInstance.carregarMais();
    const req = http.expectOne((r) => r.url === '/api/contract/my-contracts');
    expect(req.request.params.get('page')).toBe('1');
    req.flush(pagina([contrato({ contractId: 'c-9' })], true, 1));

    fixture.componentInstance.carregarMais();
    http.expectNone((r) => r.url === '/api/contract/my-contracts');
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelectorAll('.card').length).toBe(2);
  });
});
