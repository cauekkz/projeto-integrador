import { Routes } from '@angular/router';
import { Home } from './pages/home/home';
import { Login } from './pages/auth/login/login';
import { Signup } from './pages/auth/signup/signup';
import { EmailCode } from './shared/email-code/email-code';
import { HomeScreen } from './pages/home-screen/home-screen';
import { AddStudent } from './components/student/add-student/add-student';
import { DriverHome } from './pages/driver-home/driver-home';
import { DriverRoute } from './pages/driver-route/driver-route';
import { StudentCode } from './components/student/student-code/student-code';
import { Dependentes } from './components/student/dependentes/dependentes';
import { Chat } from './pages/chat/chat';
import { ProfileInfo } from './pages/profile-info/profile-info';
import { ProfileSelect } from './components/profile-select/profile-select';
import { ChatDetails } from './components/chat-details/chat-details';
import { routeGuard, roleGuard, guestGuard } from './interceptor/route.interceptor';

export const routes: Routes = [
  // qq esse faz?
  // faz o front verificar se o usuario ta logado, se ele ta ele manda pro brabo
  // amanha se der testo isso
  //
  {
    path: '',
    component: Home,
    canActivate: [guestGuard],
  },
  {
    path: 'login/:tipo',
    component: Login,
    canActivate: [guestGuard],
  },
  {
    path: 'signup/:tipo',
    component: Signup,
    canActivate: [guestGuard],
  },
  {
    path: 'email-code',
    component: EmailCode,
    canActivate: [guestGuard],
  },

  // henrique kct, toda hora q tu criar alguma pagina nova q n seja pro usuario normal, usar esse can Activate q ai se ele tenta entra igual maluco
  // ele redireciona pras "publicas" de cima ai, qlq coisa fala comigo
  // e esse roleGuard serve pra ver qual usuario pode entrar em certa rota, isso vou testar dps ainda
  // e mesma coisa, fala comigo ou ve qual tipo de usuario vai usar
  /*
  ROLE_RESPONSIBLE = responsavel
  ROLE_DRIVER = motorista
   */
  {
    path: 'home-screen',
    component: HomeScreen,
    canActivate: [routeGuard, roleGuard],
    data: { rolesPermitted: ['ROLE_RESPONSIBLE'] },
  },
  {
    path: 'add-student',
    component: AddStudent,
    canActivate: [routeGuard, roleGuard],
    data: { rolesPermitted: ['ROLE_RESPONSIBLE'] },
  },
  {
    path: 'driver-home',
    component: DriverHome,
    canActivate: [routeGuard, roleGuard],
    data: { rolesPermitted: ['ROLE_DRIVER'] },
  },
  {
    path: 'driver-route',
    component: DriverRoute,
    canActivate: [routeGuard, roleGuard],
    data: { rolesPermitted: ['ROLE_DRIVER'] },
  },
  {
    path: 'student-code',
    component: StudentCode,
    canActivate: [routeGuard, roleGuard],
    data: { rolesPermitted: ['ROLE_RESPONSIBLE'] },
  },
  {
    path: 'dependentes',
    component: Dependentes,
    canActivate: [routeGuard, roleGuard],
    data: { rolesPermitted: ['ROLE_RESPONSIBLE'] },
  },
  {
    path: 'chat',
    component: Chat,
    canActivate: [routeGuard, roleGuard],
    data: { rolesPermitted: ['ROLE_DRIVER', 'ROLE_RESPONSIBLE'] },
  },
  {
    path: 'chat-details/:id',
    component: ChatDetails,
    canActivate: [routeGuard, roleGuard],
    data: { rolesPermitted: ['ROLE_DRIVER', 'ROLE_RESPONSIBLE'] },
  },
  {
    path: 'profile-info/:tipo',
    component: ProfileInfo,
    canActivate: [routeGuard, roleGuard],
    data: { rolesPermitted: ['ROLE_DRIVER', 'ROLE_RESPONSIBLE'] },
  },
  {
    path: 'profile-select',
    component: ProfileSelect,
    canActivate: [routeGuard, roleGuard],
    data: { rolesPermitted: ['ROLE_DRIVER', 'ROLE_RESPONSIBLE'] },
  },
];
