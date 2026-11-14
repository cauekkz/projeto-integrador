import { Skeleton } from '../../../shared/skeleton/skeleton';
import { Component, DestroyRef, ElementRef, OnInit, computed, effect, inject, signal, viewChild } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { Observable, catchError, concatMap, forkJoin, from, of, tap, toArray } from 'rxjs';
import { ContractService } from '../../../services/contract.service';
import { TrailHeader } from '../../../shared/trail-header/trail-header';
import { Footer } from '../../../shared/footer/footer';
import { PopUpService } from '../../../shared/pop-up';
import { RouteService } from '../../../services/route.service';
import { SchoolService } from '../../../services/school.service';
import { VehicleService } from '../../../services/vehicle.service';
import { vehicleResponse } from '../../../services/dto/vehicle/vehicle.dto';
import { AddStudentToRoute } from '../../../components/driver/route/add-student-to-route/add-student-to-route';
import { formatarCrlv } from '../driver-vehicles/driver-vehicles';
import {
  DIAS_DA_SEMANA,
  WeekDay,
  ordenarDias,
  resumoDias,
  routeEdit,
  routePending,
  routeResponse,
  routeStop,
  ruaENumero,
  schoolStop,
  stopOptionStudent,
  studentStop,
  textoPendencia,
} from '../../../services/dto/route/route.dto';
import { ConfirmacaoService } from '../../../shared/confirmacao/confirmacao.service';

import { BotaoCarregandoDirective } from '../../../shared/botao-carregando/botao-carregando.directive';
// Figma "Rotas · Motorista": editor da rota (vazia, com pendencia, pronta), folhas do aluno, do ponto e da van
@Component({
  selector: 'app-driver-route-editor',
  standalone: true,
  imports: [BotaoCarregandoDirective, Skeleton, TrailHeader, Footer, AddStudentToRoute],
  templateUrl: './driver-route-editor.html',
  styleUrls: ['../../../shared/folha.css', '../../../shared/rota.css', './driver-route-editor.css'],
})
export class DriverRouteEditor implements OnInit {
  private routeService = inject(RouteService);
  private schoolService = inject(SchoolService);
  private vehicleService = inject(VehicleService);
  private popUpService = inject(PopUpService);
  private confirmacao = inject(ConfirmacaoService);
  private contractService = inject(ContractService);
  private activatedRoute = inject(ActivatedRoute);
  private router = inject(Router);

  protected readonly dias = DIAS_DA_SEMANA;
  protected readonly ruaENumero = ruaENumero;
  protected readonly resumoDias = resumoDias;

  protected routeId = '';
  protected rota = signal<routeResponse | null>(null);
  protected pontos = signal<routeStop[]>([]);
  protected pendencias = signal<routePending[]>([]);
  protected alunos = signal<stopOptionStudent[]>([]);
  private escolas = new Map<string, string>();

  protected carregando = signal(true);
  // 'semAcesso' = 403/404: ele saiu da rota (aparece no filtro "Todas")
  protected erroCarregar = signal<'' | 'erro' | 'semAcesso'>('');
  protected enviando = signal(false);

  protected pronta = computed(() => this.pontos().length > 0 && this.pendencias().length === 0);

  // "Adicionar desembarque": quem embarca e ainda nao desce, agrupado pela escola do contrato
  private escolaDoAluno = signal<Record<string, string>>({});
  protected desembarcando = signal(false);
  protected marcadosDesembarque = signal<Set<string>>(new Set());
  protected gruposDesembarque = computed(() => {
    const grupos = new Map<string, { escolaId: string; nome: string; alunos: routePending[] }>();
    for (const p of this.pendencias()) {
      const escolaId = this.escolaDoAluno()[p.studentId];
      if (!p.missingAlighting.length || !escolaId) continue;
      if (!grupos.has(escolaId)) grupos.set(escolaId, { escolaId, nome: this.escolas.get(escolaId) ?? 'Escola', alunos: [] });
      grupos.get(escolaId)!.alunos.push(p);
    }
    return [...grupos.values()];
  });

