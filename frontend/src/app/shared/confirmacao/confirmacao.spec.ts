import { TestBed } from '@angular/core/testing';
import { describe, it, expect } from 'vitest';
import { Confirmacao } from './confirmacao';
import { ConfirmacaoService } from './confirmacao.service';

describe('Confirmacao (pop-up de confirmar)', () => {
  function montar() {
    const fixture = TestBed.configureTestingModule({ imports: [Confirmacao] }).createComponent(Confirmacao);
    fixture.detectChanges();
    const servico = TestBed.inject(ConfirmacaoService);
    const botao = (texto: string) =>
      [...fixture.nativeElement.querySelectorAll('button')].find((b: HTMLElement) => b.textContent?.trim() === texto) as HTMLElement | undefined;
    return { fixture, servico, botao };
  }

  it('mostra o pop-up no meio e responde sim na acao', async () => {
    const { fixture, servico, botao } = montar();
    const resposta = servico.perguntar({ titulo: 'Sair da conta?', texto: 'Até logo.', acao: 'Sair da conta' });
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.fundo.confirmacao')).not.toBeNull();
    expect(fixture.nativeElement.textContent).toContain('Sair da conta?');
    expect(botao('Sair da conta')!.classList.contains('btn-perigo')).toBe(true);
    botao('Sair da conta')!.click();
    expect(await resposta).toBe(true);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.fundo')).toBeNull();
  });

  it('Voltar, clicar fora e Esc respondem nao', async () => {
    const { fixture, servico, botao } = montar();

    let resposta = servico.perguntar({ titulo: 'Iniciar a rota?', acao: 'Iniciar rota', tom: 'azul' });
    fixture.detectChanges();
    expect(botao('Iniciar rota')!.classList.contains('btn-azul')).toBe(true);
    botao('Voltar')!.click();
    expect(await resposta).toBe(false);

    resposta = servico.perguntar({ titulo: 'Apagar?', acao: 'Apagar' });
    fixture.detectChanges();
    (fixture.nativeElement.querySelector('.fundo') as HTMLElement).click();
    expect(await resposta).toBe(false);

    resposta = servico.perguntar({ titulo: 'Apagar?', acao: 'Apagar' });
    fixture.detectChanges();
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));
    expect(await resposta).toBe(false);
  });

  it('uma pergunta nova fecha a anterior como nao', async () => {
    const { servico } = montar();
    const primeira = servico.perguntar({ titulo: 'A?', acao: 'A' });
    servico.perguntar({ titulo: 'B?', acao: 'B' });
    expect(await primeira).toBe(false);
    expect(servico.aberta()?.titulo).toBe('B?');
  });
});
