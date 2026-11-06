import { Injectable } from '@angular/core';
import { Page, paramsDePagina } from './dto/page.dto';
import { HttpClient, HttpContext } from '@angular/common/http';
import { catchError, firstValueFrom, forkJoin, map, Observable, of } from 'rxjs';
import {
  createStudentRequest,
  generateStudentLink,
  getMyChildrenRequest,
  studentAddressRequest,
  studentChild,
  studentPlace,
  studentResponsibleLink,
  studentResponsibleResponse,
} from './dto/student/student.dto';
import { API_URL, urlDoStorage } from './api';
import { studentAbsence, studentRoute, studentTripToday } from './dto/trip/trip.dto';
import { SKIP_ERROR_POPUP } from '../interceptor/http-context';
import { StorageService } from './storage.service';

// qualquer item com a chave da foto do aluno (my-children, filhos do chat, membros do grupo...)
type ComFoto = { photoKey?: string | null };


@Injectable({
  providedIn: 'root',
})
export class StudentService {
  private apiUrl = `${API_URL}/student`;

  constructor(
    private http: HttpClient,
    private storageService: StorageService,
  ) {}

  createStudent(data: createStudentRequest) {
    return this.http.post<studentResponsibleResponse>(`${this.apiUrl}/create-student`, data);
  }

  generateStudentLink(data: generateStudentLink) {
    return this.http.post(`${this.apiUrl}/generate-link`, data, { responseType: 'text' });
  }

  confirmCode(code: string) {
    return this.http.post(`${this.apiUrl}/confirm-link`, {
      code,
    });
  }

  getMyChildren(filters?: getMyChildrenRequest) {
    let params: any = {};

    if (filters?.relationType) {
      params.relationType = filters.relationType;
    }

    if (filters?.isAdmin !== undefined) {
      params.isAdmin = filters.isAdmin;
    }

    if (filters?.studentName) {
      params.studentName = filters.studentName;
    }

    Object.assign(params, paramsDePagina(filters));

    return this.http.get<Page<studentChild>>(`${this.apiUrl}/my-children`, { params });
  }

  // ids dos filhos em que sou o admin (so o admin troca a foto, os enderecos e os responsaveis)
  getAdminChildIds(): Observable<Set<string>> {
    return this.getMyChildren({ isAdmin: true, size: 100 }).pipe(
      map((pagina) => new Set((pagina.content ?? []).map((aluno) => aluno.id))),
      catchError(() => of(new Set<string>())),
    );
  }

  // troca photoKey por link de leitura (15 min); sem foto ou se o link falhar, photoUrl = null (a tela mostra iniciais)
  comFotos<T extends ComFoto>(itens: T[]): Observable<Array<T & { photoUrl: string | null }>> {
    if (!itens.length) return of([]);
    return forkJoin(
      itens.map((item) =>
        item.photoKey
          ? this.storageService.getDownloadUrl(item.photoKey).pipe(
              map((photoUrl) => ({ ...item, photoUrl })),
              catchError(() => of({ ...item, photoUrl: null })),
            )
          : of({ ...item, photoUrl: null }),
      ),
    );
  }

  // foto do aluno (so o admin): link de upload -> PUT direto no bucket -> grava no aluno (a antiga e apagada sozinha)
  async uploadStudentPhoto(studentId: string, file: File): Promise<string> {
    const contentType = file.type || 'image/jpeg';
    const semPopup = { context: new HttpContext().set(SKIP_ERROR_POPUP, true) };
    const upload = await firstValueFrom(
      this.http.get<{ uploadUrl: string; fileKey: string }>(`${this.apiUrl}/${studentId}/photo-upload-url`, {
        params: { fileName: file.name, contentType },
        ...semPopup,
      }),
    );

    const resposta = await fetch(urlDoStorage(upload.uploadUrl), {
      method: 'PUT',
      headers: { 'Content-Type': contentType },
      body: file,
    });
    if (!resposta.ok) {
      throw new Error(`Falha ao enviar a foto para o armazenamento (${resposta.status}).`);
    }

    const salvo = await firstValueFrom(
      this.http.put<{ photoKey: string }>(`${this.apiUrl}/${studentId}/photo`, { fileKey: upload.fileKey }, semPopup),
    );
    return salvo.photoKey;
  }

