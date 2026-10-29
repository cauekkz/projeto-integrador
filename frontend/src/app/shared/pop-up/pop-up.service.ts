import { Injectable, signal } from '@angular/core';

export interface PopUpItem {
  id: string;
  type: 'error' | 'warning' | 'info' | 'success';
  title: string;
  message: string;
  durationMs: number;
  // quantas vezes a mesma notificacao chegou enquanto estava na tela
  repeticoes: number;
}

const JANELA_DUPLICADA_MS = 300;

@Injectable({
  providedIn: 'root',
})
export class PopUpService {
  readonly popups = signal<PopUpItem[]>([]);
  private timers = new Map<string, ReturnType<typeof setTimeout>>();
  // ultima vez que cada card foi mostrado/contado
  private ultimaVez = new Map<string, number>();

  show(
    message: string,
    type: 'error' | 'warning' | 'info' | 'success' = 'error',
    title?: string,
    durationMs: number = 4500
  ): string {
    const defaultTitle =
      title ||
      (type === 'error'
        ? 'Erro'
        : type === 'warning'
          ? 'Atenção'
          : type === 'success'
            ? 'Sucesso'
            : 'Informação');

    // mesma notificacao ainda na tela: nao empilha outra, so conta a repeticao e renova o tempo
    const existente = this.popups().find(
      (item) => item.type === type && item.title === defaultTitle && item.message === message
    );
    if (existente) {
      // a mesma chamada costuma chegar 2x seguidas (interceptor + tela, clique + submit): nao conta como repeticao
      const agora = Date.now();
      if (agora - (this.ultimaVez.get(existente.id) ?? 0) < JANELA_DUPLICADA_MS) {
        return existente.id;
      }
      this.ultimaVez.set(existente.id, agora);
      this.popups.update((current) =>
        current.map((item) =>
          item.id === existente.id ? { ...item, repeticoes: item.repeticoes + 1, durationMs } : item
        )
      );
      this.agendarRemocao(existente.id, durationMs);
      return existente.id;
    }

    const id = Math.random().toString(36).substring(2, 9) + Date.now().toString(36);

    const item: PopUpItem = {
      id,
      type,
      title: defaultTitle,
      message,
      durationMs,
      repeticoes: 1,
    };

    this.popups.update((current) => [...current, item]);
    this.ultimaVez.set(id, Date.now());
    this.agendarRemocao(id, durationMs);

    return id;
  }

  private agendarRemocao(id: string, durationMs: number): void {
    clearTimeout(this.timers.get(id));
    this.timers.delete(id);
    if (durationMs > 0) {
      this.timers.set(id, setTimeout(() => this.remove(id), durationMs));
    }
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
    clearTimeout(this.timers.get(id));
    this.timers.delete(id);
    this.ultimaVez.delete(id);
    this.popups.update((current) => current.filter((item) => item.id !== id));
  }

  clear(): void {
    this.timers.forEach((timer) => clearTimeout(timer));
    this.timers.clear();
    this.ultimaVez.clear();
    this.popups.set([]);
  }
}
