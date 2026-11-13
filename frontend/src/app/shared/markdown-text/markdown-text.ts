import { Component, Input, computed, signal } from '@angular/core';

// pedaco de uma linha: texto normal ou **negrito**
export interface Trecho {
  texto: string;
  negrito: boolean;
}

export interface Bloco {
  tipo: 'h1' | 'h2' | 'p' | 'li';
  nivel: number; // so pra lista: 0 = item, 1 = subitem
  trechos: Trecho[];
}

// mostra o markdown simples dos termos (GET /api/terms): # titulo, ## secao, - lista (com subitem) e **negrito**
// monta os elementos pelo template (sem innerHTML), entao o texto nunca vira HTML
@Component({
  selector: 'app-markdown-text',
  standalone: true,
  templateUrl: './markdown-text.html',
  styleUrl: './markdown-text.css',
})
export class MarkdownText {
  private texto = signal('');
  // o titulo (#) pode sair quando a tela ja mostra o titulo em outro lugar
  @Input() semTitulo = false;

  @Input() set conteudo(valor: string | null | undefined) {
    this.texto.set(valor ?? '');
  }

  protected blocos = computed(() => {
    const blocos = lerMarkdown(this.texto());
    return this.semTitulo ? blocos.filter((b) => b.tipo !== 'h1') : blocos;
  });
}

export function lerMarkdown(texto: string): Bloco[] {
  const blocos: Bloco[] = [];
  let paragrafo: string[] = [];

  const fecharParagrafo = () => {
    if (paragrafo.length) blocos.push({ tipo: 'p', nivel: 0, trechos: trechos(paragrafo.join(' ')) });
    paragrafo = [];
  };

  for (const linhaBruta of texto.replace(/\r/g, '').split('\n')) {
    const linha = linhaBruta.trimEnd();
    if (!linha.trim()) {
      fecharParagrafo();
      continue;
    }
    const titulo = /^(#{1,6})\s+(.*)$/.exec(linha.trim());
    if (titulo) {
      fecharParagrafo();
      blocos.push({ tipo: titulo[1].length === 1 ? 'h1' : 'h2', nivel: 0, trechos: trechos(titulo[2]) });
      continue;
    }
    const item = /^(\s*)[-*]\s+(.*)$/.exec(linha);
    if (item) {
      fecharParagrafo();
      blocos.push({ tipo: 'li', nivel: item[1].length >= 2 ? 1 : 0, trechos: trechos(item[2]) });
      continue;
    }
    paragrafo.push(linha.trim());
  }
  fecharParagrafo();
  return blocos;
}

function trechos(linha: string): Trecho[] {
  return linha
    .split('**')
    .map((texto, i) => ({ texto, negrito: i % 2 === 1 }))
    .filter((t) => t.texto);
}
