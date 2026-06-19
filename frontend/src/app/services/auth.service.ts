import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';

@Injectable({
  providedIn: 'root',
})
export class AuthService {
  private apiUrl = 'http://localhost:9090/api';

  constructor(private http: HttpClient) {}

  login(cpf: string, password: string) {
    return this.http.post(`${this.apiUrl}/auth/login`, {
      cpf,
      passwordHash: password,
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

    return this.http.post(`${this.apiUrl}/drivers/signup`, formData);
  }

  createUser(data: {
    name: string;
    email: string;
    password: string;
    confirmPassword: string;
    cpf: string;
    phone: string;
  }) {
    return this.http.post(`${this.apiUrl}/responsible/signup`, {
      name: data.name,
      email: data.email,
      password: data.password,
      confirmPassword: data.confirmPassword,
      cpf: data.cpf,
      phone: data.phone,
    });
  }

  getUserByID(id: string) {
    return this.http.get(`${this.apiUrl}/users/${id}`);
  }

  deleteUser(id: string) {
    return this.http.delete(`${this.apiUrl}/users/${id}`);
  }
}
