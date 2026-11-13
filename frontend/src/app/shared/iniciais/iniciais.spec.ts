import { describe, it, expect } from 'vitest';
import { iniciaisDe } from './iniciais';

describe('iniciaisDe', () => {
  it('primeira e ultima palavra, sem particulas', () => {
    expect(iniciaisDe('Maria da Silva')).toBe('MS');
    expect(iniciaisDe('Pedro de Alcântara Neto')).toBe('PN');
    expect(iniciaisDe('Lucas Pereira')).toBe('LP');
    expect(iniciaisDe('  ana   martins ')).toBe('AM');
  });

  it('nome curto, acento, nulo', () => {
    expect(iniciaisDe('Jo')).toBe('J');
    expect(iniciaisDe('Érica Ômega')).toBe('ÉÔ');
    expect(iniciaisDe(null)).toBe('?');
    expect(iniciaisDe('')).toBe('?');
  });
});
