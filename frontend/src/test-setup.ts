import { vi } from 'vitest';

// a suite inteira roda em paralelo e o primeiro teste de cada arquivo (que compila a tela) as vezes passa dos 5 s padrao
vi.setConfig({ testTimeout: 15000 });