  // a mesma escola em mais de uma parada (rotas antigas criavam uma por aluno): aviso pra juntar
  protected escolasRepetidas = computed(() => {
    const grupos = new Map<string, routeStop[]>();
    for (const p of this.pontos()) {
      for (const s of p.schools) {
        if (!grupos.has(s.schoolId)) grupos.set(s.schoolId, []);
        grupos.get(s.schoolId)!.push(p);
      }
    }
    return [...grupos.entries()]
      .filter(([, pontos]) => pontos.length > 1)
      .map(([escolaId, pontos]) => ({ escolaId, nome: this.escolas.get(escolaId) ?? ruaENumero(pontos[0].address), pontos }));
  });

  // "Adicionar aluno" (3 passos em tela cheia)
  protected adicionando = signal(false);

  // folha do aluno no ponto
  protected alunoAberto = signal<{ aluno: studentStop; ponto: routeStop } | null>(null);
  protected diasDoAluno = signal<WeekDay[]>([]);
  protected pontoDestino = signal<routeStop | null>(null);

  // folha do ponto
  protected pontoAberto = signal<routeStop | null>(null);

  // arrastar a parada pelos 2 tracos (ordem da rota)
  private listaPontos = viewChild<ElementRef<HTMLElement>>('listaPontos');
  protected arrastando = signal<number | null>(null);
  private arraste: Arraste | null = null;
  private destroyRef = inject(DestroyRef);

  // folha da van padrao
  protected trocandoVan = signal(false);
  protected vans = signal<vehicleResponse[]>([]);
  protected carregandoVans = signal(false);
  protected vanEscolhida = signal<string | null>(null);

  constructor() {
    // a estrada atras das paradas vai do 1o ao ultimo marcador: o fim depende da altura do ultimo card
    let observador: ResizeObserver | null = null;
    effect(() => {
      const lista = this.listaPontos()?.nativeElement;
      observador?.disconnect();
      if (!lista || typeof ResizeObserver === 'undefined') return;
      const medir = () => {
        const ultimo = lista.lastElementChild as HTMLElement | null;
        lista.style.setProperty('--fim-estrada', `${ultimo ? ultimo.offsetHeight - MARCADOR_TOPO : 0}px`);
      };
      observador = new ResizeObserver(medir);
      observador.observe(lista);
      medir();
    });
    this.destroyRef.onDestroy(() => observador?.disconnect());
  }

  ngOnInit() {
    // saiu da tela no meio do arraste: solta os ouvintes e para a rolagem
    this.destroyRef.onDestroy(() => {
      if (this.arraste) cancelAnimationFrame(this.arraste.quadro);
      this.arraste?.soltar();
    });
    this.routeId = this.activatedRoute.snapshot.paramMap.get('routeId') ?? '';
    this.carregar();
  }

  carregar() {
    this.carregando.set(true);
    this.erroCarregar.set('');
    forkJoin({
      rota: this.routeService.getRoute(this.routeId),
      pontos: this.routeService.getStops(this.routeId),
      pendencias: this.routeService.getPending(this.routeId),
      // sem as opcoes ou as escolas a rota ainda abre: so perde os apelidos dos enderecos e o nome da escola
      opcoes: this.semErro(this.routeService.getStopOptions(), { students: [] }),
      escolas: this.semErro(this.schoolService.getMySchools(), []),
    }).subscribe({
      next: ({ rota, pontos, pendencias, opcoes, escolas }) => {
        this.rota.set(rota);
        this.pontos.set(pontos ?? []);
        this.pendencias.set(pendencias ?? []);
        this.alunos.set(opcoes?.students ?? []);
        this.escolas = new Map((escolas ?? []).map((e) => [e.id, e.name]));
        this.carregando.set(false);
        // ao abrir, rota pronta nao avisa: o botao "Iniciar rota" ja mostra (o pop-up cobria o topo)
        this.avisarSituacao(true);
        this.carregarEscolasDosAlunos();
      },
      error: (erro: HttpErrorResponse) => {
        this.erroCarregar.set(erro?.status === 403 || erro?.status === 404 ? 'semAcesso' : 'erro');
        this.carregando.set(false);
      },
    });
  }

