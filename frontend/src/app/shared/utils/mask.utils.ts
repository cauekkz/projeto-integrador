/**
 * Utilitários para tratamento, limpeza e formatação de máscaras de formulários.
 * Garante que os caracteres de formatação sejam utilizados somente para exibição
 * na interface e nunca sejam enviados nas requisições HTTP para o backend.
 */

/**
 * Remove todos os caracteres não numéricos de uma string,
 * preservando zeros à esquerda e sem converter o valor para tipo numérico.
 *
 * Exemplos:
 *  - CPF: '123.456.789-00' -> '12345678900'
 *  - Telefone: '(11) 98765-4321' -> '11987654321'
 *  - CEP: '04165-011' -> '04165011'
 */
export function limparMascara(valor: string | null | undefined): string {
  return (valor ?? '').replace(/\D/g, '');
}

/**
 * Formata um valor de CPF no padrão 000.000.000-00 para exibição na interface.
 */
export function formatarCpf(valor: string | null | undefined): string {
  const digits = limparMascara(valor).slice(0, 11);
  if (!digits) return '';
  if (digits.length <= 3) return digits;
  if (digits.length <= 6) return `${digits.slice(0, 3)}.${digits.slice(3)}`;
  if (digits.length <= 9) return `${digits.slice(0, 3)}.${digits.slice(3, 6)}.${digits.slice(6)}`;
  return `${digits.slice(0, 3)}.${digits.slice(3, 6)}.${digits.slice(6, 9)}-${digits.slice(9, 11)}`;
}

/**
 * Formata um valor de telefone no padrão (00) 0000-0000 ou (00) 00000-0000.
 */
export function formatarTelefone(valor: string | null | undefined): string {
  const digits = limparMascara(valor).slice(0, 11);
  if (!digits) return '';
  if (digits.length <= 2) return `(${digits}`;
  if (digits.length <= 6) return `(${digits.slice(0, 2)}) ${digits.slice(2)}`;
  if (digits.length <= 10) return `(${digits.slice(0, 2)}) ${digits.slice(2, 6)}-${digits.slice(6)}`;
  return `(${digits.slice(0, 2)}) ${digits.slice(2, 7)}-${digits.slice(7, 11)}`;
}

/**
 * Formata um valor de CEP no padrão 00000-000 para exibição na interface.
 */
export function formatarCep(valor: string | null | undefined): string {
  const digits = limparMascara(valor).slice(0, 8);
  if (!digits) return '';
  if (digits.length <= 5) return digits;
  return `${digits.slice(0, 5)}-${digits.slice(5, 8)}`;
}

/**
 * Formata um valor de data no padrão DD/MM/AAAA para exibição na interface.
 */
export function formatarData(valor: string | null | undefined): string {
  const digits = limparMascara(valor).slice(0, 8);
  if (!digits) return '';
  if (digits.length <= 2) return digits;
  if (digits.length <= 4) return `${digits.slice(0, 2)}/${digits.slice(2)}`;
  return `${digits.slice(0, 2)}/${digits.slice(2, 4)}/${digits.slice(4, 8)}`;
}

/**
 * Converte data de DD/MM/AAAA para formato ISO AAAA-MM-DD.
 */
export function dataParaIso(valor: string | null | undefined): string {
  if (!valor) return '';
  const digits = limparMascara(valor);
  if (digits.length === 8) {
    const dia = digits.slice(0, 2);
    const mes = digits.slice(2, 4);
    const ano = digits.slice(4, 8);
    return `${ano}-${mes}-${dia}`;
  }
  const parts = valor.split('/');
  if (parts.length === 3 && parts[0] && parts[1] && parts[2]) {
    const [dia, mes, ano] = parts;
    return `${ano}-${mes}-${dia}`;
  }
  return valor;
}
