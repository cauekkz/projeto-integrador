import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpContext } from '@angular/common/http';
import { API_URL } from './api';
import { SKIP_ERROR_POPUP } from '../interceptor/http-context';
import { driverGroup, groupNoticeResponse } from './dto/group/group.dto';

// grupos do motorista (docs/FLUXO_FRONT.md 6.2.8). Erros sem popup global: 409/403 a tela mostra como no Figma
@Injectable({ providedIn: 'root' })
export class GroupService {
  private http = inject(HttpClient);
  private url = `${API_URL}/driver/groups`;
  private semPopup = { context: new HttpContext().set(SKIP_ERROR_POPUP, true) };

  getGroups() {
    return this.http.get<driverGroup[]>(this.url, this.semPopup);
  }

  // 409 = ja tem um grupo com esse nome
  createGroup(name: string) {
    return this.http.post<driverGroup>(this.url, { name }, this.semPopup);
  }

  renameGroup(groupId: string, name: string) {
    return this.http.put<driverGroup>(`${this.url}/${groupId}`, { name }, this.semPopup);
  }

  deleteGroup(groupId: string) {
    return this.http.delete<void>(`${this.url}/${groupId}`, this.semPopup);
  }

  // 403 = sem contrato ativo com o aluno; 409 = ja esta no grupo
  addStudent(groupId: string, studentId: string) {
    return this.http.post<driverGroup>(`${this.url}/${groupId}/students/${studentId}`, null, this.semPopup);
  }

  removeStudent(groupId: string, studentId: string) {
    return this.http.delete<driverGroup>(`${this.url}/${groupId}/students/${studentId}`, this.semPopup);
  }

  // "nao vou buscar hoje"; 409 = grupo sem alunos
  notify(groupId: string, reason: string) {
    return this.http.post<groupNoticeResponse>(`${this.url}/${groupId}/notice`, reason ? { reason } : {}, this.semPopup);
  }
}
