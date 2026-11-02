import { CanActivateFn, Router } from '@angular/router';
import { inject } from '@angular/core';
import { AuthService } from '../services/auth.service';

// usuario precisa estar logado (token valido), senao volta pra tela inicial
export const routeGuard: CanActivateFn = () => {
  const authService = inject(AuthService);
  const router = inject(Router);

  if (authService.isAuthenticated()) {
    return true;
  }

  authService.logout();
  return router.createUrlTree(['/']);
};

// confere se alguma role do token esta em data.rolesPermitted
// ROLE_ADMIN entra em tudo
export const roleGuard: CanActivateFn = (route) => {
  const authService = inject(AuthService);
  const router = inject(Router);

  if (!authService.isAuthenticated()) {
    authService.logout();
    return router.createUrlTree(['/']);
  }

  const userRoles = authService.getRolesFromToken();
  const rolesPermitidas: string[] = route.data['rolesPermitted'] || route.data['rolesPermitidas'] || [];

  if (userRoles.includes('ROLE_ADMIN') || userRoles.some((role) => rolesPermitidas.includes(role))) {
    return true;
  }

  console.warn(`Acesso negado para as roles: ${userRoles.join(', ')}`);

  // manda pra home dele em vez de '/', evita ficar quicando entre os guards
  const home = authService.getHomeRoute();
  if (home) {
    return router.createUrlTree([home]);
  }

  authService.logout();
  return router.createUrlTree(['/']);
};

// paginas publicas (home, login, cadastro): se ja ta logado manda pra home da role
export const guestGuard: CanActivateFn = () => {
  const authService = inject(AuthService);
  const router = inject(Router);

  if (!authService.isAuthenticated()) {
    return true;
  }

  const home = authService.getHomeRoute();
  if (home) {
    return router.createUrlTree([home]);
  }

  // token sem role conhecida: descarta e deixa entrar na pagina publica (antes isso dava loop)
  authService.logout();
  return true;
};
