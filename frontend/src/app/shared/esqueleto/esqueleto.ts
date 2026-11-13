import { Component, computed, input } from '@angular/core';

// lugar do conteudo enquanto ele carrega: o formato da tela aparece antes dos dados.
// o texto continua la pra leitor de tela (e pros testes), so nao aparece
@Component({
  selector: 'app-esqueleto',
  standalone: true,
  templateUrl: './esqueleto.html',
  styleUrl: './esqueleto.css',
})
export class Esqueleto {
  readonly tipo = input<'lista' | 'perfil' | 'texto'>('lista');
  readonly quantos = input(3);
  readonly texto = input('Carregando...');

  protected readonly itens = computed(() => Array.from({ length: this.quantos() }, (_, i) => i));
}
