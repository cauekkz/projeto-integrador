import { Injectable } from '@angular/core';
import { HttpClient, HttpContext } from '@angular/common/http';
import { jwtDecode } from 'jwt-decode';
import { UserMe } from '../models/user.model';
import { catchError, finalize, firstValueFrom, map, Observable, of, shareReplay, switchMap, tap, throwError } from 'rxjs';
import {
  tokenResponse,
  loginRequest,
  createUserRequest,
  verifyEmailRequest,
  updateUserRequest,
  forgotPasswordRequest,
} from './dto/auth/auth.dto';
import { limparMascara } from '../shared/utils';
import { SKIP_ERROR_POPUP } from '../interceptor/http-context';
import { StorageService } from './storage.service';
import { API_URL, urlDoStorage } from './api';

const TOKEN_KEY = 'token';
const REFRESH_TOKEN_KEY = 'refreshToken';
// renova um pouco antes de vencer pra requisicao nao chegar no backend com o token ja expirado
const MARGEM_EXPIRACAO_MS = 10_000;

@Injectable({
  providedIn: 'root',
})
export class AuthService {
  private apiUrl = API_URL;

  // varias chamadas podem dar 401 juntas: todas esperam o mesmo refresh
  private refreshEmAndamento$: Observable<string> | null = null;

  constructor(
    private http: HttpClient,
    private storageService: StorageService,
  ) {}

  // ---------- tokens ----------

  getToken(): string | null {
    return localStorage.getItem(TOKEN_KEY);
  }

  getRefreshToken(): string | null {
    return localStorage.getItem(REFRESH_TOKEN_KEY);
  }

  saveTokens(response: tokenResponse) {
    localStorage.setItem(TOKEN_KEY, response.token);
    localStorage.setItem(REFRESH_TOKEN_KEY, response.refreshToken);
  }

  clearTokens() {
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(REFRESH_TOKEN_KEY);
  }

  hasSession(): boolean {
    return !!this.getToken() || !!this.getRefreshToken();
  }

  isTokenExpired(token: string | null = this.getToken()): boolean {
    if (!token) return true;
    try {
      const { exp } = jwtDecode<{ exp?: number }>(token);
      return typeof exp === 'number' && exp * 1000 - MARGEM_EXPIRACAO_MS <= Date.now();
    } catch {
      return true;
    }
  }

  // troca o refreshToken por um par novo (os dois mudam); o antigo deixa de valer no backend
  refreshTokens(): Observable<string> {
    if (this.refreshEmAndamento$) {
      return this.refreshEmAndamento$;
    }

    const refreshToken = this.getRefreshToken();
    if (!refreshToken) {
      return throwError(() => new Error('Sem refreshToken salvo.'));
    }

    this.refreshEmAndamento$ = this.http
      .post<tokenResponse>(
        `${this.apiUrl}/auth/refresh`,
        { refreshToken },
        { context: new HttpContext().set(SKIP_ERROR_POPUP, true) },
      )
      .pipe(
        tap((response) => this.saveTokens(response)),
        map((response) => response.token),
        finalize(() => (this.refreshEmAndamento$ = null)),
        shareReplay(1),
      );

    return this.refreshEmAndamento$;
  }

  // token pronto pra usar (HTTP e WebSocket): renova antes se ja venceu; null se nao tem sessao
  getValidToken(): Observable<string | null> {
    const token = this.getToken();
    if (token && !this.isTokenExpired(token)) {
      return of(token);
    }
    if (this.getRefreshToken()) {
      return this.refreshTokens();
    }
    return of(null);
  }

  // ---------- dados do token ----------

  // o sub do token e o id do usuario
  getUserIdFromToken(): string | null {
    return this.decodeToken()?.sub || null;
  }

  // o backend manda as roles na claim "roles" (lista), ex: ["ROLE_DRIVER"]
  getRolesFromToken(): string[] {
    const decoded = this.decodeToken();
    if (!decoded) return [];

    if (Array.isArray(decoded.roles)) return decoded.roles;
    if (typeof decoded.role === 'string') return [decoded.role];
    return [];
  }

  // logado = token valido, ou token vencido com refreshToken (o interceptor renova na proxima chamada)
  isAuthenticated(): boolean {
    const decoded = this.decodeToken();
    if (!decoded?.sub || (this.isTokenExpired() && !this.getRefreshToken())) {
      this.clearTokens();
      return false;
    }
    return true;
  }

  // rota inicial de acordo com a role do usuário logado, null se não tiver role conhecida
  getHomeRoute(): string | null {
    const roles = this.getRolesFromToken();
    if (roles.includes('ROLE_DRIVER')) return '/driver-home';
    if (roles.includes('ROLE_RESPONSIBLE') || roles.includes('ROLE_ADMIN')) return '/responsible-home';
    return null;
  }

