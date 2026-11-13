import { Injectable } from '@angular/core';
import { HttpClient, HttpContext } from '@angular/common/http';
import { SKIP_ERROR_POPUP } from '../interceptor/http-context';
import { API_URL } from './api';
import { Page, paramsDePagina } from './dto/page.dto';
import { PaymentMethod, paymentResponse, paymentsRequest } from './dto/payment/payment.dto';

// parcelas dos contratos: o motorista da baixa ou dispensa; o responsavel so ve e copia a chave Pix
@Injectable({
  providedIn: 'root',
})
export class PaymentService {
  private apiUrl = `${API_URL}/payments`;

  constructor(private http: HttpClient) {}

  // a tela mostra o proprio erro de carregar
  getPayments(filtro: paymentsRequest = {}) {
    const params: Record<string, string | number> = { ...paramsDePagina(filtro) };
    for (const campo of ['status', 'studentId', 'driverId', 'responsibleId', 'contractId', 'from', 'to'] as const) {
      const valor = filtro[campo];
      if (valor) params[campo] = valor;
    }
    return this.http.get<Page<paymentResponse>>(this.apiUrl, {
      params,
      context: new HttpContext().set(SKIP_ERROR_POPUP, true),
    });
  }

  // so o motorista; 409 (ja paga ou dispensada) vai pro popup global com a mensagem do backend
  markPaid(paymentId: string, method: PaymentMethod) {
    return this.http.put<paymentResponse>(`${this.apiUrl}/${paymentId}/paid`, { method });
  }

  cancel(paymentId: string) {
    return this.http.put<paymentResponse>(`${this.apiUrl}/${paymentId}/cancel`, null);
  }
}
