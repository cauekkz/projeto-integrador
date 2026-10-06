import { CanActivateFn, Router } from '@angular/router';
import { inject } from '@angular/core';
import { jwtDecode } from 'jwt-decode';
import { AuthService } from '../services/auth.service';

export const routeGuard: CanActivateFn = (route, state) => {
  const authService = inject(AuthService);
  const router = inject(Router);

  const userId = authService.getUserIdFromToken();

  if (userId) {
    return true;
  }

  router.navigate(['/']);
  return false;
};

export const roleGuard: CanActivateFn = (route, state) => {
  const router = inject(Router);
  const token = localStorage.getItem('token');

  if (!token) {
    router.navigate(['/']);
    return false;
  }

  try {
    const decoded: any = jwtDecode(token);

    const userRole: string = decoded.role;

    const rolesPermitidas: string[] = route.data['rolesPermitidas'];

    if (userRole === 'ROLE_ADMIN') {
      return true;
    }

    if (rolesPermitidas && rolesPermitidas.includes(userRole)) {
      return true;
    }

    console.warn(`Acesso negado para a role: ${userRole}`);
    router.navigate(['/']);
    return false;
  } catch (error) {
    console.error('Erro ao processar a role no token:', error);
    router.navigate(['/']);
    return false;
  }
};

export const guestGuard: CanActivateFn = (route, state) => {
  const authService = inject(AuthService);
  const router = inject(Router);

  const userId = authService.getUserIdFromToken();

  if (userId) {
    const token = localStorage.getItem('token');

    try {
      const decoded: any = jwtDecode(token!);
      const userRole = decoded.role;


      if (userRole === 'ROLE_DRIVER') {
        router.navigate(['/driver-home']);
      } else if (userRole === 'ROLE_RESPONSIBLE' || userRole === 'ROLE_ADMIN') {
        router.navigate(['/home-screen']);
      } else {
        router.navigate(['/']);
      }
    } catch (error) {
      localStorage.removeItem('token');
      return true;
    }

    return false;
  }

  return true;
};