  private decodeToken(): any | null {
    const token = this.getToken();
    if (!token) return null;

    try {
      return jwtDecode(token);
    } catch {
      return null;
    }
  }

  // ---------- rotas ----------

  login(data: loginRequest) {
    const payload: loginRequest = {
      ...data,
      cpf: limparMascara(data.cpf),
    };
    return this.http.post<tokenResponse>(`${this.apiUrl}/auth/login`, payload);
  }

  createDriver(data: {
    documentPdf: File;
    name: string;
    email: string;
    password: string;
    confirmPassword: string;
    phone: string;
  }) {
    const formData = new FormData();

    formData.append('documentPdf', data.documentPdf);
    formData.append('name', data.name);
    formData.append('email', data.email);
    formData.append('password', data.password);
    formData.append('confirmPassword', data.confirmPassword);
    formData.append('phone', limparMascara(data.phone));

    return this.http.post(`${this.apiUrl}/driver/auth/signup`, formData, {
      responseType: 'text',
    });
  }

  createUser(data: createUserRequest) {
    const payload: createUserRequest = {
      ...data,
      cpf: limparMascara(data.cpf),
      phone: limparMascara(data.phone),
      address: { ...data.address, zipCode: limparMascara(data.address.zipCode) },
    };
    return this.http.post(`${this.apiUrl}/responsible/auth/signup`, payload);
  }

  // dados do usuario logado (id, roles, foto e o linkCode do motorista); com foto, ja vem o photoUrl
  getMe(): Observable<UserMe> {
    return this.http.get<UserMe>(`${this.apiUrl}/users/me`).pipe(
      switchMap((me) => {
        if (!me.photoKey) return of(me);
        return this.storageService.getDownloadUrl(me.photoKey).pipe(
          map((photoUrl) => ({ ...me, photoUrl })),
          catchError(() => of(me)),
        );
      }),
    );
  }

  // foto de perfil: pede o link de upload, sobe direto pro bucket, salva no usuario e devolve o link de leitura
  // (o backend so aceita imagem e apaga a foto antiga)
  async updateProfilePhoto(file: File): Promise<string> {
    const contentType = file.type || 'image/jpeg';
    const upload = await firstValueFrom(
      this.http.get<{ uploadUrl: string; fileKey: string }>(`${this.apiUrl}/users/photo-upload-url`, {
        params: { fileName: file.name, contentType },
        context: new HttpContext().set(SKIP_ERROR_POPUP, true),
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

    await firstValueFrom(
      this.http.put(`${this.apiUrl}/users/update-photo`, { fileKey: upload.fileKey }, {
        context: new HttpContext().set(SKIP_ERROR_POPUP, true),
      }),
    );
    return firstValueFrom(this.storageService.getDownloadUrl(upload.fileKey));
  }

  deleteUser(id: string) {
    return this.http.delete(`${this.apiUrl}/users/${id}`);
  }

  verifyCNH(data: { documentPDF: File }) {
    const formData = new FormData();
    formData.append('documentPdf', data.documentPDF);

    return this.http.post(`${this.apiUrl}/driver/auth/verify-CNH`, formData);
  }

  sendCode(email: string) {
    return this.http.post(
      `${this.apiUrl}/auth/send-verification-code?email=${encodeURIComponent(email)}`,
      {},
    );
  }

  verifyEmail(data: verifyEmailRequest) {
    return this.http.post(`${this.apiUrl}/auth/verify-email`, data, { responseType: 'text' });
  }

  sendCodeToUpdateUser() {
    return this.http.post(`${this.apiUrl}/users/send-code-updateUser`, {});
  }

  updateUser(data: updateUserRequest) {
    const payload: updateUserRequest = {
      ...data,
      phone: limparMascara(data.phone),
    };

    return this.http.put(`${this.apiUrl}/users/update-user`, payload);
  }

  // esqueci a senha: publico, sem token. A resposta e a mesma mesmo se o e-mail nao existir
  sendForgotPasswordCode(email: string) {
    return this.http.post(
      `${this.apiUrl}/auth/forgot-password/send-code?email=${encodeURIComponent(email)}`,
      {},
      { responseType: 'text' },
    );
  }

  // trocar a senha derruba as sessoes de todos os aparelhos (precisa entrar de novo)
  resetPassword(data: forgotPasswordRequest) {
    return this.http.post(`${this.apiUrl}/auth/forgot-password`, data, {
      responseType: 'text',
    });
  }

  // apaga o refreshToken no backend (sem esperar a resposta) e os tokens locais
  logout() {
    const refreshToken = this.getRefreshToken();
    this.clearTokens();

    if (refreshToken) {
      this.http
        .post(
          `${this.apiUrl}/auth/logout`,
          { refreshToken },
          { context: new HttpContext().set(SKIP_ERROR_POPUP, true) },
        )
        .subscribe({ error: () => {} });
    }
  }
}