  // tira a foto (so o admin); 204
  removeStudentPhoto(studentId: string) {
    return this.http.delete<void>(`${this.apiUrl}/${studentId}/photo`);
  }

  // ---------- detalhe do filho: enderecos e responsaveis (docs/FLUXO_FRONT.md 6.3.2 e 6.3.3) ----------
  // erros sem popup global: a tela mostra 409 como aviso "Não foi possível concluir"

  private semPopup() {
    return { context: new HttpContext().set(SKIP_ERROR_POPUP, true) };
  }

  getAddresses(studentId: string) {
    return this.http.get<studentPlace[]>(`${this.apiUrl}/${studentId}/addresses`, this.semPopup());
  }

  // so o admin; 409 = o aluno ja tem esse endereco
  addAddress(studentId: string, body: studentAddressRequest) {
    return this.http.post<studentPlace>(`${this.apiUrl}/${studentId}/addresses`, body, this.semPopup());
  }

  // so o admin; 409 = ultimo endereco ou viagem rodando com ele
  removeAddress(studentId: string, studentAddressId: string) {
    return this.http.delete<void>(`${this.apiUrl}/${studentId}/addresses/${studentAddressId}`, this.semPopup());
  }

  getResponsibles(studentId: string) {
    return this.http.get<studentResponsibleLink[]>(`${this.apiUrl}/${studentId}/responsibles`, this.semPopup());
  }

  // admin remove outro, ou o proprio responsavel sai (mesma chamada com o proprio id)
  removeResponsible(studentId: string, responsibleId: string) {
    return this.http.delete<void>(`${this.apiUrl}/${studentId}/responsibles/${responsibleId}`, this.semPopup());
  }

  transferAdmin(studentId: string, responsibleId: string) {
    return this.http.put<void>(`${this.apiUrl}/${studentId}/responsibles/${responsibleId}/admin`, null, this.semPopup());
  }

  // ---------- viagem de hoje e faltas (docs/FLUXO_TRIP.md 4.8 e 4.9) ----------

  // lista vazia = a van ainda nao saiu; 403 = nao e mais responsavel (a tela avisa)
  getTodayTrips(studentId: string) {
    return this.http.get<studentTripToday[]>(`${this.apiUrl}/${studentId}/today-trips`, {
      context: new HttpContext().set(SKIP_ERROR_POPUP, true),
    });
  }

  getStudentRoutes(studentId: string) {
    return this.http.get<studentRoute[]>(`${this.apiUrl}/${studentId}/routes`, {
      context: new HttpContext().set(SKIP_ERROR_POPUP, true),
    });
  }

  // faltas de hoje em diante
  getAbsences(studentId: string) {
    return this.http.get<studentAbsence[]>(`${this.apiUrl}/${studentId}/absences`, {
      context: new HttpContext().set(SKIP_ERROR_POPUP, true),
    });
  }

  // 400/409 a tela mostra como aviso "Não foi possível concluir"
  createAbsence(studentId: string, routeId: string, date: string) {
    return this.http.post<studentAbsence>(`${this.apiUrl}/${studentId}/absences`, { routeId, date }, {
      context: new HttpContext().set(SKIP_ERROR_POPUP, true),
    });
  }

  // "Ele vai sim": so antes de a viagem do dia comecar (depois, 409)
  cancelAbsence(studentId: string, absenceId: string) {
    return this.http.delete<void>(`${this.apiUrl}/${studentId}/absences/${absenceId}`, {
      context: new HttpContext().set(SKIP_ERROR_POPUP, true),
    });
  }
}