  // escola de cada aluno: vem do contrato ativo
  private carregarEscolasDosAlunos() {
    this.contractService
      .getMyContracts({ status: 'ACTIVE', size: 200 })
      .pipe(catchError(() => of(null)))
      .subscribe((pagina) => {
        const mapa: Record<string, string> = {};
        for (const c of pagina?.content ?? []) {
          mapa[c.studentId] = c.schoolId;
          if (!this.escolas.has(c.schoolId)) this.escolas.set(c.schoolId, c.schoolName);
        }
        this.escolaDoAluno.set(mapa);
      });
  }

  abrirDesembarque() {
    this.marcadosDesembarque.set(new Set(this.gruposDesembarque().flatMap((g) => g.alunos.map((a) => a.studentId))));
    this.desembarcando.set(true);
  }

  fecharDesembarque() {
    if (!this.enviando()) this.desembarcando.set(false);
  }

  alternarDesembarque(studentId: string) {
    this.marcadosDesembarque.update((s) => {
      const novo = new Set(s);
      if (novo.has(studentId)) novo.delete(studentId);
      else novo.add(studentId);
      return novo;
    });
  }

  // cada um desce na escola do contrato: usa o ponto dela se ja existe; se nao, o primeiro cria e os outros entram nele.
  // os dias sao os que faltam desembarque (pendencia do backend). um pedido por aluno, um depois do outro
  adicionarDesembarques() {
    const marcados = this.marcadosDesembarque();
    const tarefas = this.gruposDesembarque().flatMap((g) => g.alunos.filter((a) => marcados.has(a.studentId)).map((a) => ({ escolaId: g.escolaId, a })));
    if (!tarefas.length || this.enviando()) return;
    this.enviando.set(true);
    let ultima: routeEdit | null = null;
    let falhas = 0;
    from(tarefas)
      .pipe(
        concatMap(({ escolaId, a }) => {
          const ponto = this.pontos().find((p) => p.schools.some((s) => s.schoolId === escolaId));
          const base = { action: 'ALIGHTING' as const, locationType: 'SCHOOL' as const, weekdays: a.missingAlighting };
          const pedido = ponto ? { ...base, routeStopId: ponto.id } : { ...base, newStop: true };
          return this.routeService.addStudent(this.routeId, a.studentId, pedido).pipe(
            // ja redesenha: o proximo da mesma escola acha o ponto novo
            tap((edicao) => {
              ultima = edicao;
              this.pontos.set(edicao.stops ?? []);
            }),
            catchError(() => {
              falhas++;
              return of(null);
            }),
          );
        }),
        toArray(),
      )
      .subscribe(() => {
        this.enviando.set(false);
        this.desembarcando.set(false);
        if (ultima) this.aplicar(ultima);
        if (falhas) this.popUpService.show(`${tarefas.length - falhas} desembarques adicionados. ${falhas} não entraram.`, 'warning', 'Alguns não entraram');
      });
  }

  // junta as paradas repetidas da escola numa so: muda os alunos pra ela e apaga as que sobram.
  // so desembarque: fica na ultima (desce depois de todos embarcarem); se tem embarque, na primeira
  juntarParadas(grupo: { escolaId: string; pontos: routeStop[] }) {
    if (this.enviando()) return;
    const soDesce = grupo.pontos.every((p) => p.students.every((a) => a.action === 'ALIGHTING'));
    const destino = soDesce ? grupo.pontos[grupo.pontos.length - 1] : grupo.pontos[0];
    const outros = grupo.pontos.filter((p) => p !== destino);
    const passos: Observable<routeEdit>[] = [
      ...outros.flatMap((p) => p.students.map((a) => this.routeService.moveStudent(a.id, destino.id))),
      ...outros.flatMap((p) => p.schools.filter((s) => s.schoolId === grupo.escolaId).map((s) => this.routeService.removeSchoolStop(s.id))),
      // a parada so sai se ficou vazia (nao tinha outra escola)
      ...outros.filter((p) => p.schools.every((s) => s.schoolId === grupo.escolaId)).map((p) => this.routeService.removeStop(p.id)),
    ];
    this.enviando.set(true);
    from(passos)
      .pipe(
        concatMap((passo) => passo),
        toArray(),
      )
      .subscribe({
        next: (edicoes) => {
          this.enviando.set(false);
          this.aplicarSemAviso(edicoes[edicoes.length - 1]);
          this.popUpService.show('Os alunos da escola agora descem na mesma parada.', 'success', 'Paradas juntadas');
        },
        error: () => {
          this.enviando.set(false);
          this.carregar();
        },
      });
  }

