import { Injectable } from '@angular/core';
import { Page, paramsDePagina } from './dto/page.dto';
import { HttpClient, HttpContext } from '@angular/common/http';
import { catchError, firstValueFrom, forkJoin, map, Observable, of } from 'rxjs';
import {
  createStudentRequest,
  generateStudentLink,
  getMyChildrenRequest,
  studentChild,
  studentResponsibleResponse,
} from './dto/student/student.dto';
import { API_URL, urlDoStorage } from './api';
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
}
