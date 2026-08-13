import { Routes } from '@angular/router';
import { Home } from './pages/home/home';
import { Login } from './pages/auth/login/login';
import { Signup } from './pages/auth/signup/signup';
import { EmailCode } from './components/email-code/email-code';
import { HomeScreen } from './pages/home-screen/home-screen';
import { AddStudent } from './components/add-student/add-student';

export const routes: Routes = [
  { path: '', component: Home },
  { path: 'login/:tipo', component: Login },
  { path: 'signup/:tipo', component: Signup },
  { path: 'email-code', component: EmailCode },
  { path: 'home-screen', component: HomeScreen },
  { path: 'add-student', component: AddStudent },
];