import { Component, OnInit, computed, inject, input, output, signal } from '@angular/core';
import { catchError, concatMap, from, of, toArray } from 'rxjs';
import { FormsModule } from '@angular/forms';
import { RouteService } from '../../../../services/route.service';
import { ContractService } from '../../../../services/contract.service';
import { PopUpService } from '../../../../shared/pop-up';
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
// cada envio poe UMA acao do aluno (embarque e desembarque sao duas vezes).
// desembarque na escola: da pra levar junto os outros alunos da mesma escola (a escola vem do contrato ativo)
@Component({
  selector: 'app-add-student-to-route',
  standalone: true,
  imports: [FormsModule],
  templateUrl: './add-student-to-route.html',
  styleUrls: ['../../../../shared/folha.css', '../../../../shared/rota.css', './add-student-to-route.css'],
})
export class AddStudentToRoute implements OnInit {
  private routeService = inject(RouteService);
  private contractService = inject(ContractService);
  private popUpService = inject(PopUpService);

  readonly routeId = input.required<string>();
  readonly pontos = input<routeStop[]>([]);
  // alunos com contrato ACTIVE (GET /api/route/stop-options)
  readonly alunos = input<stopOptionStudent[]>([]);
  readonly fechar = output<void>();
  readonly adicionado = output<routeEdit>();

  protected readonly dias = DIAS_DA_SEMANA;
  protected readonly ruaENumero = ruaENumero;

  protected passo = signal<1 | 2 | 3 | 4>(1);
  protected aluno = signal<stopOptionStudent | null>(null);
  protected acao = signal<StopAction>('BOARDING');
  protected local = signal<Local>('HOME');
  protected endereco = signal<stopOptionAddress | null>(null);
  protected diasMarcados = signal<WeekDay[]>([]);
  // null = criar ponto novo
  protected ponto = signal<routeStop | null>(null);
  protected posicao: number | null = null;
  protected enviando = signal(false);

  // escola de cada aluno, pelos contratos ativos do motorista (studentId -> escola)
  private escolas = signal<Record<string, { id: string; nome: string }>>({});
  // desembarque na escola: outros alunos da mesma escola marcados pra ir juntos
  protected juntos = signal<Set<string>>(new Set());

  protected emLote = computed(() => this.acao() === 'ALIGHTING' && this.local() === 'SCHOOL');
  protected escolaDoAluno = computed(() => {
    const aluno = this.aluno();
    return aluno ? (this.escolas()[aluno.studentId] ?? null) : null;
  });
  // da mesma escola e ainda sem desembarque na escola nesta rota
  protected mesmaEscola = computed(() => {
    const aluno = this.aluno();
    const escola = this.escolaDoAluno();
    if (!aluno || !escola || !this.emLote()) return [];
    const jaDescem = new Set(
      this.pontos().flatMap((p) => p.students.filter((a) => a.action === 'ALIGHTING' && a.locationType === 'SCHOOL').map((a) => a.studentId)),
    );
    return this.alunos().filter(
      (a) => a.studentId !== aluno.studentId && this.escolas()[a.studentId]?.id === escola.id && !jaDescem.has(a.studentId),
    );
  });
  // etapa 4 so no desembarque na escola, quando tem outros da mesma escola pra levar junto
  protected temEtapa4 = computed(() => this.emLote() && this.mesmaEscola().length > 0);
  // quantos vao no envio (o aluno escolhido + os marcados)
  protected totalNoEnvio = computed(() => {
    if (!this.emLote()) return 1;
    const marcaveis = new Set(this.mesmaEscola().map((a) => a.studentId));
    return 1 + [...this.juntos()].filter((id) => marcaveis.has(id)).length;
  });

  ngOnInit() {
    this.contractService
      .getMyContracts({ status: 'ACTIVE', size: 200 })
      .pipe(catchError(() => of(null)))
      .subscribe((pagina) => {
        const mapa: Record<string, { id: string; nome: string }> = {};
        for (const c of pagina?.content ?? []) mapa[c.studentId] = { id: c.schoolId, nome: c.schoolName };
        this.escolas.set(mapa);
      });
  }

  alternarJunto(studentId: string) {
    this.juntos.update((s) => {
      const novo = new Set(s);
      if (novo.has(studentId)) novo.delete(studentId);
      else novo.add(studentId);
      return novo;
    });
  }

