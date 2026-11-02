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

// mensagens que o backend ainda manda em ingles (ou genericas do Spring) traduzidas pro usuario
const TRADUCOES: Record<string, string> = {
  'User not found': 'Usuário não encontrado.',
  'Student not found': 'Dependente não encontrado.',
  'Route not found': 'Rota não encontrada.',
  "Route doesn't exists": 'Rota não encontrada.',
  'Trip not found': 'Viagem não encontrada.',
  'Stop not found': 'Ponto não encontrado.',
  'Route stop not found': 'Ponto da rota não encontrado.',
  'Student stop not found': 'Ponto do aluno não encontrado.',
  'School stop not found': 'Ponto da escola não encontrado.',
  'School not found.': 'Escola não encontrada.',
  'Student is already registered at this stop.': 'O aluno já está cadastrado neste ponto.',
  'Driver already linked to this school': 'Você já atende esta escola.',
  'Route does not belong to this driver': 'Esta rota não pertence a você.',
  'Access Denied': 'Você não tem permissão para realizar esta ação.',
};

// "trip" aparece no meio de varias mensagens em portugues do backend
function ajustarTermos(mensagem: string): string {
  return mensagem.replace(/\btrip\b/gi, 'viagem');
}

/**
 * Mensagem que o backend escreveu pro erro (ExceptionResponse.message ou ResponseStatusException.reason),
 * ja traduzida. Devolve null quando nao tem mensagem aproveitavel: sem corpo, texto tecnico do Angular
 * ("Http failure response...") ou o "Invalid request content." do @Valid, que nao diz o campo.
 */
export function mensagemDoBackend(error: unknown): string | null {
  const corpo = (error as { error?: unknown } | null)?.error;
  const bruta =
    typeof corpo === 'string'
      ? corpo
      : (corpo as { message?: unknown } | null)?.message ?? (corpo as { detail?: unknown } | null)?.detail;

  if (typeof bruta !== 'string') return null;
  const mensagem = bruta.trim();
  if (!mensagem || mensagem.startsWith('{') || mensagem.startsWith('<')) return null;
  if (mensagem.includes('Http failure') || mensagem === 'Invalid request content.') return null;

  return TRADUCOES[mensagem] ?? ajustarTermos(mensagem);
}

const JANELA_DUPLICADA_MS = 300;
// erros de negocio que o backend ainda devolve como 500 (IllegalStateException/RuntimeException)
const MENSAGENS_500_PARA_USUARIO = ['Código incorreto ou expirado.', 'Não foi possível gerar um código único.'];

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

    const status: number | undefined = error.status;
    const backendMessage = mensagemDoBackend(error);

    if (status === 0) {
      this.show('Não foi possível conectar ao servidor. Verifique sua conexão com a internet.', 'error', 'Sem conexão');
    } else if (status === 400) {
      this.show(backendMessage || 'Dados inválidos. Verifique as informações preenchidas.', 'error', 'Dados inválidos');
    } else if (status === 401) {
      // 401 de rota logada vira "sessao expirada" no interceptor; aqui chega o do login
      this.show(backendMessage || 'CPF ou senha inválidos.', 'error', 'Acesso negado');
    } else if (status === 403) {
      this.show(backendMessage || 'Você não tem permissão para realizar esta ação.', 'error', 'Sem permissão');
    } else if (status === 404) {
      this.show(backendMessage || 'Não encontramos o que você procurou.', 'error', 'Não encontrado');
    } else if (status === 409) {
      this.show(backendMessage || 'Esta ação conflita com dados já cadastrados.', 'warning', 'Não foi possível concluir');
    } else if (status === 422) {
      this.show(backendMessage || 'Informações inválidas. Verifique os dados inseridos.', 'error', 'Dados inválidos');
    } else if (status === 503) {
      this.show(backendMessage || 'Serviço indisponível agora. Tente mais tarde.', 'error', 'Serviço indisponível');
    } else if (status !== undefined && status >= 500) {
      // 500 traz a mensagem crua da exception (as vezes tecnica): so mostra as que sao pro usuario
      const message =
        backendMessage && MENSAGENS_500_PARA_USUARIO.includes(backendMessage)
          ? backendMessage
          : 'Ocorreu um erro no servidor. Tente novamente mais tarde.';
      this.show(message, 'error', 'Erro no servidor');
    } else {
      this.show(backendMessage || defaultMessage, 'error');
    }
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
