import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { API_URL } from './api';

// GET /api/terms e publico (o cadastro mostra antes da conta existir): versao atual + texto em markdown
export interface Terms {
  version: string;
  content: string;
}

@Injectable({
  providedIn: 'root',
})
export class TermsService {
  constructor(private http: HttpClient) {}

  getCurrent(): Observable<Terms> {
    return this.http.get<Terms>(`${API_URL}/terms`);
  }

  // usuario logado aceita a versao atual (204); 400 se a versao nao for a atual
  accept(version: string): Observable<void> {
    return this.http.post<void>(`${API_URL}/terms/accept`, { version });
  }
}
