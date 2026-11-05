import { describe, it, expect } from 'vitest';
import { paramsDePagina, paginaVazia, PAGE_SIZE_MAX } from './page.dto';

describe('Page<T> (paginas do backend)', () => {
  it('sem pedido não manda page nem size', () => {
    expect(paramsDePagina()).toEqual({});
    expect(paramsDePagina({})).toEqual({});
  });

  it('manda page e size como vieram quando estão no limite', () => {
    expect(paramsDePagina({ page: 2, size: 20 })).toEqual({ page: 2, size: 20 });
  });

  it('size fica entre 1 e 100 e page não fica negativa', () => {
    expect(paramsDePagina({ size: 500 })).toEqual({ size: PAGE_SIZE_MAX });
    expect(paramsDePagina({ size: 0 })).toEqual({ size: 1 });
    expect(paramsDePagina({ page: -3 })).toEqual({ page: 0 });
  });

  it('página vazia é a última e não tem itens', () => {
    const pagina = paginaVazia<string>();
    expect(pagina.content).toEqual([]);
    expect(pagina.last).toBe(true);
    expect(pagina.empty).toBe(true);
  });
});