  private semErro<T>(pedido: Observable<T>, padrao: T): Observable<T> {
    return pedido.pipe(catchError(() => of(padrao)));
  }

  // toda alteracao devolve a rota inteira: redesenha e avisa como ficou
  private aplicar(edicao: routeEdit) {
    this.pontos.set(edicao.stops ?? []);
    this.pendencias.set(edicao.pending ?? []);
    this.avisarSituacao();
  }

  // Figma: rota vazia (info), com pendencia (aviso) e pronta (sucesso), no pop-up do topo
  private avisarSituacao(abrindo = false) {
    if (!this.pontos().length) {
      this.popUpService.show(
        'Cada aluno precisa de embarque e desembarque em cada dia. O embarque vem antes na ordem.',
        'info',
        'Monte a rota aluno por aluno',
      );
    } else if (this.pendencias().length) {
      this.popUpService.show(this.pendencias().map(textoPendencia).join('; '), 'warning', 'A viagem ainda não pode iniciar');
    } else if (!abrindo) {
      this.popUpService.show('Nenhuma pendência. A viagem já pode iniciar.', 'success', 'Rota pronta');
    }
  }

  // ---------- textos ----------

  // "Bela Vista" (casa) ou "Fatec Ipiranga" (ponto de escola)
  complemento(ponto: routeStop): string {
    if (ponto.schools.length) {
      const nomes = ponto.schools.map((s) => this.nomeEscola(s)).filter(Boolean);
      if (nomes.length) return nomes.join(', ');
    }
    return ponto.address.neighborhood ?? '';
  }

  nomeEscola(escola: schoolStop): string {
    return this.escolas.get(escola.schoolId) ?? 'Escola';
  }

  // "Casa · Seg a Sex": apelido do endereco do aluno (ou "Escola") e os dias
  detalhe(aluno: studentStop, ponto: routeStop): string {
    return `${this.lugar(aluno, ponto)} · ${resumoDias(aluno.weekdays)}`;
  }

  private lugar(aluno: studentStop, ponto: routeStop): string {
    if (aluno.locationType === 'SCHOOL') return 'Escola';
    const opcao = this.alunos().find((a) => a.studentId === aluno.studentId);
    const endereco = opcao?.addresses.find((e) => e.address.id === ponto.address.id);
    if (endereco?.label) return endereco.label;
    return aluno.locationType === 'HOME' ? 'Casa' : 'Outro lugar';
  }

  // "ABC1D23 · Sprinter · Prata"
  van(): string {
    const v = this.rota()?.vehicle;
    if (!v) return '';
    return [v.plate, formatarCrlv(v.model), formatarCrlv(v.color)].filter(Boolean).join(' · ');
  }

  // ---------- adicionar aluno ----------

  abrirAdicionar() {
    // as opcoes mudam quando um contrato comeca: busca de novo
    this.adicionando.set(true);
    this.routeService.getStopOptions().subscribe({
      next: (opcoes) => this.alunos.set(opcoes?.students ?? []),
      error: () => {},
    });
  }

  alunoAdicionado(edicao: routeEdit) {
    this.adicionando.set(false);
    this.aplicar(edicao);
  }

  // ---------- aluno no ponto ----------

  abrirAluno(aluno: studentStop, ponto: routeStop) {
    this.diasDoAluno.set(ordenarDias(aluno.weekdays));
    this.pontoDestino.set(ponto);
    this.alunoAberto.set({ aluno, ponto });
  }

  fecharAluno() {
    if (!this.enviando()) this.alunoAberto.set(null);
  }

  alternarDia(dia: WeekDay) {
    this.diasDoAluno.update((dias) => (dias.includes(dia) ? dias.filter((d) => d !== dia) : [...dias, dia]));
  }

