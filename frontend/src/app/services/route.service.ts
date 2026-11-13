import { Injectable } from '@angular/core';
import { HttpClient, HttpContext } from '@angular/common/http';
import { SKIP_ERROR_POPUP } from '../interceptor/http-context';
import { API_URL } from './api';
import { Page, PageRequest, paramsDePagina } from './dto/page.dto';
import {
  addStudentRequest,
  routeEdit,
  routePending,
  routeResponse,
  routeStop,
  routeWithStops,
  stopOptions,
  WeekDay,
} from './dto/route/route.dto';

// montar a rota do motorista (docs/FLUXO_ROTA.md). Toda alteracao devolve { stops, pending }
// os erros 400/403/409 vao pro popup global com o texto do backend, salvo onde a tela trata
@Injectable({
  providedIn: 'root',
})
export class RouteService {
  private apiUrl = `${API_URL}/route`;

  constructor(private http: HttpClient) {}

  // active=true (padrao) = so as rotas em que ele esta hoje; false inclui as que ele deixou
  getMyRoutes(filtro: { active?: boolean; name?: string } & PageRequest = {}) {
    const params: Record<string, string | number | boolean> = { ...paramsDePagina(filtro) };
    if (filtro.active !== undefined) params['active'] = filtro.active;
    if (filtro.name) params['name'] = filtro.name;
    return this.http.get<Page<routeWithStops>>(`${this.apiUrl}/get-all-routes-and-stops`, {
      params,
      context: new HttpContext().set(SKIP_ERROR_POPUP, true),
    });
  }

  // 201 { id, name, driverName, vehicle: null }
  createRoute(name: string) {
    return this.http.post<routeResponse>(`${this.apiUrl}/create-route`, { name });
  }

  // a tela de edicao mostra o proprio erro de carregar
  getRoute(routeId: string) {
    return this.http.get<routeResponse>(`${this.apiUrl}/${routeId}`, {
      context: new HttpContext().set(SKIP_ERROR_POPUP, true),
    });
  }

  getStops(routeId: string) {
    return this.http.get<routeStop[]>(`${this.apiUrl}/${routeId}/stops`, {
      context: new HttpContext().set(SKIP_ERROR_POPUP, true),
    });
  }

  // [] = rota pronta pra iniciar a viagem
  getPending(routeId: string) {
    return this.http.get<routePending[]>(`${this.apiUrl}/${routeId}/pending`, {
      context: new HttpContext().set(SKIP_ERROR_POPUP, true),
    });
  }

  // alunos com contrato ACTIVE e os enderecos de cada um
  getStopOptions() {
    return this.http.get<stopOptions>(`${this.apiUrl}/stop-options`, {
      context: new HttpContext().set(SKIP_ERROR_POPUP, true),
    });
  }

  addStudent(routeId: string, studentId: string, pedido: addStudentRequest) {
    return this.http.post<routeEdit>(`${this.apiUrl}/${routeId}/add-student/${studentId}`, pedido);
  }

  // troca o ponto com o que esta na posicao pedida
  swapOrder(routeStopId: string, orderIndex: number) {
    return this.http.put<routeEdit>(`${this.apiUrl}/stop/${routeStopId}/swap-order`, { orderIndex });
  }

  moveStudent(studentStopId: string, routeStopId: string) {
    return this.http.put<routeEdit>(`${this.apiUrl}/student-stop/${studentStopId}/move`, { routeStopId });
  }

  // vazio = todos os dias
  updateWeekdays(studentStopId: string, weekdays: WeekDay[]) {
    return this.http.put<routeEdit>(`${this.apiUrl}/student-stop/${studentStopId}/weekdays`, { weekdays });
  }

  removeStudentStop(studentStopId: string) {
    return this.http.delete<routeEdit>(`${this.apiUrl}/student-stop/${studentStopId}`);
  }

  // 409 se algum aluno da escola ainda embarca ou desce no ponto
  removeSchoolStop(schoolStopId: string) {
    return this.http.delete<routeEdit>(`${this.apiUrl}/school-stop/${schoolStopId}`);
  }

  // 409 se o ponto ainda tem aluno ou escola (a tela avisa antes de chamar)
  removeStop(routeStopId: string) {
    return this.http.delete<routeEdit>(`${this.apiUrl}/stop/${routeStopId}`);
  }

  setVehicle(routeId: string, vehicleId: string) {
    return this.http.put<routeResponse>(`${this.apiUrl}/${routeId}/set-vehicle/${vehicleId}`, null);
  }
}
