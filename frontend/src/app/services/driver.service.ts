import { Injectable } from '@angular/core';
import { HttpClient, HttpContext } from '@angular/common/http';
import { SKIP_ERROR_POPUP } from '../interceptor/http-context';
import { API_URL } from './api';
import { pixKey, PixKeyType } from './dto/driver/pix.dto';

// chave Pix do motorista: os responsaveis veem nas parcelas pra copiar e pagar (o app nao recebe dinheiro)
@Injectable({
  providedIn: 'root',
})
export class DriverService {
  private apiUrl = `${API_URL}/driver`;

  constructor(private http: HttpClient) {}

  getPixKey() {
    return this.http.get<pixKey>(`${this.apiUrl}/pix-key`, {
      context: new HttpContext().set(SKIP_ERROR_POPUP, true),
    });
  }

  // 400 ("Chave Pix inválida para o tipo e-mail") vai pro popup global com a mensagem do backend
  setPixKey(type: PixKeyType, key: string) {
    return this.http.put<pixKey>(`${this.apiUrl}/pix-key`, { type, key });
  }

  // 204
  removePixKey() {
    return this.http.delete<void>(`${this.apiUrl}/pix-key`);
  }
}
