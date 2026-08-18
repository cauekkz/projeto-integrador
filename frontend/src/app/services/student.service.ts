import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';

export interface StudentResponsibleResponse {
  id: string;
  relationType: string;
  student: {
    id: string;
    name: string;
    notes: string;
    birthDate: string;
  };
  admin: boolean;
}
@Injectable({
  providedIn: 'root',
})
export class StudentService {
  private apiUrl = 'http://localhost:9090/api/student';

  constructor(private http: HttpClient) {}

  createStudent(data: { name: string; notes: string; birthDate: string; relationType: string }) {
    return this.http.post<StudentResponsibleResponse>(`${this.apiUrl}/create-student`, data);
  }

  generateStudentLink(data: { id: string; relationType: string }) {
    return this.http.post(`${this.apiUrl}/generate-link`, data, { responseType: 'text' });
  }

  confirmCode(code: string) {
    return this.http.post(`${this.apiUrl}/confirm-link`, {
      code,
    });
  }

  getMyChildren(filters?: {
    relationType?: string;
    isAdmin?: boolean;
    studentName?: string;
    page?: number;
    size?: number;
  }) {
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

    if (filters?.page !== undefined) {
      params.page = filters.page;
    }

    if (filters?.size !== undefined) {
      params.size = filters.size;
    }

    return this.http.get(`${this.apiUrl}/my-children`, { params });
  }
}
