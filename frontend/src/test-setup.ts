import { vi } from 'vitest';

// a suite inteira roda em paralelo e o primeiro teste de cada arquivo (que compila a tela) as vezes passa dos 5 s padrao
vi.setConfig({ testTimeout: 15000 });

// o jsdom nao tem ResizeObserver e a TabBar da Glisse (rodape do motorista) usa: stub so pros testes
if (typeof globalThis.ResizeObserver === 'undefined') {
  globalThis.ResizeObserver = class {
    observe() {}
    unobserve() {}
    disconnect() {}
  } as unknown as typeof ResizeObserver;
}

// idem matchMedia (a Glisse pergunta tema escuro e "menos movimento")
if (typeof window !== 'undefined' && typeof window.matchMedia !== 'function') {
  window.matchMedia = ((query: string) => ({
    matches: false,
    media: query,
    onchange: null,
    addEventListener() {},
    removeEventListener() {},
    addListener() {},
    removeListener() {},
    dispatchEvent: () => false,
  })) as unknown as typeof window.matchMedia;
}
