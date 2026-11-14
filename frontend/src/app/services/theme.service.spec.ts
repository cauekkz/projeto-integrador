import { TestBed } from '@angular/core/testing';
import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { CHAVE_TEMA, ThemeService } from './theme.service';

// jwt fake so com payload
function fakeToken(payload: object) {
  const b = (o: object) => btoa(JSON.stringify(o)).replace(/=+$/, '');
  return `${b({ alg: 'HS256' })}.${b(payload)}.assinatura`;
}

// matchMedia falso: controla o "modo escuro do sistema" e dispara o change
function mockSistema(escuroInicial: boolean) {
  const ouvintes: ((e: { matches: boolean }) => void)[] = [];
  const mql = {
    matches: escuroInicial,
    addEventListener: (_: string, fn: (e: { matches: boolean }) => void) => ouvintes.push(fn),
    removeEventListener: () => {},
  };
  vi.stubGlobal('matchMedia', (q: string) =>
    q.includes('prefers-color-scheme') ? mql : { matches: false, addEventListener() {}, removeEventListener() {} },
  );
  return {
    mudar(escuro: boolean) {
      mql.matches = escuro;
      ouvintes.forEach((fn) => fn({ matches: escuro }));
    },
  };
}

function criar() {
  const s = TestBed.inject(ThemeService);
  TestBed.tick();
  return s;
}

const tema = () => document.documentElement.dataset['theme'];

describe('ThemeService', () => {
  beforeEach(() => {
    localStorage.clear();
    document.documentElement.removeAttribute('data-theme');
    TestBed.configureTestingModule({});
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    localStorage.clear();
  });

  it('sem escolha salva segue o sistema (claro)', () => {
    mockSistema(false);
    const s = criar();
    expect(s.tema()).toBe('sistema');
    expect(tema()).toBe('light');
  });

  it('sem escolha salva segue o sistema (escuro) e reage quando o sistema muda', () => {
    const sistema = mockSistema(true);
    criar();
    expect(tema()).toBe('dark');
    sistema.mudar(false);
    TestBed.tick();
    expect(tema()).toBe('light');
  });

  it('escolha salva vale mais que o sistema', () => {
    mockSistema(false);
    localStorage.setItem(CHAVE_TEMA, 'escuro');
    criar();
    expect(tema()).toBe('dark');
  });

  it('valor invalido no storage volta pra sistema', () => {
    mockSistema(false);
    localStorage.setItem(CHAVE_TEMA, 'roxo');
    expect(criar().tema()).toBe('sistema');
  });

  it('escolher salva no localStorage e aplica no <html>', () => {
    mockSistema(false);
    const s = criar();
    s.escolher('escuro');
    TestBed.tick();
    expect(localStorage.getItem(CHAVE_TEMA)).toBe('escuro');
    expect(tema()).toBe('dark');
    s.escolher('claro');
    TestBed.tick();
    expect(tema()).toBe('light');
  });

  it('storage bloqueado nao quebra (le sistema, escolha vale na sessao)', () => {
    mockSistema(false);
    vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => {
      throw new Error('bloqueado');
    });
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw new Error('bloqueado');
    });
    const s = criar();
    expect(s.tema()).toBe('sistema');
    s.escolher('escuro');
    TestBed.tick();
    expect(tema()).toBe('dark');
    vi.restoreAllMocks();
  });

  it('atualiza a cor da barra do navegador (meta theme-color)', () => {
    mockSistema(false);
    const meta = document.createElement('meta');
    meta.name = 'theme-color';
    document.head.appendChild(meta);
    const s = criar();
    expect(meta.content).toBe('#ffffff');
    s.escolher('escuro');
    TestBed.tick();
    expect(meta.content).toBe('#323232');
    meta.remove();
  });

  it('motorista logado tambem usa o escuro', () => {
    mockSistema(false);
    localStorage.setItem(CHAVE_TEMA, 'escuro');
    localStorage.setItem('token', fakeToken({ sub: '1', roles: ['ROLE_DRIVER'] }));
    criar();
    expect(tema()).toBe('dark');
  });
});
