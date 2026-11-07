import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { describe, it, expect, afterEach } from 'vitest';
import { TermsAccept } from './terms-accept';
import { lerMarkdown } from '../../../shared/markdown-text/markdown-text';

const TEXTO = `# Termos de Uso e Política de Privacidade do VanRoute (versão 3)

Ao criar a conta, você concorda.

## 1. O que é o VanRoute
O app **não recebe dinheiro**.

- **Do motorista:**
  - **CNH:** lida na hora.
`;

describe('Termos no cadastro', () => {
  let http: HttpTestingController;

  afterEach(() => {
    http?.verify();
    TestBed.resetTestingModule();
    document.body.style.overflow = '';
  });

  it('lê o markdown dos termos em títulos, parágrafos, lista e negrito', () => {
    const blocos = lerMarkdown(TEXTO);
    expect(blocos.map((b) => b.tipo)).toEqual(['h1', 'p', 'h2', 'p', 'li', 'li']);
    expect(blocos[3].trechos).toEqual([
      { texto: 'O app ', negrito: false },
      { texto: 'não recebe dinheiro', negrito: true },
      { texto: '.', negrito: false },
    ]);
    expect(blocos[5].nivel).toBe(1);
  });

  it('abre a página dos termos e "Li e aceito" marca o checkbox com a versão', () => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    http = TestBed.inject(HttpTestingController);
    const fixture = TestBed.createComponent(TermsAccept);
    const versoes: (string | null)[] = [];
    fixture.componentInstance.versaoAceitaChange.subscribe((v) => versoes.push(v));
    fixture.detectChanges();
    http.expectOne('/api/terms').flush({ version: '3', content: TEXTO });
    fixture.detectChanges();

    (fixture.nativeElement.querySelector('.link') as HTMLElement).click();
    fixture.detectChanges();
    const tela = fixture.nativeElement.querySelector('.tela-termos') as HTMLElement;
    expect(tela.textContent).toContain('Versão 3');
    expect(tela.querySelector('strong')?.textContent).toBe('não recebe dinheiro');
    expect(document.body.style.overflow).toBe('hidden');

    const aceitar = [...tela.querySelectorAll('button')].find((b) => b.textContent?.trim() === 'Li e aceito') as HTMLElement;
    aceitar.click();
    fixture.detectChanges();

    expect(versoes).toEqual(['3']);
    expect(fixture.nativeElement.querySelector('.tela-termos')).toBeNull();
    expect((fixture.nativeElement.querySelector('input[type=checkbox]') as HTMLInputElement).checked).toBe(true);
    expect(document.body.style.overflow).toBe('');
  });
});
