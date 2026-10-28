import { Routes } from '@angular/router';
import { Home } from './pages/home/home';
import { Login } from './pages/auth/login/login';
import { Signup } from './pages/auth/signup/signup';
import { EmailCode } from './components/login-signup/email-code/email-code';
import { HomeScreen } from './pages/responsible-home/home-screen';
import { AddStudent } from './components/student/add-student/add-student';
import { DriverHome } from './pages/driver-home/driver-home';
import { DriverRoute } from './pages/driver-route/driver-route';
import { StudentCode } from './components/student/student-code/student-code';
import { Dependentes } from './components/student/dependentes/dependentes';
import { Chat } from './pages/chat/chat';
import { ProfileInfo } from './pages/profile-info/profile-info';
import { ProfileSelect } from './components/profile-select/profile-select';
import { ChatDetails } from './components/chat/chat-details/chat-details';
import { routeGuard, roleGuard, guestGuard } from './interceptor/route.interceptor';
import { DriverCode } from './components/driver-code/driver-code';
import { ChatListComponent } from './components/chat/chat-list/chat-list';
import { NotFound } from './pages/not-found/not-found';
import { DriverLinkCode } from './pages/driver-link-code/driver-link-code';

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


  // henrique, toda hora q tu criar alguma pagina nova q n seja pro usuario normal, usar esse can Activate q ai se ele tenta entra igual maluco
  // ele redireciona pras "publicas" de cima ai, qlq coisa fala comigo
  // e esse roleGuard serve pra ver qual usuario pode entrar em certa rota, isso vou testar dps ainda
  // e mesma coisa, fala comigo ou ve qual tipo de usuario vai usar
  /*
  ROLE_RESPONSIBLE = responsavel
  ROLE_DRIVER = motorista
   */
  {
    path: 'responsible-home',
    component: HomeScreen,
    canActivate: [routeGuard, roleGuard],
    data: { rolesPermitted: ['ROLE_RESPONSIBLE'] },
  },
  {
    path: 'home-screen',
    redirectTo: 'responsible-home',
    pathMatch: 'full',
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
    path: 'chat-list',
    redirectTo: 'chat',
    pathMatch: 'full',
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
  {
    // responsavel digita o codigo do motorista (o redeem do backend so aceita responsavel)
    path: 'driver-code',
    component: DriverCode,
    canActivate: [routeGuard, roleGuard],
    data: { rolesPermitted: ['ROLE_RESPONSIBLE'] },
  },
  {
    // motorista ve o proprio codigo de conversa
    path: 'driver-link-code',
    component: DriverLinkCode,
    canActivate: [routeGuard, roleGuard],
    data: { rolesPermitted: ['ROLE_DRIVER'] },
  },
  {
    // motorista monta a proposta de contrato pro responsavel do chat (o backend tambem barra quem nao e motorista)
    path: 'driver-contract/:chatId',
    // carregada sob demanda: so o motorista usa e deixa o bundle inicial menor
    loadComponent: () => import('./pages/driver-contract/driver-contract-page').then((m) => m.DriverContractPage),
    canActivate: [routeGuard, roleGuard],
    data: { rolesPermitted: ['ROLE_DRIVER'] },
  },
  {
    // aba "Adicionar escola" do profile select
    path: 'driver-schools',
    loadComponent: () => import('./pages/driver-schools/driver-schools-page').then((m) => m.DriverSchoolsPage),
    canActivate: [routeGuard, roleGuard],
    data: { rolesPermitted: ['ROLE_DRIVER'] },
  },
  {
    // aba "Contratos" do profile select (o backend devolve so os contratos do usuario logado)
    path: 'contracts',
    loadComponent: () => import('./pages/contracts/contracts').then((m) => m.Contracts),
    canActivate: [routeGuard, roleGuard],
    data: { rolesPermitted: ['ROLE_DRIVER', 'ROLE_RESPONSIBLE'] },
  },
  {
    // analise do contrato; ?chatId= faz o botao voltar pra conversa (so as partes do contrato conseguem ver)
    path: 'contract/:contractId',
    loadComponent: () => import('./pages/contract-view/contract-view').then((m) => m.ContractView),
    canActivate: [routeGuard, roleGuard],
    data: { rolesPermitted: ['ROLE_DRIVER', 'ROLE_RESPONSIBLE'] },
  },
  // qualquer url que nao bate com nada acima cai na 404 (tem que ser a ultima)
  {
    path: '**',
    component: NotFound,
  },
];
