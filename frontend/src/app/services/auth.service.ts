import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { jwtDecode } from 'jwt-decode';
import { User } from '../models/user.model';
import { Observable } from 'rxjs';
import {
  tokenResponse,
  loginRequest,
  createUserRequest,
  verifyEmailRequest,
  updateUserRequest,
  updatePasswordRequest,
} from './dto/auth/auth.dto';
import { limparMascara } from '../shared/utils';

@Injectable({
  providedIn: 'root',
})
// por enquanto aq uma parte, dps mudo
export class AuthService {
  getUserIdFromToken(): string | null {
    const token = localStorage.getItem('token');
    if (!token) return null;

    try {
      const decoded: any = jwtDecode(token);
      return decoded.id || decoded.sub || null;
    } catch (error) {
      console.error('Erro ao decodificar o token:', error);
      return null;
    }
  }

  // o backend manda as roles na claim "roles" (lista), ex: ["ROLE_DRIVER"]
  getRolesFromToken(): string[] {
    const decoded = this.decodeToken();
    if (!decoded) return [];

    if (Array.isArray(decoded.roles)) return decoded.roles;
    if (typeof decoded.role === 'string') return [decoded.role];
    return [];
  }

  // token existe, é decodificável e não expirou (exp é opcional, o backend ainda não manda)
  isAuthenticated(): boolean {
    const decoded = this.decodeToken();
    if (!decoded) return false;

    if (typeof decoded.exp === 'number' && decoded.exp * 1000 <= Date.now()) {
      this.logout();
      return false;
    }

    return !!(decoded.id || decoded.sub);
  }

  // rota inicial de acordo com a role do usuário logado, null se não tiver role conhecida
  getHomeRoute(): string | null {
    const roles = this.getRolesFromToken();
    if (roles.includes('ROLE_DRIVER')) return '/driver-home';
    if (roles.includes('ROLE_RESPONSIBLE') || roles.includes('ROLE_ADMIN')) return '/responsible-home';
    return null;
  }

  private decodeToken(): any | null {
    const token = localStorage.getItem('token');
    if (!token) return null;

    try {
      return jwtDecode(token);
    } catch {
      return null;
    }
  }

  private apiUrl = 'http://localhost:9090/api';

  constructor(private http: HttpClient) {}

  login(data: loginRequest) {
    const payload: loginRequest = {
      ...data,
      cpf: limparMascara(data.cpf),
    };
    return this.http.post<tokenResponse>(`${this.apiUrl}/auth/login`, payload);
  }

  createDriver(data: {
    documentPdf: File;
    name: string;
    email: string;
    password: string;
    confirmPassword: string;
    phone: string;
  }) {
    const formData = new FormData();

    formData.append('documentPdf', data.documentPdf);
    formData.append('name', data.name);
    formData.append('email', data.email);
    formData.append('password', data.password);
    formData.append('confirmPassword', data.confirmPassword);
    formData.append('phone', limparMascara(data.phone));

    return this.http.post(`${this.apiUrl}/driver/auth/signup`, formData, {
      responseType: 'text',
    });
  }

  createUser(data: createUserRequest) {
    const payload: createUserRequest = {
      ...data,
      cpf: limparMascara(data.cpf),
      phone: limparMascara(data.phone),
    };
    return this.http.post(`${this.apiUrl}/responsible/auth/signup`, payload);
  }

  getUserByID(id: string): Observable<User> {
    return this.http.get<User>(`${this.apiUrl}/users/${id}`);
  }

  deleteUser(id: string) {
    return this.http.delete(`${this.apiUrl}/users/${id}`);
  }

  verifyCNH(data: { documentPDF: File }) {
    const formData = new FormData();
    formData.append('documentPdf', data.documentPDF);

    return this.http.post(`${this.apiUrl}/driver/verifyCNH`, formData);
  }

  sendCode(email: string) {
    return this.http.post(
      `${this.apiUrl}/auth/send-verification-code?email=${encodeURIComponent(email)}`,
      {},
    );
  }

  verifyEmail(data: verifyEmailRequest) {
    return this.http.post(`${this.apiUrl}/auth/verify-email`, data, { responseType: 'text' });
  }

  sendCodeToUpdateUser() {
    return this.http.post(`${this.apiUrl}/users/send-code-updateUser`, {});
  }

  updateUser(data: updateUserRequest) {
    const payload: updateUserRequest = {
      ...data,
      phone: limparMascara(data.phone),
    };

    return this.http.put(`${this.apiUrl}/users/update-user`, payload);
  }

  updatePassword(data: updatePasswordRequest) {
    return this.http.put(`${this.apiUrl}/users/forgot-password`, data, {
      responseType: 'text',
    });
  }

  logout() {
    localStorage.removeItem('token');
  }
}

// new branch
