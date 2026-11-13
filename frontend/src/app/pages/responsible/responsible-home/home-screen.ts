import { Trajeto } from '../../../shared/trajeto/trajeto';
import { Skeleton } from '../../../shared/skeleton/skeleton';
import { catchError, forkJoin, of, switchMap } from 'rxjs';
import { Component, DestroyRef, OnInit, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { HttpErrorResponse } from '@angular/common/http';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { NotificationService } from '../../../services/notification.service';
import { UserMe } from '../../../models/user.model';
import { StudentService } from '../../../services/student.service';
import { AuthService } from '../../../services/auth.service';
import { PopUpService } from '../../../shared/pop-up';
import { Footer } from '../../../shared/footer/footer';
import { ProfileSelect } from '../../../components/common/profile-select/profile-select';
import { TopoResponsavel } from '../../../shared/topo-responsavel/topo-responsavel';
import {
  SELO_DO_ALUNO,
  BoardingStatus,
  dataLocal,
  diaEMes,
  studentAbsence,
  studentRoute,
  studentTripToday,
  viagemAtiva,
} from '../../../services/dto/trip/trip.dto';
import { ConfirmacaoService } from '../../../shared/confirmacao/confirmacao.service';
import { iniciaisDe } from '../../../shared/iniciais/iniciais';

import { BotaoCarregandoDirective } from '../../../shared/botao-carregando/botao-carregando.directive';
// notificacoes que mudam o card do filho (a viagem saiu, embarcou, chegou...): busca de novo, sem polling
const TIPOS_QUE_ATUALIZAM = ['TRIP_STARTED', 'VAN_ON_THE_WAY', 'STUDENT_BOARDED', 'STUDENT_DELIVERED', 'STUDENT_ABSENT'];

// card de cada filho: as viagens de hoje e as faltas avisadas pra hoje
interface CartaoFilho {
  id: string;
  name: string;
  photoUrl: string | null;
  viagens: studentTripToday[];
  faltasHoje: studentAbsence[];
}

// folha "Não vai": hoje (rota da viagem ou do filho) ou outro dia
interface FolhaFalta {
  filho: CartaoFilho;
  hoje: boolean;
  rotas: studentRoute[];
  carregando: boolean;
}

// Figma "Responsável · /responsible-home": van ainda não saiu, viagem em andamento, duas viagens, falta avisada
@Component({
  selector: 'app-responsible-home',
  standalone: true,
  imports: [BotaoCarregandoDirective, Trajeto, Skeleton, Footer, ProfileSelect, FormsModule, TopoResponsavel],
  templateUrl: './home-screen.html',
  styleUrls: ['../../../shared/folha.css', '../../../shared/rota.css', './home-screen.css'],
})
export class HomeScreen implements OnInit {
  // badge do sino: nao lidas de verdade (GET /api/notifications/unread-count + WebSocket)
  protected notificacoes = inject(NotificationService);
  private authService = inject(AuthService);
  private studentService = inject(StudentService);
  private popUpService = inject(PopUpService);
  private confirmacao = inject(ConfirmacaoService);
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private destroyRef = inject(DestroyRef);

  protected mostrarPerfil = signal(false);
  protected usuarioLogado = signal<UserMe | null>(null);
  protected filhos = signal<CartaoFilho[]>([]);
  protected carregando = signal(true);
  protected folha = signal<FolhaFalta | null>(null);
  protected rotaEscolhida = signal<string | null>(null);
  protected dataEscolhida = '';
  protected enviando = signal(false);
  // "Ele vai sim" em andamento (id da falta)
  protected desfazendo = signal<string | null>(null);

  protected readonly diaEMes = diaEMes;

  ngOnInit() {
    this.notificacoes.iniciar();
    // voltando de "codigo de conversa"/"vincular motorista" reabre o painel do perfil
    this.mostrarPerfil.set(this.route.snapshot.queryParamMap.get('perfil') === 'aberto');

    this.authService.getMe().subscribe({
      next: (user) => this.usuarioLogado.set(user),
      error: (err) => console.error('Erro ao buscar usuário:', err),
    });
    this.carregarDependentes();

    this.notificacoes.chegou.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((n) => {
      if (TIPOS_QUE_ATUALIZAM.includes(n.type)) this.atualizarViagens();
    });
    // voltou pro app: a viagem pode ter mudado enquanto a tela estava escondida
    const aoVoltar = () => {
      if (document.visibilityState === 'visible') this.atualizarViagens();
    };
    document.addEventListener('visibilitychange', aoVoltar);
    this.destroyRef.onDestroy(() => document.removeEventListener('visibilitychange', aoVoltar));
  }

  carregarDependentes() {
    this.carregando.set(true);
    // foto do backend (photoKey -> link de leitura); sem foto, a tela mostra as iniciais
    this.studentService
      .getMyChildren({ size: 100 })
      .pipe(switchMap((pagina) => this.studentService.comFotos(pagina.content ?? [])))
      .subscribe({
        next: (lista) => {
          this.filhos.set(lista.map((f) => ({ id: f.id, name: f.name, photoUrl: f.photoUrl, viagens: [], faltasHoje: [] })));
          this.carregando.set(false);
          this.atualizarViagens();
        },
        error: () => {
          this.filhos.set([]);
          this.carregando.set(false);
        },
      });
  }

  // today-trips e faltas de cada filho (ao abrir, ao voltar pro app e quando chega notificacao de viagem)
  atualizarViagens() {
    const hoje = dataLocal();
    for (const filho of this.filhos()) {
      forkJoin({
        viagens: this.studentService.getTodayTrips(filho.id),
        faltas: this.studentService.getAbsences(filho.id).pipe(catchError(() => of([] as studentAbsence[]))),
      }).subscribe({
        next: ({ viagens, faltas }) =>
          this.mudarFilho(filho.id, {
            viagens: viagens ?? [],
            faltasHoje: (faltas ?? []).filter((f) => f.date === hoje),
          }),
        error: (erro: HttpErrorResponse) => {
          // Figma "acesso removido": tiraram ele dos responsaveis deste filho
          if (erro?.status === 403) {
            this.popUpService.show('Você foi removido dos responsáveis deste filho.', 'warning', 'Você não tem mais acesso a esta viagem');
            this.filhos.update((lista) => lista.filter((f) => f.id !== filho.id));
          }
        },
      });
    }
  }

  private mudarFilho(id: string, mudanca: Partial<CartaoFilho>) {
    this.filhos.update((lista) => lista.map((f) => (f.id === id ? { ...f, ...mudanca } : f)));
  }

  // ---------- o que o card mostra ----------

  primeiroNome(nome: string): string {
    return nome.split(' ')[0] || nome;
  }

  iniciais(nome: string | null | undefined): string {
    return iniciaisDe(nome);
  }

  selo(status: BoardingStatus) {
    return SELO_DO_ALUNO[status] ?? SELO_DO_ALUNO.PENDING;
  }

  // faltas de hoje das rotas que ainda nao sairam (a da viagem que ja saiu aparece como "Ausente")
  faltasSemViagem(filho: CartaoFilho): studentAbsence[] {
    return filho.faltasHoje.filter((f) => !filho.viagens.some((v) => v.routeId === f.routeId));
  }

  // "Acompanhar" abre a viagem aberta em que o filho ainda esta (aguardando ou na van)
  viagemParaAcompanhar(filho: CartaoFilho): studentTripToday | null {
    const abertas = filho.viagens.filter((v) => viagemAtiva(v.tripStatus));
    return (
      abertas.find((v) => v.boardingStatus === 'BOARDED') ??
      abertas.find((v) => v.boardingStatus === 'PENDING') ??
      null
    );
  }

  // viagens abertas em que ele ainda nao embarcou: da pra avisar a falta
  viagensSemEmbarque(filho: CartaoFilho): studentTripToday[] {
    return filho.viagens.filter((v) => viagemAtiva(v.tripStatus) && v.boardingStatus === 'PENDING');
  }

  // sem viagem e sem falta hoje: "A van ainda não saiu" com "Não vai hoje" e "Não vai outro dia"
  semNadaHoje(filho: CartaoFilho): boolean {
    return !filho.viagens.length && !filho.faltasHoje.length;
  }

  acompanhar(filho: CartaoFilho, viagem: studentTripToday) {
    this.router.navigate(['/responsible-route', viagem.tripId], { queryParams: { aluno: filho.id } });
  }

  // ---------- faltas ----------

  abrirFalta(filho: CartaoFilho, hoje: boolean) {
    this.dataEscolhida = '';
    // durante a viagem: so as rotas em que ele ainda nao embarcou
    const daViagem = hoje ? this.viagensSemEmbarque(filho).map((v) => ({ id: v.routeId, name: v.routeName })) : [];
    if (daViagem.length) {
      this.rotaEscolhida.set(daViagem.length === 1 ? daViagem[0].id : null);
      this.folha.set({ filho, hoje, rotas: daViagem, carregando: false });
      return;
    }
    this.rotaEscolhida.set(null);
    this.folha.set({ filho, hoje, rotas: [], carregando: true });
    this.studentService.getStudentRoutes(filho.id).subscribe({
      next: (rotas) => {
        const lista = rotas ?? [];
        if (lista.length === 1) this.rotaEscolhida.set(lista[0].id);
        this.folha.update((f) => (f ? { ...f, rotas: lista, carregando: false } : f));
      },
      error: () => this.folha.update((f) => (f ? { ...f, carregando: false } : f)),
    });
  }

  fecharFalta() {
    if (!this.enviando()) this.folha.set(null);
  }

  // amanha em diante pro "outro dia"
  amanha(): string {
    const data = new Date();
    data.setDate(data.getDate() + 1);
    return dataLocal(data);
  }

  avisarFalta() {
    const folha = this.folha();
    const rotaId = this.rotaEscolhida();
    if (!folha || this.enviando()) return;
    const data = folha.hoje ? dataLocal() : this.dataEscolhida;
    if (!rotaId || !data) {
      this.popUpService.show(folha.hoje ? 'Escolha a rota.' : 'Escolha a rota e o dia.', 'warning', 'Campos Incompletos');
      return;
    }
    this.enviando.set(true);
    this.studentService.createAbsence(folha.filho.id, rotaId, data).subscribe({
      next: (falta) => {
        this.enviando.set(false);
        this.folha.set(null);
        const quando = folha.hoje ? 'hoje' : `em ${diaEMes(falta.date)}`;
        this.popUpService.show(`O motorista foi avisado que ${this.primeiroNome(folha.filho.name)} não vai ${quando}.`, 'success', 'Falta avisada');
        this.atualizarViagens();
      },
      error: (erro: HttpErrorResponse) => {
        this.enviando.set(false);
        this.avisarErro(erro);
      },
    });
  }

  // Figma "Ele vai sim · erro 409": a viagem ja começou
  async eleVaiSim(filho: CartaoFilho, falta: studentAbsence) {
    if (this.desfazendo()) return;
    const nome = filho.name.split(' ')[0];
    const sim = await this.confirmacao.perguntar({
      titulo: `Desfazer a falta de ${nome}?`,
      texto: `A falta avisada em ${falta.routeName} é cancelada.`,
      acao: `${nome} vai sim`,
      tom: 'azul',
    });
    if (!sim || this.desfazendo()) return;
    this.desfazendo.set(falta.id);
    this.studentService.cancelAbsence(filho.id, falta.id).subscribe({
      next: () => {
        this.desfazendo.set(null);
        this.atualizarViagens();
      },
      error: (erro: HttpErrorResponse) => {
        this.desfazendo.set(null);
        this.avisarErro(erro);
        this.atualizarViagens();
      },
    });
  }

  private avisarErro(erro: HttpErrorResponse) {
    const mensagem = erro?.error?.message || 'Tente de novo em instantes.';
    this.popUpService.show(mensagem, 'warning', 'Não foi possível concluir');
  }

  // ---------- topo ----------

  // 1 dependente: o primeiro nome dele; mais: a quantidade
  rotuloDependentes(): string {
    const lista = this.filhos();
    if (lista.length === 1) return this.primeiroNome(lista[0]?.name ?? '') || 'Dependente';
    return `${lista.length} dependentes`;
  }

  abrirFluxoDependente() {
    this.router.navigate([this.filhos().length === 0 ? '/add-student' : '/dependentes']);
  }

  fecharPerfil() {
    this.mostrarPerfil.set(false);
    if (this.route.snapshot.queryParamMap.has('perfil')) {
      this.router.navigate([], { queryParams: {}, replaceUrl: true });
    }
  }
}