  // marca todos da mesma escola (o padrao quando chega no desembarque na escola)
  private marcarTodosDaEscola() {
    this.juntos.set(new Set(this.mesmaEscola().map((a) => a.studentId)));
  }

  escolherAcao(acao: StopAction) {
    this.acao.set(acao);
    this.marcarTodosDaEscola();
  }

  // passo 3: pontos que ja existem no lugar escolhido (escola: os pontos com escola; o backend confere se e a do contrato)
  protected pontosNoLugar = computed(() => {
    if (this.local() === 'SCHOOL') {
      const escola = this.escolaDoAluno();
      const daEscola = this.pontos().filter((p) => p.schools.length > 0);
      // sabendo a escola do contrato: so os pontos dela
      return escola ? daEscola.filter((p) => p.schools.some((s) => s.schoolId === escola.id)) : daEscola;
    }
    const enderecoId = this.endereco()?.address.id;
    return this.pontos().filter((p) => !p.schools.length && p.address.id === enderecoId);
  });

  // escola com parada na rota: todo mundo dessa escola desce/embarca na mesma parada (nao cria outra)
  protected escolaJaTemPonto = computed(() => this.local() === 'SCHOOL' && !!this.escolaDoAluno() && this.pontosNoLugar().length > 0);

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
      this.marcarTodosDaEscola();
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
      this.ponto.set(this.escolaJaTemPonto() ? this.pontosNoLugar()[0] : null);
      this.posicao = null;
      this.passo.set(3);
      return;
    }
    if (this.passo() === 3 && this.temEtapa4()) {
      this.marcarTodosDaEscola();
      this.passo.set(4);
    }
  }

  voltar() {
    if (this.enviando()) return;
    if (this.passo() === 1) {
      this.fechar.emit();
      return;
    }
    this.passo.set(this.passo() === 4 ? 3 : this.passo() === 3 ? 2 : 1);
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

    const outros = this.emLote() ? this.mesmaEscola().filter((a) => this.juntos().has(a.studentId)) : [];
    this.enviando.set(true);
    // 400 (ordem, ponto errado), 403 e 409 vao pro popup global com o texto do backend (Figma "erro 400 ordem")
    this.routeService.addStudent(this.routeId(), aluno.studentId, pedido).subscribe({
      next: (edicao) => {
        if (!outros.length) {
          this.enviando.set(false);
          this.adicionado.emit(edicao);
          return;
        }
        this.enviarJuntos(edicao, outros, ponto);
      },
      error: () => this.enviando.set(false),
    });
  }

  // os outros da mesma escola vao pro mesmo ponto da escola, um pedido por aluno (o backend recebe um por vez)
  private enviarJuntos(primeira: routeEdit, outros: stopOptionStudent[], ponto: routeStop | null) {
    // ponto novo: e o que apareceu na resposta e nao existia antes
    const antes = new Set(this.pontos().map((p) => p.id));
    const destino = ponto ?? primeira.stops.find((p) => !antes.has(p.id) && p.schools.length > 0) ?? null;
    if (!destino) {
      this.enviando.set(false);
      this.adicionado.emit(primeira);
      return;
    }
    const pedido: addStudentRequest = {
      action: 'ALIGHTING',
      locationType: 'SCHOOL',
      weekdays: this.diasMarcados(),
      routeStopId: destino.id,
    };
    let ultima = primeira;
    from(outros)
      .pipe(
        concatMap((a) =>
          this.routeService.addStudent(this.routeId(), a.studentId, pedido).pipe(
            catchError(() => of(null)),
          ),
        ),
        toArray(),
      )
      .subscribe((respostas) => {
        for (const r of respostas) if (r) ultima = r;
        const certos = 1 + respostas.filter(Boolean).length;
        const falhas = respostas.length - (certos - 1);
        this.enviando.set(false);
        this.popUpService.show(
          falhas ? `${certos} adicionados. ${falhas} não entraram (o motivo aparece no aviso de cada um).` : `${certos} alunos descem neste ponto da escola.`,
          falhas ? 'warning' : 'success',
          falhas ? 'Alguns alunos não entraram' : 'Alunos adicionados',
        );
        this.adicionado.emit(ultima);
      });
  }
}
