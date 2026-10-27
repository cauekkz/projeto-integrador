import { Injectable } from '@angular/core';
import { HttpClient, HttpContext } from '@angular/common/http';
import { map, Observable } from 'rxjs';
import { SKIP_ERROR_POPUP } from '../interceptor/http-context';

// o bucket e privado: o backend guarda so a chave (fileKey) e gera um link de leitura que vale 15 min
@Injectable({
  providedIn: 'root',
})
export class StorageService {
  private apiUrl = 'http://localhost:9090/api/storage';

  constructor(private http: HttpClient) {}

  // sem popup: imagem que nao carrega cai no placeholder da tela
  getDownloadUrl(fileKey: string): Observable<string> {
    return this.http
      .get<{ downloadUrl: string; fileKey: string }>(`${this.apiUrl}/download-url`, {
        params: { fileKey },
        context: new HttpContext().set(SKIP_ERROR_POPUP, true),
      })
      .pipe(map((response) => response.downloadUrl));
  }
}
