import { Injectable } from '@angular/core';
import { HttpClient, HttpContext } from '@angular/common/http';
import { SKIP_ERROR_POPUP } from '../interceptor/http-context';
import { limparMascara } from '../shared/utils';
import { schoolRequest, schoolResponse } from './dto/school/school.dto';

// escolas que o motorista atende (o motorista sai do jwt)
@Injectable({
  providedIn: 'root',
})
export class SchoolService {
  private apiUrl = 'http://localhost:9090/api/school';

  constructor(private http: HttpClient) {}

  getMySchools() {
    return this.http.get<schoolResponse[]>(`${this.apiUrl}/my-schools`);
  }

  // escola com mesmo nome e rua ja cadastrada: o backend so vincula o motorista (409 se ja estiver vinculado)
  // o erro e tratado na tela, por isso sem popup global
  addSchool(data: schoolRequest) {
    const payload: schoolRequest = {
      ...data,
      phone: limparMascara(data.phone),
      addressRequestDTO: { ...data.addressRequestDTO, zipCode: limparMascara(data.addressRequestDTO.zipCode) },
    };
    return this.http.post(`${this.apiUrl}/add-school`, payload, {
      context: new HttpContext().set(SKIP_ERROR_POPUP, true),
    });
  }
}
