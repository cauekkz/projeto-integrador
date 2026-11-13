import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpContext } from '@angular/common/http';
import { API_URL } from './api';
import { SKIP_ERROR_POPUP } from '../interceptor/http-context';
import { driverProfile, ratingRequest, ratingResponse, responsibleProfile } from './dto/profile/profile.dto';

// perfis vistos pelo outro lado (docs/FLUXO_FRONT.md 6.3.4 e 6.3.9). Erros sem popup global: a tela mostra o do Figma
@Injectable({ providedIn: 'root' })
export class ProfileService {
  private http = inject(HttpClient);
  private semPopup = { context: new HttpContext().set(SKIP_ERROR_POPUP, true) };

  // 403 = sem conversa nem contrato com ele
  getDriverProfile(driverId: string) {
    return this.http.get<driverProfile>(`${API_URL}/driver/${driverId}/profile`, this.semPopup);
  }

  // cria ou troca a minha nota; 403 = nunca teve contrato ativo com ele
  rateDriver(driverId: string, body: ratingRequest) {
    return this.http.put<ratingResponse>(`${API_URL}/driver/${driverId}/rating`, body, this.semPopup);
  }

  deleteRating(driverId: string) {
    return this.http.delete<void>(`${API_URL}/driver/${driverId}/rating`, this.semPopup);
  }

  // o motorista ve um responsavel, ou o responsavel ve o proprio (situacao geral)
  getResponsibleProfile(responsibleId: string) {
    return this.http.get<responsibleProfile>(`${API_URL}/responsible/${responsibleId}/profile`, this.semPopup);
  }
}
