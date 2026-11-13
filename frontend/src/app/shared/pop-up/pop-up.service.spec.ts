import { TestBed } from '@angular/core/testing';
import { describe, it, expect } from 'vitest';
import { PopUpService, mensagemDoBackend } from './pop-up.service';

describe('PopUpService', () => {
  it('lê a mensagem do JSON que chega como texto (responseType text)', () => {
    const erro = { status: 429, error: '{"message":"Aguarde 39 segundos para pedir outro código"}' };
    expect(mensagemDoBackend(erro)).toBe('Aguarde 39 segundos para pedir outro código');
  });

  it('429 mostra a mensagem do servidor com o título "Aguarde"', () => {
    const popUp = TestBed.inject(PopUpService);
    popUp.showError({ status: 429, error: '{"message":"Aguarde 39 segundos para pedir outro código"}' });
    const ultimo = popUp.popups().at(-1)!;
    expect(ultimo.title).toBe('Aguarde');
    expect(ultimo.message).toBe('Aguarde 39 segundos para pedir outro código');
  });
});
