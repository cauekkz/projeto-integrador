import { Routes } from '@angular/router';
import { Home } from './pages/common/home/home';
import { Login } from './pages/common/auth/login/login';
import { Signup } from './pages/common/auth/signup/signup';
import { EmailCode } from './components/common/login-signup/email-code/email-code';
import { HomeScreen } from './pages/responsible/responsible-home/home-screen';
import { AddStudent } from './components/student/add-student/add-student';
import { DriverRoute } from './pages/driver/driver-route/driver-route';
import { StudentCode } from './components/student/student-code/student-code';
import { Dependentes } from './components/student/dependentes/dependentes';
import { Chat } from './pages/common/chat/chat';
import { ProfileInfo } from './pages/common/profile-info/profile-info';
import { ProfileSelect } from './components/common/profile-select/profile-select';
import { ChatDetails } from './components/common/chat/chat-details/chat-details';
import { routeGuard, roleGuard, guestGuard } from './interceptor/route.interceptor';
import { DriverCode } from './components/responsible/driver-code/driver-code';
import { ChatListComponent } from './components/common/chat/chat-list/chat-list';
import { NotFound } from './pages/common/not-found/not-found';
import { DriverLinkCode } from './pages/driver/driver-link-code/driver-link-code';

export const routes: Routes = [
  // guestGuard: quem ja esta logado vai direto pra home da sua role
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
    // home do motorista: a lista das rotas pra criar e montar (casinha do rodape; o login cai aqui)
    path: 'driver-home',
    loadComponent: () => import('./pages/driver/driver-routes/driver-routes').then((m) => m.DriverRoutes),
    canActivate: [routeGuard, roleGuard],
    data: { rolesPermitted: ['ROLE_DRIVER'] },
  },
  {
    // a viagem precisa da rota: sem ela, a aba do meio mostra qual escolher
    path: 'driver-route',
    redirectTo: 'driver-routes',
    pathMatch: 'full',
  },
  {
    // viagem do dia da rota: iniciar, continuar, em viagem e finalizar
    path: 'driver-route/:routeId',
    component: DriverRoute,
    canActivate: [routeGuard, roleGuard],
    data: { rolesPermitted: ['ROLE_DRIVER'] },
  },
  {
    // aba do meio do rodape do motorista: "Qual rota você vai fazer agora?" (iniciar a viagem)
    path: 'driver-routes',
    loadComponent: () => import('./pages/driver/driver-home/driver-home').then((m) => m.DriverHome),
    canActivate: [routeGuard, roleGuard],
    data: { rolesPermitted: ['ROLE_DRIVER'] },
  },
  {
    // montar a rota: pontos, alunos, ordem, pendencias e van padrao
    path: 'driver-routes/:routeId',
    loadComponent: () => import('./pages/driver/driver-route-editor/driver-route-editor').then((m) => m.DriverRouteEditor),
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
    loadComponent: () => import('./pages/driver/driver-contract/driver-contract-page').then((m) => m.DriverContractPage),
    canActivate: [routeGuard, roleGuard],
    data: { rolesPermitted: ['ROLE_DRIVER'] },
  },
  {
    // aba "Adicionar escola" do profile select
    path: 'driver-schools',
    loadComponent: () => import('./pages/driver/driver-schools/driver-schools-page').then((m) => m.DriverSchoolsPage),
    canActivate: [routeGuard, roleGuard],
    data: { rolesPermitted: ['ROLE_DRIVER'] },
  },
  {
    // "Minhas vans" do profile select: vans pelo CRLV digital
    path: 'driver-vehicles',
    loadComponent: () => import('./pages/driver/driver-vehicles/driver-vehicles').then((m) => m.DriverVehicles),
    canActivate: [routeGuard, roleGuard],
    data: { rolesPermitted: ['ROLE_DRIVER'] },
  },
  {
    // "Chave Pix" do profile select: os responsaveis veem nas parcelas
    path: 'driver-pix',
    loadComponent: () => import('./pages/driver/driver-pix/driver-pix').then((m) => m.DriverPix),
    canActivate: [routeGuard, roleGuard],
    data: { rolesPermitted: ['ROLE_DRIVER'] },
  },
  {
    // "Parcelas" do profile select e do detalhe do contrato (?contractId=); os dois perfis, cada um no seu modo
    path: 'payments',
    loadComponent: () => import('./pages/common/payments/payments').then((m) => m.Payments),
    canActivate: [routeGuard, roleGuard],
    data: { rolesPermitted: ['ROLE_DRIVER', 'ROLE_RESPONSIBLE'] },
  },
  {
    // aba "Contratos" do profile select (o backend devolve so os contratos do usuario logado)
    path: 'contracts',
    loadComponent: () => import('./pages/common/contracts/contracts').then((m) => m.Contracts),
    canActivate: [routeGuard, roleGuard],
    data: { rolesPermitted: ['ROLE_DRIVER', 'ROLE_RESPONSIBLE'] },
  },
  {
    // pasta de um filho (tela de Contratos): contratos e parcelas dele
    path: 'contracts/aluno/:studentId',
    loadComponent: () => import('./pages/responsible/pasta-dependente/pasta-dependente').then((m) => m.PastaDependente),
    canActivate: [routeGuard, roleGuard],
    data: { rolesPermitted: ['ROLE_DRIVER', 'ROLE_RESPONSIBLE'] },
  },
  {
    // motorista: pasta de um responsavel, com as pastas dos dependentes dele
    path: 'contracts/responsavel/:responsibleId',
    loadComponent: () => import('./pages/common/contracts/contracts').then((m) => m.Contracts),
    canActivate: [routeGuard, roleGuard],
    data: { rolesPermitted: ['ROLE_DRIVER'] },
  },
  {
    // analise do contrato; ?chatId= faz o botao voltar pra conversa (so as partes do contrato conseguem ver)
    path: 'contract/:contractId',
    loadComponent: () => import('./pages/common/contract-view/contract-view').then((m) => m.ContractView),
    canActivate: [routeGuard, roleGuard],
    data: { rolesPermitted: ['ROLE_DRIVER', 'ROLE_RESPONSIBLE'] },
  },
  {
    // sem a viagem nao tem o que acompanhar: a home mostra os filhos e o "Acompanhar"
    // aba do meio sem viagem rodando: tela "Não há uma rota para acompanhar"
    path: 'responsible-route',
    pathMatch: 'full',
    loadComponent: () => import('./pages/responsible/responsible-route/sem-viagem').then((m) => m.SemViagem),
    canActivate: [routeGuard, roleGuard],
    data: { rolesPermitted: ['ROLE_RESPONSIBLE'] },
  },
  {
    // grupos do motorista ("avisar o grupo")
    path: 'driver-groups',
    loadComponent: () => import('./pages/driver/driver-groups/driver-groups').then((m) => m.DriverGroups),
    canActivate: [routeGuard, roleGuard],
    data: { rolesPermitted: ['ROLE_DRIVER'] },
  },
  {
    path: 'driver-groups/:groupId',
    loadComponent: () => import('./pages/driver/driver-group/driver-group').then((m) => m.DriverGroup),
    canActivate: [routeGuard, roleGuard],
    data: { rolesPermitted: ['ROLE_DRIVER'] },
  },
  {
    // historico de viagens (motorista: das viagens dele; responsavel: dos filhos)
    path: 'trip-history',
    loadComponent: () => import('./pages/common/trip-history/trip-history').then((m) => m.TripHistory),
    canActivate: [routeGuard, roleGuard],
    data: { rolesPermitted: ['ROLE_DRIVER', 'ROLE_RESPONSIBLE'] },
  },
  {
    path: 'trip-history/:tripId/ocorrencias',
    loadComponent: () => import('./pages/common/trip-occurrences/trip-occurrences').then((m) => m.TripOccurrences),
    canActivate: [routeGuard, roleGuard],
    data: { rolesPermitted: ['ROLE_DRIVER', 'ROLE_RESPONSIBLE'] },
  },
  {
    // perfil do motorista visto pelo responsavel (do chat), com avaliacao
    path: 'driver-profile/:driverId',
    loadComponent: () => import('./pages/responsible/driver-profile/driver-profile').then((m) => m.DriverProfile),
    canActivate: [routeGuard, roleGuard],
    data: { rolesPermitted: ['ROLE_RESPONSIBLE'] },
  },
  {
    // perfil do responsavel visto pelo motorista (do chat ou do contrato)
    path: 'responsible-profile/:responsibleId',
    loadComponent: () => import('./pages/driver/responsible-profile/responsible-profile').then((m) => m.ResponsibleProfile),
    canActivate: [routeGuard, roleGuard],
    data: { rolesPermitted: ['ROLE_DRIVER'] },
  },
  {
    // filho em detalhe: foto, enderecos, responsaveis, rotas e faltas
    path: 'student/:studentId',
    loadComponent: () => import('./pages/student/student-detail/student-detail').then((m) => m.StudentDetail),
    canActivate: [routeGuard, roleGuard],
    data: { rolesPermitted: ['ROLE_RESPONSIBLE'] },
  },
  {
    // acompanhar a viagem do filho (?aluno= diz qual filho, se tiver mais de um na viagem)
    path: 'responsible-route/:tripId',
    loadComponent: () => import('./pages/responsible/responsible-route/responsible-route').then((m) => m.ResponsibleRoute),
    canActivate: [routeGuard, roleGuard],
    data: { rolesPermitted: ['ROLE_RESPONSIBLE'] },
  },
  {
    // aba de notificacoes (clicar no sino): avisos do backend + mensagens novas das conversas
    path: 'notifications',
    loadComponent: () => import('./pages/common/notifications/notifications').then((m) => m.Notifications),
    canActivate: [routeGuard, roleGuard],
    data: { rolesPermitted: ['ROLE_DRIVER', 'ROLE_RESPONSIBLE'] },
  },
  {
    // termos atualizados: o interceptor traz pra ca quando o backend responde 403 TERMS_NOT_ACCEPTED
    path: 'terms',
    loadComponent: () => import('./pages/common/terms/terms').then((m) => m.TermsPage),
    canActivate: [routeGuard],
  },
  {
    // "Privacidade e dados" do profile select: termos (leitura), exportar meus dados e excluir a conta
    path: 'privacy',
    loadComponent: () => import('./pages/common/privacy/privacy').then((m) => m.Privacy),
    canActivate: [routeGuard, roleGuard],
    data: { rolesPermitted: ['ROLE_DRIVER', 'ROLE_RESPONSIBLE'] },
  },
  // qualquer url que nao bate com nada acima cai na 404 (tem que ser a ultima)
  {
    path: '**',
    component: NotFound,
  },
];
