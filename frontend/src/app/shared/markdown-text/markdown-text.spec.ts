import { TestBed } from '@angular/core/testing';
import { describe, it, expect } from 'vitest';
import { MarkdownText } from './markdown-text';

describe('MarkdownText', () => {
  it('não deixa espaço entre o negrito e a pontuação', () => {
    const fixture = TestBed.createComponent(MarkdownText);
    fixture.componentRef.setInput('conteudo', 'O app **não repassa dinheiro**. O responsável paga.');
    fixture.detectChanges();
    const texto = (fixture.nativeElement.querySelector('.paragrafo') as HTMLElement).textContent!.trim();
    expect(texto).toBe('O app não repassa dinheiro. O responsável paga.');
  });
});
