import { Injectable } from '@angular/core';
import { Subject } from 'rxjs';

// ponte entre o interceptor HTTP e os botoes com [appCarregando]:
// o interceptor avisa quando uma acao (POST/PUT/PATCH/DELETE) terminou bem ou deu erro,
// e o botao que esta carregando completa a barra ou volta ela
@Injectable({ providedIn: 'root' })
export class BotaoCarregandoService {
  // quantos botoes estao carregando agora (o interceptor so segura a resposta quando tem algum)
  ativos = 0;

  readonly concluiu = new Subject<void>();
  readonly falhou = new Subject<void>();
}

// tempo da barra indo de onde estiver ate 100% (o interceptor segura a resposta esse tempo)
export const DURACAO_COMPLETAR = 220;
