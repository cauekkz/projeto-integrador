import { Routes } from '@angular/router';
import { Home } from './pages/home/home';
import { Login } from './pages/auth/login/login';
import { Signup } from './pages/auth/signup/signup';

export const routes: Routes = [
  {
    path: '',
    component: Home,
  },
  {
    path: 'login/:tipo',
    component: Login,
  },
  {
    path: 'signup/:tipo',
    component: Signup
  },
];
