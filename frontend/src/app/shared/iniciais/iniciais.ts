import { signal } from '@angular/core';

// "Maria da Silva" -> "MS"; "Pedro de Alcântara Neto" -> "PN"; "Jo" -> "J"; nulo -> "?".
// primeira e ultima palavra, sem as particulas (da, de, do, dos, das, e), por letra de verdade (acento e emoji inteiros)
const PARTICULAS = new Set(['da', 'de', 'do', 'das', 'dos', 'e', 'di', 'del', 'van', 'von']);

function primeiraLetra(palavra: string): string {
  return (Array.from(palavra)[0] ?? '').toLocaleUpperCase('pt-BR');
}

export function iniciaisDe(nome: string | null | undefined): string {
  const palavras = (nome ?? '').trim().split(/\s+/).filter(Boolean);
  if (!palavras.length) return '?';
  const fortes = palavras.filter((p, i) => i === 0 || !PARTICULAS.has(p.toLocaleLowerCase('pt-BR')));
  const primeira = primeiraLetra(fortes[0]);
  const ultima = fortes.length > 1 ? primeiraLetra(fortes[fortes.length - 1]) : '';
  return primeira + ultima;
}

// foto que nao carregou (link expirado da MinIO, arquivo apagado): a tela volta pras iniciais.
// uso: fotos = fotosQuebradas(); no template @if (url && !fotos.falhou(url)) <img (error)="fotos.marcar(url)">
export function fotosQuebradas() {
  const quebradas = signal<ReadonlySet<string>>(new Set());
  return {
    falhou: (url: string | null | undefined) => !!url && quebradas().has(url),
    marcar: (url: string | null | undefined) => {
      if (url) quebradas.update((s) => new Set(s).add(url));
    },
  };
}