  // ponto atual e os outros pontos do mesmo tipo (escola com escola; casa/outro num endereco do aluno)
  pontosParaMover(): routeStop[] {
    const aberto = this.alunoAberto();
    if (!aberto) return [];
    const { aluno, ponto } = aberto;
    if (aluno.locationType === 'SCHOOL') {
      return this.pontos().filter((p) => p.id === ponto.id || p.schools.length > 0);
    }
    const enderecos = new Set(
      (this.alunos().find((a) => a.studentId === aluno.studentId)?.addresses ?? []).map((e) => e.address.id),
    );
    return this.pontos().filter((p) => p.id === ponto.id || (!p.schools.length && enderecos.has(p.address.id)));
  }

  salvarAluno() {
    const aberto = this.alunoAberto();
    if (!aberto || this.enviando()) return;
    const { aluno, ponto } = aberto;
    const diasMudaram = ordenarDias(aluno.weekdays).join() !== ordenarDias(this.diasDoAluno()).join();
    const destino = this.pontoDestino();
    const mudouDePonto = !!destino && destino.id !== ponto.id;
    if (!diasMudaram && !mudouDePonto) {
      this.alunoAberto.set(null);
      return;
    }

    this.enviando.set(true);
    const mover = () => {
      if (!mudouDePonto) return;
      this.routeService.moveStudent(aluno.id, destino!.id).subscribe({
        next: (edicao) => this.concluir(edicao, () => this.alunoAberto.set(null)),
        error: () => this.enviando.set(false),
      });
    };
    if (diasMudaram) {
      this.routeService.updateWeekdays(aluno.id, this.diasDoAluno()).subscribe({
        next: (edicao) => {
          if (mudouDePonto) {
            this.aplicarSemAviso(edicao);
            mover();
          } else {
            this.concluir(edicao, () => this.alunoAberto.set(null));
          }
        },
        error: () => this.enviando.set(false),
      });
    } else {
      mover();
    }
  }

  async tirarAluno() {
    const aberto = this.alunoAberto();
    if (!aberto || this.enviando()) return;
    const sim = await this.confirmacao.perguntar({
      titulo: `Tirar ${aberto.aluno.studentName} do ponto?`,
      acao: 'Tirar do ponto',
    });
    if (!sim || this.enviando()) return;
    this.enviando.set(true);
    this.routeService.removeStudentStop(aberto.aluno.id).subscribe({
      next: (edicao) => this.concluir(edicao, () => this.alunoAberto.set(null)),
      error: () => this.enviando.set(false),
    });
  }

  // ---------- ponto ----------

  abrirPonto(ponto: routeStop) {
    this.pontoAberto.set(ponto);
  }

  fecharPonto() {
    if (!this.enviando()) this.pontoAberto.set(null);
  }

  // ---------- ordem: arrastar a parada ----------

  // muda a parada de lugar na hora (na tela) e manda pro backend troca a troca com a vizinha:
  // o backend so troca duas paradas. Cada passo e valido se o final for (a parada so cruza as do meio).
  // se o backend recusar (desembarque antes do embarque, popup global), recarrega a rota
  moverPonto(de: number, para: number) {
    const lista = this.pontos();
    if (this.enviando() || de === para || para < 0 || para >= lista.length) return;
    const ponto = lista[de];
    const nova = [...lista];
    nova.splice(de, 1);
    nova.splice(para, 0, ponto);
    this.pontos.set(nova.map((p, i) => ({ ...p, orderIndex: i + 1 })));

    const passo = para > de ? 1 : -1;
    const destinos: number[] = [];
    for (let i = de + passo; i !== para + passo; i += passo) destinos.push(i + 1);

    this.enviando.set(true);
    from(destinos)
      .pipe(
        concatMap((orderIndex) => this.routeService.swapOrder(ponto.id, orderIndex)),
        toArray(),
      )
      .subscribe({
        // mudar a ordem nao mostra o pop-up da situacao da rota (pronta, com pendencia...)
        next: (edicoes) => {
          this.enviando.set(false);
          this.aplicarSemAviso(edicoes[edicoes.length - 1]);
        },
        error: () => {
          this.enviando.set(false);
          this.carregar();
        },
      });
  }

