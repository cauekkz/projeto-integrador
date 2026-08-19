import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { jwtDecode } from 'jwt-decode';
import { User } from '../models/user.model';
import { Observable } from 'rxjs';

interface TokenResponse {
  token: string;
  expiresIn: number;
}
@Injectable({
  providedIn: 'root',
})
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

  private apiUrl = 'http://localhost:9090/api';

  constructor(private http: HttpClient) {}

  login(cpf: string, passwordHash: string) {
    return this.http.post<TokenResponse>(`${this.apiUrl}/auth/login`, {
      cpf,
      passwordHash,
    });
  }

  createDriver(data: {
    /* driverType: string; */
    documentPdf: File;
    name: string;
    email: string;
    password: string;
    confirmPassword: string;
    phone: string;
  }) {
    const formData = new FormData();

    // esse drivertype ta inutil por enquanto
    // no signup-driver to enviando nd util do drivertype
    /* formData.append('driverType', data.driverType); */
    formData.append('documentPdf', data.documentPdf);
    formData.append('name', data.name);
    formData.append('email', data.email);
    formData.append('password', data.password);
    formData.append('confirmPassword', data.confirmPassword);
    formData.append('phone', data.phone);

    return this.http.post(`${this.apiUrl}/driver/signup`, formData);
  }

  createUser(data: {
    name: string;
    email: string;
    password: string;
    confirmPassword: string;
    cpf: string;
    phone: string;
  }) {
    return this.http.post(`${this.apiUrl}/responsible/auth/signup`, {
      name: data.name,
      email: data.email,
      password: data.password,
      confirmPassword: data.confirmPassword,
      cpf: data.cpf,
      phone: data.phone,
    });
  }

  getUserByID(id: string): Observable<User> {
    return this.http.get<User>(`${this.apiUrl}/users/${id}`);
  }

  deleteUser(id: string) {
    return this.http.delete(`${this.apiUrl}/users/${id}`);
  }

  // dps ve se isso vai ser usado
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

  verifyEmail(data: { email: string; code: string }) {
    return this.http.post(`${this.apiUrl}/auth/verify-email`, data, { responseType: 'text' });
  }
}
