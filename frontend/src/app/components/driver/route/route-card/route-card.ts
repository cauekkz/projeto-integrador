import { Component, computed, input, output } from '@angular/core';
import { routeStop } from '../../../../services/dto/route/route.dto';

// Figma "Cartão de rota" 1038:648: "3 pontos", nome da rota, alunos e a seta.
// usado na lista de rotas, na home do motorista e no topo da viagem
@Component({
  selector: 'app-route-card',
  standalone: true,
  template: `
    <button type="button" class="cartao" [disabled]="!clicavel()" (click)="abrir.emit()">
      <span class="textos">
        <small>{{ pontos() }}</small>
        <strong>{{ nome() }}</strong>
      </span>
      <span class="alunos" [attr.aria-label]="alunos() + ' alunos'">
        {{ alunos() }}
        <i class="bi bi-people" aria-hidden="true"></i>
      </span>
      <i class="bi bi-chevron-down seta" aria-hidden="true"></i>
    </button>
  `,
  styleUrl: './route-card.css',
})
export class RouteCard {
  readonly nome = input.required<string>();
  readonly paradas = input<routeStop[]>([]);
  readonly clicavel = input(true);
  readonly abrir = output<void>();

  // "3 pontos" / "1 ponto"
  protected pontos = computed(() => {
    const total = this.paradas()?.length ?? 0;
    return total === 1 ? '1 ponto' : `${total} pontos`;
  });

  // alunos diferentes na rota (o mesmo aluno embarca e desce)
  protected alunos = computed(() => new Set((this.paradas() ?? []).flatMap((s) => s.students.map((a) => a.studentId))).size);
}