  iniciarArraste(evento: PointerEvent, indice: number) {
    if (this.enviando() || this.arraste || evento.button !== 0) return;
    const lista = this.listaPontos()?.nativeElement;
    if (!lista) return;
    evento.preventDefault();
    const alca = evento.currentTarget as HTMLElement;
    const itens = [...lista.querySelectorAll<HTMLElement>(':scope > .ponto')];
    if (itens.length < 2) return;
    const caixas = itens.map((el) => el.getBoundingClientRect());
    const vao = caixas[1].top - caixas[0].bottom;
    alca.setPointerCapture?.(evento.pointerId);

    const mover = (e: PointerEvent) => {
      if (e.pointerId !== evento.pointerId || !this.arraste) return;
      this.arraste.y = e.clientY;
      this.posicionar();
    };
    const soltar = (e: PointerEvent) => {
      if (e.pointerId !== evento.pointerId) return;
      this.terminarArraste();
    };
    alca.addEventListener('pointermove', mover);
    alca.addEventListener('pointerup', soltar);
    alca.addEventListener('pointercancel', soltar);

    this.arraste = {
      itens,
      caixas,
      de: indice,
      para: indice,
      inicioY: evento.clientY,
      y: evento.clientY,
      rolagemInicial: window.scrollY,
      passo: caixas[indice].height + vao,
      quadro: 0,
      soltar: () => {
        alca.removeEventListener('pointermove', mover);
        alca.removeEventListener('pointerup', soltar);
        alca.removeEventListener('pointercancel', soltar);
      },
    };
    this.arrastando.set(indice);
    navigator.vibrate?.(10);
    this.rolarPerto();
  }

  // a parada segue o dedo; as outras abrem espaco onde ela vai cair
  private posicionar() {
    const a = this.arraste;
    if (!a) return;
    const dy = a.y - a.inicioY + (window.scrollY - a.rolagemInicial);
    a.itens[a.de].style.transform = `translateY(${dy}px)`;
    const centro = a.caixas[a.de].top + a.caixas[a.de].height / 2 + dy;
    let para = a.de;
    a.caixas.forEach((c, j) => {
      const meio = c.top + c.height / 2;
      if (j > a.de && centro > meio) para = Math.max(para, j);
      if (j < a.de && centro < meio) para = Math.min(para, j);
    });
    a.para = para;
    a.itens.forEach((el, j) => {
      if (j === a.de) return;
      const desloca = j > a.de && j <= para ? -a.passo : j < a.de && j >= para ? a.passo : 0;
      el.style.transform = desloca ? `translateY(${desloca}px)` : '';
    });
  }

  // dedo perto da borda (em cima ou dos botoes de baixo): a tela rola sozinha
  private rolarPerto() {
    const a = this.arraste;
    if (!a) return;
    const borda = 90;
    const baixo = window.innerHeight - 180;
    const vel = a.y < borda ? -Math.ceil((borda - a.y) / 8) : a.y > baixo ? Math.ceil((a.y - baixo) / 8) : 0;
    if (vel) {
      window.scrollBy(0, vel);
      this.posicionar();
    }
    a.quadro = requestAnimationFrame(() => this.rolarPerto());
  }

  private terminarArraste() {
    const a = this.arraste;
    if (!a) return;
    cancelAnimationFrame(a.quadro);
    a.soltar();
    this.arraste = null;
    // tira os deslocamentos sem animar: a lista ja vai renderizar na ordem nova
    const lista = this.listaPontos()?.nativeElement;
    lista?.classList.add('sem-transicao');
    a.itens.forEach((el) => (el.style.transform = ''));
    this.arrastando.set(null);
    if (a.para !== a.de) this.moverPonto(a.de, a.para);
    requestAnimationFrame(() => requestAnimationFrame(() => lista?.classList.remove('sem-transicao')));
  }

