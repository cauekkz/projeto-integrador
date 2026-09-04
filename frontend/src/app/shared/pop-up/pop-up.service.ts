import { Injectable, signal } from '@angular/core';

export interface PopUpItem {
  id: string;
  type: 'error' | 'warning' | 'info' | 'success';
  title: string;
  message: string;
  durationMs: number;
}

@Injectable({
  providedIn: 'root' // <-- Garante que existe APENAS UMA instância no projeto todo
})
export class PopUpService {
  readonly popups = signal<PopUpItem[]>([]);
  private lastShowTimestamp = 0;
  private lastShowMessage = '';

  show(
    message: string,
    type: 'error' | 'warning' | 'info' | 'success' = 'error',
    title?: string,
    durationMs: number = 4500
  ): string {
    const now = Date.now();

    // TRAVA ANTI-DUPLICAÇÃO:
    // Se for a mesma mensagem disparada num intervalo menor que 300ms, ignora a segunda.
    if (this.lastShowMessage === message && now - this.lastShowTimestamp < 300) {
      return '';
    }

    this.lastShowMessage = message;
    this.lastShowTimestamp = now;

    const id = Math.random().toString(36).substring(2, 9) + now.toString(36);

    const defaultTitle =
      title ||
      (type === 'error'
        ? 'Erro'
        : type === 'warning'
          ? 'Atenção'
          : type === 'success'
            ? 'Sucesso'
            : 'Informação');

    const item: PopUpItem = {
      id,
      type,
      title: defaultTitle,
      message,
      durationMs,
    };

    this.popups.update((current) => [...current, item]);

    if (durationMs > 0) {
      setTimeout(() => {
        this.remove(id);
      }, durationMs);
    }

    return id;
  }

  showError(error: any, defaultMessage: string = 'Ocorreu um erro ao processar a requisição.'): void {
    if (typeof error === 'string') {
      this.show(error, 'error');
      return;
    }

    if (!error) {
      this.show(defaultMessage, 'error');
      return;
    }

    const status = error.status;
    let message = defaultMessage;

    const backendMessage =
      typeof error.error === 'string'
        ? error.error
        : error.error?.message || error.error?.error || error.message;

    if (status === 0) {
      message = 'Não foi possível conectar ao servidor. Verifique sua conexão com a internet.';
    } else if (status === 400) {
      message = backendMessage || 'Dados inválidos. Por favor, verifique as informações preenchidas.';
    } else if (status === 401) {
      message = backendMessage || 'E-mail, CPF ou senha inválidos.';
    } else if (status === 403) {
      message = 'Sua sessão expirou ou você não tem permissão para realizar esta ação.';
    } else if (status === 404) {
      message = backendMessage || 'Usuário ou recurso não encontrado.';
    } else if (status === 409) {
      message = backendMessage || 'Este e-mail ou CPF já está cadastrado no sistema.';
    } else if (status === 422) {
      message = backendMessage || 'Informações inválidas. Verifique os dados inseridos.';
    } else if (status >= 500) {
      message = 'Ocorreu um erro no servidor. Tente novamente mais tarde.';
    } else if (typeof backendMessage === 'string' && backendMessage.trim() && !backendMessage.includes('Http failure')) {
      message = backendMessage;
    }

    this.show(message, 'error');
  }

  remove(id: string): void {
    this.popups.update((current) => current.filter((item) => item.id !== id));
  }

  clear(): void {
    this.popups.set([]);
  }
}
