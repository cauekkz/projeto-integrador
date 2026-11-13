import { Injectable } from '@angular/core';
import { HttpClient, HttpContext } from '@angular/common/http';
import { SKIP_ERROR_POPUP } from '../interceptor/http-context';
import { API_URL } from './api';
import { vehicleResponse } from './dto/vehicle/vehicle.dto';

// vans do motorista: entram pelo CRLV digital (PDF), o backend le os dados do documento
@Injectable({
  providedIn: 'root',
})
export class VehicleService {
  private apiUrl = `${API_URL}/vehicle`;

  constructor(private http: HttpClient) {}

  // a tela mostra o proprio erro de carregar (com "tentar de novo")
  getMyVehicles() {
    return this.http.get<vehicleResponse[]>(`${this.apiUrl}/my-vehicles`, {
      context: new HttpContext().set(SKIP_ERROR_POPUP, true),
    });
  }

  // multipart documentPdf -> 201 com a van; 422 (CRLV invalido, licenciamento vencido) vai pro popup global
  addVehicle(crlv: File) {
    const form = new FormData();
    form.append('documentPdf', crlv);
    return this.http.post<vehicleResponse>(`${this.apiUrl}/add-vehicle`, form);
  }

  // para de usar (sai da lista do motorista); 204
  unlinkVehicle(vehicleId: string) {
    return this.http.put<void>(`${this.apiUrl}/${vehicleId}/unlink`, null);
  }
}
