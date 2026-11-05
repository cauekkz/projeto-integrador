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
