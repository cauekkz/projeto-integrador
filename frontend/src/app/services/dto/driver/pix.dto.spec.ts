import { describe, it, expect } from 'vitest';
import { formatarChavePix, formatarCnpj } from './pix.dto';

describe('formatarChavePix', () => {
  it('mostra CPF, CNPJ e telefone com máscara', () => {
    expect(formatarChavePix('CPF', '80526060638')).toBe('805.260.606-38');
    expect(formatarChavePix('CNPJ', '11222333000181')).toBe('11.222.333/0001-81');
    expect(formatarChavePix('PHONE', '+5511912345678')).toBe('(11) 91234-5678');
  });

  it('e-mail e chave aleatória ficam como vieram', () => {
    expect(formatarChavePix('EMAIL', 'ana@example.com')).toBe('ana@example.com');
    expect(formatarChavePix('RANDOM', 'abc-123')).toBe('abc-123');
    expect(formatarChavePix(null, null)).toBe('');
  });

  it('CNPJ pela metade (enquanto digita)', () => {
    expect(formatarCnpj('11222')).toBe('11.222');
  });
});
