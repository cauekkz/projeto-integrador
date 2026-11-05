import { Injectable } from '@angular/core';
import { Page, paramsDePagina } from './dto/page.dto';
import { HttpClient } from '@angular/common/http';
import {
  createStudentRequest,
  generateStudentLink,
  getMyChildrenRequest,
  studentResponsibleResponse,
} from './dto/student/student.dto';
import { API_URL } from './api';


@Injectable({
  providedIn: 'root',
})
export class StudentService {
  private apiUrl = `${API_URL}/student`;

  constructor(private http: HttpClient) {}

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

    // os campos do aluno ganham tipo na etapa da foto do aluno (photoKey)
    return this.http.get<Page<any>>(`${this.apiUrl}/my-children`, { params });
  }
}
