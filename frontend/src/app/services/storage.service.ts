import { Injectable } from '@angular/core';
import { HttpClient, HttpContext } from '@angular/common/http';
import { finalize, map, Observable, of, shareReplay } from 'rxjs';
import { SKIP_ERROR_POPUP } from '../interceptor/http-context';
import { API_URL, urlDoStorage } from './api';

// o bucket e privado: o backend guarda so a chave (fileKey) e gera um link de leitura que vale 15 min
@Injectable({
  providedIn: 'root',
})
export class StorageService {
  private apiUrl = `${API_URL}/storage`;

  constructor(private http: HttpClient) {}

  // links ja assinados, por fileKey: reaproveitar o MESMO link faz o navegador usar a foto do cache em vez de baixar
  // de novo a cada tela. Guarda por 12 min (o link vale 15) e junta pedidos iguais que chegam ao mesmo tempo
  private static readonly VALIDADE_MS = 12 * 60 * 1000;
  private links = new Map<string, { url: string; expira: number }>();
  private pedindo = new Map<string, Observable<string>>();

  // sem popup: imagem que nao carrega cai no placeholder da tela
  getDownloadUrl(fileKey: string): Observable<string> {
    const guardado = this.links.get(fileKey);
    if (guardado && guardado.expira > Date.now()) return of(guardado.url);

    const emAndamento = this.pedindo.get(fileKey);
    if (emAndamento) return emAndamento;

    const pedido = this.http
      .get<{ downloadUrl: string; fileKey: string }>(`${this.apiUrl}/download-url`, {
        params: { fileKey },
        context: new HttpContext().set(SKIP_ERROR_POPUP, true),
      })
      .pipe(
        map((response) => {
          const url = urlDoStorage(response.downloadUrl);
          this.links.set(fileKey, { url, expira: Date.now() + StorageService.VALIDADE_MS });
          return url;
        }),
        finalize(() => this.pedindo.delete(fileKey)),
        shareReplay(1),
      );
    this.pedindo.set(fileKey, pedido);
    return pedido;
  }
}
