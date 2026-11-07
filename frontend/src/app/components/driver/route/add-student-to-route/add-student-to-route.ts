import { Component, computed, inject, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouteService } from '../../../../services/route.service';
import {
  DIAS_DA_SEMANA,
  StopAction,
  StopLocationType,
  WeekDay,
  addStudentRequest,
  routeEdit,
  routeStop,
  ruaENumero,
  stopOptionAddress,
  stopOptionStudent,
} from '../../../../services/dto/route/route.dto';

type Local = StopLocationType;

// Figma "Motorista · Adicionar aluno" 1 a 3: aluno -> acao, local, endereco e dias -> ponto (novo ou que ja existe)
// cada envio poe UMA acao do aluno (embarque e desembarque sao duas vezes)
@Component({
  selector: 'app-add-student-to-route',
  standalone: true,
  imports: [FormsModule],
  templateUrl: './add-student-to-route.html',
  styleUrls: ['../../../../shared/folha.css', '../../../../shared/rota.css', './add-student-to-route.css'],
})
export class AddStudentToRoute {
  private routeService = inject(RouteService);

  readonly routeId = input.required<string>();
  readonly pontos = input<routeStop[]>([]);
  // alunos com contrato ACTIVE (GET /api/route/stop-options)
  readonly alunos = input<stopOptionStudent[]>([]);
  readonly fechar = output<void>();
  readonly adicionado = output<routeEdit>();

  protected readonly dias = DIAS_DA_SEMANA;
  protected readonly ruaENumero = ruaENumero;

  protected passo = signal<1 | 2 | 3>(1);
  protected aluno = signal<stopOptionStudent | null>(null);
  protected acao = signal<StopAction>('BOARDING');
  protected local = signal<Local>('HOME');
  protected endereco = signal<stopOptionAddress | null>(null);
  protected diasMarcados = signal<WeekDay[]>([]);
  // null = criar ponto novo
  protected ponto = signal<routeStop | null>(null);
  protected posicao: number | null = null;
  protected enviando = signal(false);

  // passo 3: pontos que ja existem no lugar escolhido (escola: os pontos com escola; o backend confere se e a do contrato)
  protected pontosNoLugar = computed(() => {
    if (this.local() === 'SCHOOL') return this.pontos().filter((p) => p.schools.length > 0);
    const enderecoId = this.endereco()?.address.id;
    return this.pontos().filter((p) => !p.schools.length && p.address.id === enderecoId);
  });

  // "2 endereços"
  totalEnderecos(aluno: stopOptionStudent): string {
    const total = aluno.addresses?.length ?? 0;
    return total === 1 ? '1 endereço' : `${total} endereços`;
  }

  // "Av. Paulista, 1000 · Bela Vista"
  linhaEndereco(opcao: stopOptionAddress): string {
    return [ruaENumero(opcao.address), opcao.address.neighborhood].filter(Boolean).join(' · ');
  }

  escolherAluno(aluno: stopOptionStudent) {
    this.aluno.set(aluno);
  }

  escolherLocal(local: Local) {
    this.local.set(local);
    if (local === 'SCHOOL') {
      this.endereco.set(null);
      return;
    }
    // "Casa" comeca no endereco "Casa"; "Outro lugar", no primeiro que nao e a casa
    const enderecos = this.aluno()?.addresses ?? [];
    const casa = enderecos.find((e) => e.label?.toLowerCase() === 'casa');
    const outro = enderecos.find((e) => e !== casa);
    this.endereco.set((local === 'HOME' ? casa ?? enderecos[0] : outro ?? enderecos[0]) ?? null);
  }

  alternarDia(dia: WeekDay) {
    this.diasMarcados.update((dias) => (dias.includes(dia) ? dias.filter((d) => d !== dia) : [...dias, dia]));
  }

  continuar() {
    if (this.passo() === 1) {
      if (!this.aluno()) return;
      this.acao.set('BOARDING');
      this.diasMarcados.set([]);
      this.escolherLocal('HOME');
      this.passo.set(2);
      return;
    }
    if (this.passo() === 2) {
      if (this.local() !== 'SCHOOL' && !this.endereco()) return;
      this.ponto.set(null);
      this.posicao = null;
      this.passo.set(3);
    }
  }

  voltar() {
    if (this.enviando()) return;
    if (this.passo() === 1) {
      this.fechar.emit();
      return;
    }
    this.passo.set(this.passo() === 3 ? 2 : 1);
  }

  enviar() {
    const aluno = this.aluno();
    if (!aluno || this.enviando()) return;

    const pedido: addStudentRequest = {
      action: this.acao(),
      locationType: this.local(),
      weekdays: this.diasMarcados(),
    };
    const ponto = this.ponto();
    if (ponto) {
      pedido.routeStopId = ponto.id;
    } else {
      pedido.newStop = true;
      if (this.local() !== 'SCHOOL') pedido.studentAddressId = this.endereco()!.id;
      const posicao = Number(this.posicao);
      if (this.posicao !== null && String(this.posicao) !== '' && posicao >= 1) pedido.orderIndex = Math.floor(posicao);
    }

    this.enviando.set(true);
    // 400 (ordem, ponto errado), 403 e 409 vao pro popup global com o texto do backend (Figma "erro 400 ordem")
    this.routeService.addStudent(this.routeId(), aluno.studentId, pedido).subscribe({
      next: (edicao) => {
        this.enviando.set(false);
        this.adicionado.emit(edicao);
      },
      error: () => this.enviando.set(false),
    });
  }
}
