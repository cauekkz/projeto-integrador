import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ActivatedRoute, Router, convertToParamMap, provideRouter } from '@angular/router';
import { describe, it, expect, afterEach, beforeEach, vi } from 'vitest';
import { PopUpService } from '../../../shared/pop-up';
import { AuthService } from '../../../services/auth.service';
import { Privacy } from './privacy';
import { TermsPage } from '../terms/terms';

describe('Privacidade e termos', () => {
  let http: HttpTestingController;
  let popUp: { show: ReturnType<typeof vi.fn>; showError: ReturnType<typeof vi.fn> };
  let router: Router;

  function configurar(query: Record<string, string> = {}) {
    popUp = { show: vi.fn(), showError: vi.fn() };
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(), provideHttpClientTesting(), provideRouter([]),
        { provide: PopUpService, useValue: popUp },
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: convertToParamMap({}), queryParamMap: convertToParamMap(query) } } },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    router = TestBed.inject(Router);
    vi.spyOn(router, 'navigate').mockResolvedValue(true);
  }

  beforeEach(() => {
    try { localStorage.removeItem('vanroute.exportadoEm'); } catch {}
  });

  afterEach(() => {
    if (!http) return;
    for (const req of http.match(() => true)) if (!req.cancelled) req.flush({});
    http.verify();
    TestBed.resetTestingModule();
  });

  function botao(fixture: { nativeElement: HTMLElement }, texto: string) {
    return [...fixture.nativeElement.querySelectorAll('button')].find((b) => b.textContent?.includes(texto)) as HTMLButtonElement | undefined;
  }

  it('exporta os dados, baixa o arquivo e desliga o item por 5 minutos', () => {
    configurar();
    const clique = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {});
    URL.createObjectURL = vi.fn(() => 'blob:x');
    URL.revokeObjectURL = vi.fn();
    const fixture = TestBed.createComponent(Privacy);
    fixture.detectChanges();

    botao(fixture, 'Exportar meus dados')!.click();
    http.expectOne('/api/users/me/export').flush({ user: { name: 'Ana' } });
    fixture.detectChanges();

    expect(clique).toHaveBeenCalled();
    expect(popUp.show).toHaveBeenCalledWith('O arquivo meus-dados.json foi baixado.', 'success', 'Dados exportados');
    expect(fixture.nativeElement.textContent).toContain('Disponível de novo em 5 minutos');
    expect(botao(fixture, 'Exportar meus dados')!.disabled).toBe(true);
    clique.mockRestore();
  });

  it('excluir: aviso, senha e 204 limpa a sessão e volta pro início', async () => {
    configurar();
    const limpar = vi.spyOn(TestBed.inject(AuthService), 'clearTokens');
    const fixture = TestBed.createComponent(Privacy);
    fixture.detectChanges();

    botao(fixture, 'Excluir minha conta')!.click();
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Excluir sua conta?');
    botao(fixture, 'Continuar')!.click();
    fixture.detectChanges();
    await fixture.whenStable();
    expect(fixture.nativeElement.textContent).toContain('Confirme com sua senha');

    const input = fixture.nativeElement.querySelector('#senha-excluir') as HTMLInputElement;
    input.value = 'Senha@123';
    input.dispatchEvent(new Event('input'));
    fixture.nativeElement.querySelector('form.senha').dispatchEvent(new Event('submit'));
    const req = http.expectOne('/api/users/me');
    expect(req.request.method).toBe('DELETE');
    expect(req.request.body).toEqual({ password: 'Senha@123' });
    req.flush(null, { status: 204, statusText: 'No Content' });

    expect(limpar).toHaveBeenCalled();
    expect(router.navigate).toHaveBeenCalledWith(['/']);
  });

  it('409 de parcela fecha a folha e mostra "Ver parcelas"', async () => {
    configurar();
    const fixture = TestBed.createComponent(Privacy);
    fixture.detectChanges();
    botao(fixture, 'Excluir minha conta')!.click();
    fixture.detectChanges();
    botao(fixture, 'Continuar')!.click();
    fixture.detectChanges();
    await fixture.whenStable();
    const input = fixture.nativeElement.querySelector('#senha-excluir') as HTMLInputElement;
    input.value = 'Senha@123';
    input.dispatchEvent(new Event('input'));
    fixture.nativeElement.querySelector('form.senha').dispatchEvent(new Event('submit'));
    http.expectOne('/api/users/me').flush(
      { message: 'Há parcelas atrasadas. Elas precisam ser quitadas antes de excluir a conta' },
      { status: 409, statusText: 'Conflict' },
    );
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).not.toContain('Confirme com sua senha');
    botao(fixture, 'Ver parcelas')!.click();
    expect(router.navigate).toHaveBeenCalledWith(['/payments'], { queryParams: { status: 'OVERDUE' } });
  });

  it('termos pelo menu: só leitura, sem botões de aceite', () => {
    configurar({ leitura: '1' });
    const fixture = TestBed.createComponent(TermsPage);
    fixture.detectChanges();
    http.expectOne('/api/terms').flush({ version: '3', content: '# Termos de Uso e Política de Privacidade\n\n## 1. O que é\nO app **não recebe dinheiro**.' });
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('.texto h2')?.textContent).toContain('Termos de Uso e Política de Privacidade');
    expect(fixture.nativeElement.querySelector('.texto h3')?.textContent).toContain('1. O que é');
    expect(fixture.nativeElement.querySelector('.texto strong')?.textContent).toBe('não recebe dinheiro');
    expect(fixture.nativeElement.textContent).not.toContain('**');
    expect(botao(fixture, 'Aceito')).toBeUndefined();
    expect(popUp.show).not.toHaveBeenCalled();
  });

  it('reaceite: avisa a versão, "Não aceito" oferece sair ou excluir e excluir pula o aviso', () => {
    configurar();
    const fixture = TestBed.createComponent(TermsPage);
    fixture.detectChanges();
    http.expectOne('/api/terms').flush({ version: '3', content: 'texto' });
    fixture.detectChanges();
    expect(popUp.show).toHaveBeenCalledWith('Para continuar usando o VanRoute, leia e aceite a versão 3.', 'info', 'Os termos mudaram');

    botao(fixture, 'Não aceito')!.click();
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Sem aceitar, não dá para usar o app');
    expect(botao(fixture, 'Sair da conta')).toBeTruthy();

    botao(fixture, 'Excluir minha conta')!.click();
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Confirme com sua senha');

    botao(fixture, 'Voltar e ler os termos')!.click();
    fixture.detectChanges();
    expect(botao(fixture, 'Aceito')).toBeTruthy();
  });
});
