import { HttpContextToken, HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';
import { PopUpService } from '../shared/pop-up';
import { AuthService } from '../services/auth.service';

// requisicoes que tratam o erro na propria tela marcam isso pra nao aparecer popup duplicado
export const SKIP_ERROR_POPUP = new HttpContextToken<boolean>(() => false);

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const token = localStorage.getItem('token');
  const popUpService = inject(PopUpService);
  const authService = inject(AuthService);
  const router = inject(Router);

  if (token) {
    req = req.clone({
      setHeaders: {
        Authorization: `Bearer ${token}`,
      },
    });
  }

  return next(req).pipe(
    catchError((error) => {
      // 401 com token = sessao invalida/expirada: limpa e manda pra tela inicial
      if (error instanceof HttpErrorResponse && error.status === 401 && token) {
        authService.logout();
        router.navigate(['/']);
      }

      if (!req.context.get(SKIP_ERROR_POPUP)) {
        popUpService.showError(error);
      }
      return throwError(() => error);
    })
  );
};
