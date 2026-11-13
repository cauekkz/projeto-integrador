// toda lista grande do backend vem paginada (Spring Data, PageImpl serializado direto):
// ?page= comeca em 0 e ?size= vai ate 100. Vale pra chats, mensagens, notificacoes, my-contracts,
// parcelas, historico de viagens, my-children e get-all-routes-and-stops
export interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  // pagina atual, comecando em 0
  number: number;
  size: number;
  numberOfElements: number;
  first: boolean;
  last: boolean;
  empty: boolean;
}

export interface PageRequest {
  page?: number;
  size?: number;
}

// o backend so aceita size de 1 a 100
export const PAGE_SIZE_MAX = 100;

// monta ?page=&size= (so o que veio), com o size dentro do limite do backend
export function paramsDePagina(pedido?: PageRequest): Record<string, number> {
  const params: Record<string, number> = {};
  if (pedido?.page !== undefined) params['page'] = Math.max(0, Math.floor(pedido.page));
  if (pedido?.size !== undefined) params['size'] = Math.min(PAGE_SIZE_MAX, Math.max(1, Math.floor(pedido.size)));
  return params;
}

// pagina vazia, pra estado inicial ou erro
export function paginaVazia<T>(): Page<T> {
  return { content: [], totalElements: 0, totalPages: 0, number: 0, size: 0, numberOfElements: 0, first: true, last: true, empty: true };
}
