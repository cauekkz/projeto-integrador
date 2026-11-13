import { formatarCpf, formatarTelefone, limparMascara } from '../../../shared/utils';

// enum PixKeyType do backend
export type PixKeyType = 'CPF' | 'CNPJ' | 'EMAIL' | 'PHONE' | 'RANDOM';

// PixKeyDTO: sem chave, os dois vem null; na resposta a chave ja vem normalizada
export interface pixKey {
  type: PixKeyType | null;
  key: string | null;
}

export const PIX_TIPOS: { value: PixKeyType; label: string; placeholder: string; inputmode: string }[] = [
  { value: 'CPF', label: 'CPF', placeholder: '000.000.000-00', inputmode: 'numeric' },
  { value: 'CNPJ', label: 'CNPJ', placeholder: '00.000.000/0000-00', inputmode: 'numeric' },
  { value: 'EMAIL', label: 'E-mail', placeholder: 'voce@email.com', inputmode: 'email' },
  { value: 'PHONE', label: 'Telefone', placeholder: '(11) 91234-5678', inputmode: 'tel' },
  { value: 'RANDOM', label: 'Aleatória', placeholder: 'Chave aleatória', inputmode: 'text' },
];

export function pixTipoLabel(tipo: PixKeyType | null | undefined): string {
  return PIX_TIPOS.find((t) => t.value === tipo)?.label ?? '';
}

// 00.000.000/0000-00 (aceita o valor pela metade, enquanto digita)
export function formatarCnpj(valor: string | null | undefined): string {
  const d = limparMascara(valor).slice(0, 14);
  if (d.length <= 2) return d;
  if (d.length <= 5) return `${d.slice(0, 2)}.${d.slice(2)}`;
  if (d.length <= 8) return `${d.slice(0, 2)}.${d.slice(2, 5)}.${d.slice(5)}`;
  if (d.length <= 12) return `${d.slice(0, 2)}.${d.slice(2, 5)}.${d.slice(5, 8)}/${d.slice(8)}`;
  return `${d.slice(0, 2)}.${d.slice(2, 5)}.${d.slice(5, 8)}/${d.slice(8, 12)}-${d.slice(12)}`;
}

// so pra mostrar: CPF/CNPJ/telefone com mascara; copiar continua usando a chave como veio do backend
export function formatarChavePix(tipo: PixKeyType | null | undefined, chave: string | null | undefined): string {
  if (!chave) return '';
  if (tipo === 'CPF') return formatarCpf(chave);
  if (tipo === 'CNPJ') return formatarCnpj(chave);
  if (tipo === 'PHONE') return formatarTelefone(chave.replace(/^\+?55(?=\d{10,11}$)/, ''));
  return chave;
}
