import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { Observable, catchError, forkJoin, of } from 'rxjs';
import { TrailHeader } from '../../shared/trail-header/trail-header';
import { Footer } from '../../shared/footer/footer';
import { PopUpService } from '../../shared/pop-up';
import { RouteService } from '../../services/route.service';
import { SchoolService } from '../../services/school.service';
import { VehicleService } from '../../services/vehicle.service';
import { vehicleResponse } from '../../services/dto/vehicle/vehicle.dto';
import { AddStudentToRoute } from '../../components/route/add-student-to-route/add-student-to-route';
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
} from '../../services/dto/route/route.dto';

// Figma "Rotas · Motorista": editor da rota (vazia, com pendencia, pronta), folhas do aluno, do ponto e da van
@Component({
  selector: 'app-driver-route-editor',
  standalone: true,
  imports: [TrailHeader, Footer, AddStudentToRoute],
  templateUrl: './driver-route-editor.html',
  styleUrls: ['../../shared/folha.css', '../../shared/rota.css', './driver-route-editor.css'],
})
export class DriverRouteEditor implements OnInit {
  private routeService = inject(RouteService);
  private schoolService = inject(SchoolService);
  private vehicleService = inject(VehicleService);
  private popUpService = inject(PopUpService);
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

  // "Adicionar aluno" (3 passos em tela cheia)
  protected adicionando = signal(false);

  // folha do aluno no ponto
  protected alunoAberto = signal<{ aluno: studentStop; ponto: routeStop } | null>(null);
  protected diasDoAluno = signal<WeekDay[]>([]);
  protected pontoDestino = signal<routeStop | null>(null);

  // folha do ponto
  protected pontoAberto = signal<routeStop | null>(null);
  protected novaPosicao = signal(1);

  // folha da van padrao
  protected trocandoVan = signal(false);
  protected vans = signal<vehicleResponse[]>([]);
  protected carregandoVans = signal(false);
  protected vanEscolhida = signal<string | null>(null);

  ngOnInit() {
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
        this.avisarSituacao();
      },
      error: (erro: HttpErrorResponse) => {
        this.erroCarregar.set(erro?.status === 403 || erro?.status === 404 ? 'semAcesso' : 'erro');
        this.carregando.set(false);
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
  private avisarSituacao() {
    if (!this.pontos().length) {
      this.popUpService.show(
        'Cada aluno precisa de embarque e desembarque em cada dia. O embarque vem antes na ordem.',
        'info',
        'Monte a rota aluno por aluno',
      );
    } else if (this.pendencias().length) {
      this.popUpService.show(this.pendencias().map(textoPendencia).join('; '), 'warning', 'A viagem ainda não pode iniciar');
    } else {
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

  tirarAluno() {
    const aberto = this.alunoAberto();
    if (!aberto || this.enviando()) return;
    this.enviando.set(true);
    this.routeService.removeStudentStop(aberto.aluno.id).subscribe({
      next: (edicao) => this.concluir(edicao, () => this.alunoAberto.set(null)),
      error: () => this.enviando.set(false),
    });
  }

  // ---------- ponto ----------

  abrirPonto(ponto: routeStop) {
    this.novaPosicao.set(ponto.orderIndex);
    this.pontoAberto.set(ponto);
  }

  fecharPonto() {
    if (!this.enviando()) this.pontoAberto.set(null);
  }

  posicoes(): number[] {
    return this.pontos().map((p) => p.orderIndex);
  }

  salvarPosicao() {
    const ponto = this.pontoAberto();
    if (!ponto || this.enviando()) return;
    if (this.novaPosicao() === ponto.orderIndex) {
      this.pontoAberto.set(null);
      return;
    }
    this.enviando.set(true);
    // 400 se a troca deixar o desembarque de alguem antes do embarque (popup global)
    this.routeService.swapOrder(ponto.id, this.novaPosicao()).subscribe({
      next: (edicao) => this.concluir(edicao, () => this.pontoAberto.set(null)),
      error: () => this.enviando.set(false),
    });
  }

  tirarEscola(escola: schoolStop) {
    if (this.enviando()) return;
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

  apagarPonto() {
    const ponto = this.pontoAberto();
    if (!ponto || this.enviando()) return;
    // Figma "Ponto · ordem e apagar": o backend da 409 com aluno ou escola, entao avisa antes
    if (ponto.students.length || ponto.schools.length) {
      this.popUpService.show('Tire antes os alunos e as escolas dele.', 'warning', 'Para apagar este ponto');
      return;
    }
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
  iniciar() {
    this.router.navigate(['/driver-route', this.routeId]);
  }

  voltar() {
    this.router.navigate(['/driver-routes']);
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