  async tirarEscola(escola: schoolStop) {
    if (this.enviando()) return;
    const sim = await this.confirmacao.perguntar({
      titulo: `Tirar ${this.nomeEscola(escola)} do ponto?`,
      acao: 'Tirar escola',
    });
    if (!sim || this.enviando()) return;
    this.enviando.set(true);
    // 409 se algum aluno da escola ainda embarca ou desce aqui (popup global)
    this.routeService.removeSchoolStop(escola.id).subscribe({
      next: (edicao) =>
        this.concluir(edicao, () => {
          const atual = this.pontoAberto();
          this.pontoAberto.set(edicao.stops.find((p) => p.id === atual?.id) ?? null);
        }),
      error: () => this.enviando.set(false),
    });
  }

  async apagarPonto() {
    const ponto = this.pontoAberto();
    if (!ponto || this.enviando()) return;
    // Figma "Ponto · ordem e apagar": o backend da 409 com aluno ou escola, entao avisa antes
    if (ponto.students.length || ponto.schools.length) {
      this.popUpService.show('Tire antes os alunos e as escolas dele.', 'warning', 'Para apagar este ponto');
      return;
    }
    const sim = await this.confirmacao.perguntar({ titulo: `Apagar o ponto ${ponto.orderIndex}?`, acao: 'Apagar ponto' });
    if (!sim || this.enviando()) return;
    this.enviando.set(true);
    this.routeService.removeStop(ponto.id).subscribe({
      next: (edicao) => this.concluir(edicao, () => this.pontoAberto.set(null)),
      error: () => this.enviando.set(false),
    });
  }

  // ---------- van padrao ----------

  abrirVan() {
    this.vanEscolhida.set(this.rota()?.vehicle?.id ?? null);
    this.trocandoVan.set(true);
    this.carregandoVans.set(true);
    this.vehicleService.getMyVehicles().subscribe({
      next: (vans) => {
        this.vans.set(vans ?? []);
        this.carregandoVans.set(false);
      },
      error: () => {
        this.vans.set([]);
        this.carregandoVans.set(false);
        this.popUpService.show('Não foi possível carregar as vans.', 'error');
      },
    });
  }

  fecharVan() {
    if (!this.enviando()) this.trocandoVan.set(false);
  }

  // "Sprinter"
  modelo(van: vehicleResponse): string {
    return formatarCrlv(van.model);
  }

  // "Prata · 15 lugares"
  detalheVan(van: vehicleResponse): string {
    return [formatarCrlv(van.color), van.capacity ? `${van.capacity} lugares` : ''].filter(Boolean).join(' · ');
  }

  salvarVan() {
    const vanId = this.vanEscolhida();
    if (this.enviando()) return;
    if (!vanId || vanId === this.rota()?.vehicle?.id) {
      this.trocandoVan.set(false);
      return;
    }
    this.enviando.set(true);
    this.routeService.setVehicle(this.routeId, vanId).subscribe({
      next: (rota) => {
        this.enviando.set(false);
        this.rota.set(rota);
        this.trocandoVan.set(false);
      },
      error: () => this.enviando.set(false),
    });
  }

  // ---------- viagem ----------

  // a execucao da viagem e a etapa 8 (/driver-route/:routeId)
  // "Iniciar rota" ja inicia: a tela da viagem comeca sozinha (sem pedir de novo) e abre o mapa
  iniciar() {
    this.router.navigate(['/driver-route', this.routeId], { queryParams: { iniciar: 1 } });
  }

  // a lista das rotas e a home
  voltar() {
    this.router.navigate(['/driver-home']);
  }

  private aplicarSemAviso(edicao: routeEdit) {
    this.pontos.set(edicao.stops ?? []);
    this.pendencias.set(edicao.pending ?? []);
  }

  private concluir(edicao: routeEdit, depois: () => void) {
    this.enviando.set(false);
    this.aplicar(edicao);
    depois();
  }
}

// centro do marcador (numero da parada) a partir do topo do card, igual ao CSS (.ordem: top 12px + 14px)
const MARCADOR_TOPO = 27;

// estado do arraste de uma parada (posicoes medidas quando o dedo encosta)
interface Arraste {
  itens: HTMLElement[];
  caixas: DOMRect[];
  de: number;
  para: number;
  inicioY: number;
  y: number;
  rolagemInicial: number;
  passo: number;
  quadro: number;
  soltar: () => void;
}
