import { Injectable } from '@angular/core';
import { HttpClient, HttpContext } from '@angular/common/http';
import { SKIP_ERROR_POPUP } from '../interceptor/http-context';
import {
  chatStudentResponse,
  contractDetailsResponse,
  contractProposalRequest,
  contractProposalResponse,
  contractSummaryResponse,
  myContractsRequest,
} from './dto/contract/contract.dto';
import { Page, paramsDePagina } from './dto/page.dto';
import { API_URL } from './api';

// a proposta de contrato nasce dentro do chat (docs/FLUXO_CONTRATO.md)
@Injectable({
  providedIn: 'root',
})
export class ContractService {
  private apiUrl = API_URL;

  constructor(private http: HttpClient) {}

  // filhos (como responsavel principal) do pai desse chat; so o motorista do chat consegue
  getChatStudents(chatId: string) {
    return this.http.get<chatStudentResponse[]>(`${this.apiUrl}/chats/${chatId}/students`);
  }

  // o erro e tratado na tela (mensagem por status), por isso sem popup global
  createContract(chatId: string, data: contractProposalRequest) {
    return this.http.post<contractProposalResponse>(`${this.apiUrl}/chats/${chatId}/contract`, data, {
      context: new HttpContext().set(SKIP_ERROR_POPUP, true),
    });
  }

  // detalhes da proposta (card do chat); sem popup: o card mostra o proprio erro
  getContract(contractId: string) {
    return this.http.get<contractDetailsResponse>(`${this.apiUrl}/contract/${contractId}`, {
      context: new HttpContext().set(SKIP_ERROR_POPUP, true),
    });
  }

  // contratos do usuario logado (motorista ou responsavel), paginado; ?status= filtra no backend
  getMyContracts(filtro: myContractsRequest = {}) {
    const params: Record<string, string | number> = { ...paramsDePagina(filtro) };
    if (filtro.status) params['status'] = filtro.status;
    if (filtro.studentId) params['studentId'] = filtro.studentId;
    if (filtro.driverId) params['driverId'] = filtro.driverId;
    return this.http.get<Page<contractSummaryResponse>>(`${this.apiUrl}/contract/my-contracts`, {
      params,
      context: new HttpContext().set(SKIP_ERROR_POPUP, true),
    });
  }

  // so o responsavel do contrato consegue (motorista recebe 403; ja decidido da 409); respondem 200 sem corpo
  acceptContract(chatId: string, contractId: string) {
    return this.http.put<void>(`${this.apiUrl}/chats/${chatId}/contracts/${contractId}/accept`, null, {
      context: new HttpContext().set(SKIP_ERROR_POPUP, true),
    });
  }

  rejectContract(chatId: string, contractId: string) {
    return this.http.put<void>(`${this.apiUrl}/chats/${chatId}/contracts/${contractId}/reject`, null, {
      context: new HttpContext().set(SKIP_ERROR_POPUP, true),
    });
  }
}
