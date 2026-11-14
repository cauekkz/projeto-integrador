import { HttpInterceptorFn, HttpResponse } from '@angular/common/http';
import { inject } from '@angular/core';
import { concatMap, delay, of, tap } from 'rxjs';
import { BotaoCarregandoService, DURACAO_COMPLETAR } from './botao-carregando.service';

// so muda o tempo da resposta, nunca o conteudo: com um botao carregando, uma acao que deu certo
// espera a barra chegar em 100% antes de chegar na tela (e ai a tela navega ou mostra o resultado);
// uma acao com erro avisa o botao na hora. GET (listas, polling) passa direto
export const botaoCarregandoInterceptor: HttpInterceptorFn = (req, next) => {
  if (req.method === 'GET') return next(req);
  const botoes = inject(BotaoCarregandoService);

  return next(req).pipe(
    tap({ error: () => botoes.ativos > 0 && botoes.falhou.next() }),
    concatMap((evento) => {
      if (!(evento instanceof HttpResponse) || botoes.ativos === 0) return of(evento);
      botoes.concluiu.next();
      return of(evento).pipe(delay(DURACAO_COMPLETAR));
    }),
  );
};
