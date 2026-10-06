import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { jwtDecode } from 'jwt-decode';
import { User } from '../models/user.model';
import { Observable } from 'rxjs';
import { tokenResponse, loginRequest, createUserRequest, verifyEmailRequest } from './dto/auth/auth.dto';

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

  login(data: loginRequest) {
    return this.http.post<tokenResponse>(`${this.apiUrl}/auth/login`, data);
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
    formData.append('phone', data.phone);

    return this.http.post(`${this.apiUrl}/driver/auth/signup`, formData, {
      responseType: 'text',
    });
  }

  createUser(data: createUserRequest) {
    return this.http.post(`${this.apiUrl}/responsible/auth/signup`, data);
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

  logout() {
    localStorage.removeItem('token');
  }
}

// new branch
