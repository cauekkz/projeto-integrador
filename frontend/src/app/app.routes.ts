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

export const routes: Routes = [
  { path: '', component: Home },
  { path: 'login/:tipo', component: Login },
  { path: 'signup/:tipo', component: Signup },
  { path: 'email-code', component: EmailCode },
  { path: 'home-screen', component: HomeScreen },
  { path: 'add-student', component: AddStudent },
  { path: 'driver-home', component: DriverHome },
  { path: 'driver-route', component: DriverRoute },
  { path: 'student-code', component: StudentCode },
  { path: 'dependentes', component: Dependentes },
  { path: 'chat', component: Chat },
  { path: 'profile-info/:tipo', component: ProfileInfo },
  { path: 'signup/:tipo', component: Signup }

];
