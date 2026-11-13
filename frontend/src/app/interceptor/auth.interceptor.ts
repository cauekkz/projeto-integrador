import { HttpErrorResponse, HttpInterceptorFn, HttpRequest } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, switchMap, throwError } from 'rxjs';
import { PopUpService } from '../shared/pop-up';
import { AuthService } from '../services/auth.service';
import { SKIP_ERROR_POPUP } from './http-context';

export { SKIP_ERROR_POPUP } from './http-context';

// login, refresh, logout, cadastro e codigos de e-mail sao publicos: nao levam token nem tentam renovar
// (um 401 do login e senha errada, nao sessao vencida)
function isRotaPublica(req: HttpRequest<unknown>): boolean {
  return req.url.includes('/auth/');
}

function is401(error: unknown): boolean {
  return error instanceof HttpErrorResponse && error.status === 401;
}

// termos novos ainda nao aceitos: o backend barra quase tudo com 403 { code: 'TERMS_NOT_ACCEPTED', version }
function isTermosPendentes(error: unknown): boolean {
  return error instanceof HttpErrorResponse && error.status === 403 && error.error?.code === 'TERMS_NOT_ACCEPTED';
}

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const popUpService = inject(PopUpService);
  const authService = inject(AuthService);
  const router = inject(Router);

  const mostrarErro = (error: unknown) => {
    if (!req.context.get(SKIP_ERROR_POPUP)) {
      popUpService.showError(error);
    }
    return throwError(() => error);
  };

  if (isRotaPublica(req)) {
    return next(req).pipe(catchError(mostrarErro));
  }

  const enviar = (token: string | null) =>
    next(token ? req.clone({ setHeaders: { Authorization: `Bearer ${token}` } }) : req);

  // token vencido renova antes de mandar; se o backend ainda responder 401, renova uma vez e repete
  return authService.getValidToken().pipe(
    switchMap((token) =>
      enviar(token).pipe(
        catchError((error) => {
          if (is401(error) && token && authService.getRefreshToken()) {
            return authService.refreshTokens().pipe(switchMap((novoToken) => enviar(novoToken)));
          }
          return throwError(() => error);
        }),
      ),
    ),
    catchError((error) => {
      // nem o refresh resolveu (30 dias sem abrir, logout, token reaproveitado): volta pro login
      if (is401(error)) {
        if (authService.hasSession()) {
          authService.clearTokens();
          popUpService.show('Sua sessão expirou. Entre novamente.', 'warning', 'Sessão expirada');
          router.navigate(['/']);
        }
        return throwError(() => error);
      }
      // sem popup: leva pra tela de aceite (uma vez so, mesmo com varias chamadas barradas juntas)
      if (isTermosPendentes(error)) {
        if (!(router.url ?? '').startsWith('/terms')) {
          router.navigate(['/terms']);
        }
        return throwError(() => error);
      }
      return mostrarErro(error);
    }),
  );
};
