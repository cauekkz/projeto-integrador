import { Skeleton } from '../../../shared/skeleton/skeleton';
import { Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { FormsModule } from '@angular/forms';
import { NgTemplateOutlet } from '@angular/common';
import { Observable, catchError, forkJoin, of, throwError } from 'rxjs';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { TrailHeader } from '../../../shared/trail-header/trail-header';
import { Footer } from '../../../shared/footer/footer';
import { StatusTopbar } from '../../../shared/status-topbar/status-topbar';
import { PopUpService } from '../../../shared/pop-up';
import { RouteCard } from '../../../components/driver/route/route-card/route-card';
import { PontoNoMapa, TripMap } from '../../../components/common/trip/trip-map/trip-map';
import { ProfileSelect } from '../../../components/common/profile-select/profile-select';
import { AuthService } from '../../../services/auth.service';
import { NotificationService } from '../../../services/notification.service';
import { RouteService } from '../../../services/route.service';
import { StorageService } from '../../../services/storage.service';
import { TripService, TripSocket } from '../../../services/trip.service';
import { VehicleService } from '../../../services/vehicle.service';
import { routeResponse, routeStop } from '../../../services/dto/route/route.dto';
import { vehicleResponse } from '../../../services/dto/vehicle/vehicle.dto';
import {
  OccurrenceType,
  SELO_DO_ALUNO,
  TIPOS_DE_OCORRENCIA,
  horaDe,
  minutosAte,
  nomeDoPonto,
  tarefaDoAluno,
  tripStudent,
  tripUpdate,
  viagemAtiva,
} from '../../../services/dto/trip/trip.dto';
import { formatarCrlv } from '../driver-vehicles/driver-vehicles';
import { GlisseVidro } from '../../../shared/glisse-vidro/glisse-vidro';
import { UserMe } from '../../../models/user.model';
import { ConfirmacaoService } from '../../../shared/confirmacao/confirmacao.service';
import { iniciaisDe } from '../../../shared/iniciais/iniciais';

import { BotaoCarregandoDirective } from '../../../shared/botao-carregando/botao-carregando.directive';
type Estado = 'carregando' | 'erro' | 'semAcesso' | 'antes' | 'aberta' | 'finalizada' | 'viagem';

// o GPS manda a posicao no maximo a cada 3 s (o backend pede de 2 a 5 s)
const INTERVALO_GPS_MS = 3000;

// Figma "Etapa 6 · Viagem" (motorista): iniciar, continuar/recomeçar, em viagem, ocorrência, todos atendidos e finalizar
@Component({
  selector: 'app-driver-route',
  standalone: true,
  imports: [BotaoCarregandoDirective, Skeleton, TrailHeader, Footer, StatusTopbar, RouteCard, TripMap, ProfileSelect, FormsModule, NgTemplateOutlet, GlisseVidro],
  templateUrl: './driver-route.html',
  styleUrls: ['../../../shared/folha.css', '../../../shared/rota.css', './driver-route.css'],
})
export class DriverRoute implements OnInit {
  private activatedRoute = inject(ActivatedRoute);
  private router = inject(Router);
  private routeService = inject(RouteService);
  private tripService = inject(TripService);
  private vehicleService = inject(VehicleService);
  private storageService = inject(StorageService);
  private authService = inject(AuthService);
  private notificacoes = inject(NotificationService);
  private popUpService = inject(PopUpService);
  private confirmacao = inject(ConfirmacaoService);
  private destroyRef = inject(DestroyRef);

  protected readonly tiposOcorrencia = TIPOS_DE_OCORRENCIA;
  protected readonly tarefa = tarefaDoAluno;
  protected readonly nomeDoPonto = nomeDoPonto;

  protected routeId = '';
  protected estado = signal<Estado>('carregando');
  protected rota = signal<routeResponse | null>(null);
  protected paradas = signal<routeStop[]>([]);
  protected vans = signal<vehicleResponse[]>([]);
  protected vanEscolhida = '';
  protected enviando = signal(false);
  protected usuario = signal<UserMe | null>(null);

  // viagem aberta
  protected tripId = signal<string | null>(null);
  protected viagem = signal<tripUpdate | null>(null);
  private socket?: TripSocket;
  private vigiaGps: number | null = null;
  private ultimoEnvio = 0;
  private ultimaPosicao: { latitude: number; longitude: number } | null = null;
  private trava: { release(): Promise<void> } | null = null;

  // telas por cima
  protected confirmandoRecomeco = signal(false);
  protected confirmandoEncerrar = signal(false);
  protected mapaExpandido = signal(false);
  protected mostrarPerfil = signal(false);
  protected registrando = signal(false);
  protected tipoOcorrencia = signal<OccurrenceType>('TRAFFIC');
  protected descricao = '';
  protected proximosAbertos = signal(false);
  protected embarcadosAbertos = signal(false);
  // alunos com um PUT em andamento (evita dois cliques)
  protected ocupados = signal<Set<string>>(new Set());
  protected fotos = signal<Record<string, string>>({});

  // ---------- o que a tela mostra durante a viagem ----------

  protected pontos = computed(() => this.viagem()?.stops ?? []);
  protected pontoAtual = computed(() => {
    const alvo = this.viagem()?.targetStopId;
    return alvo ? this.pontos().find((p) => p.routeStopId === alvo) ?? null : null;
  });
  // targetStopId null com a viagem aberta: ninguem mais precisa de parada
  protected todosAtendidos = computed(() => {
    const v = this.viagem();
    return !!v && viagemAtiva(v.status) && !v.targetStopId && this.pontos().length > 0;
  });
  protected proximos = computed(() => {
    const atual = this.pontoAtual();
    return this.pontos().filter((p) => !p.done && (!atual || p.orderIndex > atual.orderIndex));
  });
  protected alunosDaViagem = computed(() => this.cadaAlunoUmaVez(this.pontos()));

  // cada aluno uma vez (ele aparece no ponto de embarque e no de descida)
  private cadaAlunoUmaVez(pontos: { students: tripStudent[] }[]): tripStudent[] {
    const vistos = new Map<string, tripStudent>();
    for (const ponto of pontos) {
      for (const aluno of ponto.students) {
        // o de descida tem a tarefa final ("Descer na escola")
        if (!vistos.has(aluno.studentId) || aluno.action === 'ALIGHTING') vistos.set(aluno.studentId, aluno);
      }
    }
    return [...vistos.values()];
  }
  // viagem feita hoje: os passageiros (cada um uma vez) e se a lista esta aberta (seta do cartao da rota)
  protected passageirosDeHoje = signal<tripStudent[]>([]);
  protected passageirosAbertos = signal(false);
  protected embarcados = computed(() => this.alunosDaViagem().filter((a) => a.status === 'BOARDED'));
  protected minutos = computed(() => minutosAte(this.viagem()?.etaSeconds));
  protected van = computed(() => {
    const v = this.viagem();
    return v?.latitude != null && v?.longitude != null ? { latitude: v.latitude, longitude: v.longitude } : null;
  });
  protected marcadores = computed<PontoNoMapa[]>(() =>
    this.pontos()
      .filter((p) => p.latitude != null && p.longitude != null && !p.done)
      .map((p) => ({
        id: p.routeStopId,
        latitude: p.latitude!,
        longitude: p.longitude!,
        tipo: p.schoolNames?.length ? 'escola' : 'casa',
        alvo: p.routeStopId === this.viagem()?.targetStopId,
      })),
  );

  ngOnInit() {
    this.routeId = this.activatedRoute.snapshot.paramMap.get('routeId') ?? '';
    this.authService.getMe().subscribe({ next: (u) => this.usuario.set(u), error: () => {} });
    // Figma "Pop-up · falta avisada": o pai avisou a falta nesta rota
    this.notificacoes.chegou.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((n) => {
      if (n.type === 'ABSENCE_REPORTED' && n.payload?.['routeId'] === this.routeId) {
        this.popUpService.show(n.message, 'info', n.title);
      }
    });
    this.destroyRef.onDestroy(() => this.encerrarConexao());
    this.carregar();
  }

  carregar() {
    this.estado.set('carregando');
    forkJoin({
      rota: this.routeService.getRoute(this.routeId),
      paradas: this.routeService.getStops(this.routeId),
      hoje: this.tripService.getToday(this.routeId).pipe(
        catchError((erro: HttpErrorResponse) => (erro?.status === 404 ? of(null) : throwError(() => erro))),
      ),
    }).subscribe({
      next: ({ rota, paradas, hoje }) => {
        this.rota.set(rota);
        this.paradas.set(paradas ?? []);
        if (!hoje) {
          this.estado.set('antes');
          // veio do "Iniciar rota" do editor: ja inicia, sem pedir de novo
          const jaIniciar = this.activatedRoute.snapshot.queryParamMap.has('iniciar');
          if (jaIniciar) {
            // tira o ?iniciar da URL: recarregar a pagina nao inicia de novo
            this.router.navigate([], { queryParams: {}, replaceUrl: true });
          }
          this.carregarVans(() => {
            if (jaIniciar) this.iniciar(false, true);
          });
          this.avisarTelaAberta();
        } else if (hoje.status === 'FINISHED') {
          this.mostrarFinalizada(hoje.stops);
        } else if (viagemAtiva(hoje.status) && this.activatedRoute.snapshot.queryParamMap.has('continuar')) {
          // veio do botao do meio do rodape: abre direto o mapa da viagem
          this.abrirViagem(hoje.id);
        } else if (viagemAtiva(hoje.status)) {
          this.tripId.set(hoje.id);
          this.estado.set('aberta');
          this.popUpService.show('Continue de onde parou. Se recomeçar, a viagem atual é cancelada.', 'warning', 'Esta rota já está em andamento');
        } else {
          this.estado.set('antes');
          this.carregarVans();
        }
      },
      error: (erro: HttpErrorResponse) => this.estado.set(erro?.status === 403 || erro?.status === 404 ? 'semAcesso' : 'erro'),
    });
  }

  private carregarVans(depois?: () => void) {
    this.vehicleService
      .getMyVehicles()
      .pipe(catchError(() => of([] as vehicleResponse[])))
      .subscribe((vans) => {
        this.vans.set(vans ?? []);
        this.vanEscolhida = this.rota()?.vehicle?.id ?? vans?.[0]?.id ?? '';
        depois?.();
      });
  }

  // "ABC1D23 · Sprinter"
  textoVan(van: vehicleResponse): string {
    return [van.plate, formatarCrlv(van.model)].filter(Boolean).join(' · ');
  }

  private avisarTelaAberta() {
    this.popUpService.show('O navegador não manda a localização com a aba fechada ou a tela apagada.', 'info', 'Deixe esta tela aberta');
  }

  // pontos = os da viagem de hoje (GET today); sem eles, os do ultimo trip-update da viagem que acabou de terminar
  private mostrarFinalizada(pontos: { students: tripStudent[] }[] = this.pontos()) {
    this.passageirosDeHoje.set(this.cadaAlunoUmaVez(pontos));
    this.passageirosAbertos.set(false);
    this.carregarFotos({ stops: pontos });
    this.estado.set('finalizada');
    this.popUpService.show('Esta rota já foi feita hoje e não pode ser iniciada de novo.', 'success', 'Rota de hoje finalizada');
  }

  // ---------- iniciar, continuar e recomeçar ----------

  // semPerguntar: o motorista ja tocou em "Iniciar rota" no editor
  async iniciar(recomecar = false, semPerguntar = false) {
    if (this.enviando()) return;
    // recomecar ja passou pela confirmacao "Recomeçar do zero?"
    if (
      !recomecar &&
      !semPerguntar &&
      !(await this.confirmacao.perguntar({
        titulo: 'Iniciar a rota?',
        texto: 'A viagem de hoje começa e os responsáveis passam a acompanhar a van no mapa.',
        acao: 'Iniciar rota',
        tom: 'azul',
      }))
    )
      return;
    this.enviando.set(true);
    const posicao = await this.pegarPosicao();
    if (!posicao) {
      this.enviando.set(false);
      return;
    }
    const pedido = { ...posicao, vehicleId: this.vanEscolhida || undefined };
    this.tripService.start(this.routeId, pedido, recomecar).subscribe({
      next: (trip) => {
        this.enviando.set(false);
        this.confirmandoRecomeco.set(false);
        this.abrirViagem(trip.id);
      },
      error: (erro: HttpErrorResponse) => {
        this.enviando.set(false);
        this.confirmandoRecomeco.set(false);
        // 409: ja existe viagem hoje (outro aparelho) ou ja foi finalizada: mostra como esta agora
        if (erro?.status === 409) this.carregar();
      },
    });
  }

  fecharRecomeco() {
    if (!this.enviando()) this.confirmandoRecomeco.set(false);
  }

  continuar() {
    const id = this.tripId();
    if (id) this.abrirViagem(id);
  }

  private abrirViagem(tripId: string) {
    this.encerrarConexao();
    this.tripId.set(tripId);
    this.viagem.set(null);
    this.estado.set('viagem');
    this.socket = this.tripService.conectar(tripId);
    this.socket.atualizacoes.subscribe((atualizacao) => this.receber(atualizacao));
    this.socket.fechou.subscribe(() => this.estado.set('semAcesso'));
    this.vigiarGps();
    this.travarTela();
  }

  private receber(atualizacao: tripUpdate) {
    if (atualizacao.tripId !== this.tripId()) return;
    if (atualizacao.status === 'FINISHED') {
      this.encerrarConexao();
      this.mostrarFinalizada();
      return;
    }
    if (atualizacao.status === 'CANCELED') {
      // recomeçaram em outro aparelho: busca a viagem nova
      this.encerrarConexao();
      this.carregar();
      return;
    }
    this.viagem.set(atualizacao);
    this.carregarFotos(atualizacao);
  }

  // ---------- GPS e tela ligada ----------

  private pegarPosicao(): Promise<{ latitude: number; longitude: number } | null> {
    return new Promise((resolver) => {
      if (!navigator.geolocation) {
        this.avisarSemLocalizacao();
        resolver(null);
        return;
      }
      navigator.geolocation.getCurrentPosition(
        (p) => {
          this.ultimaPosicao = { latitude: p.coords.latitude, longitude: p.coords.longitude };
          resolver(this.ultimaPosicao);
        },
        () => {
          this.avisarSemLocalizacao();
          resolver(null);
        },
        { enableHighAccuracy: true, timeout: 15000 },
      );
    });
  }

  private avisarSemLocalizacao() {
    this.popUpService.show('Permita o uso da localização para iniciar a rota.', 'error', 'Sem acesso à localização');
  }

  private vigiarGps() {
    if (!navigator.geolocation || this.vigiaGps !== null) return;
    this.vigiaGps = navigator.geolocation.watchPosition(
      (p) => {
        this.ultimaPosicao = { latitude: p.coords.latitude, longitude: p.coords.longitude };
        const agora = Date.now();
        if (agora - this.ultimoEnvio < INTERVALO_GPS_MS) return;
        this.ultimoEnvio = agora;
        this.socket?.enviarPosicao(p.coords.latitude, p.coords.longitude);
      },
      (erro) => {
        if (erro.code === erro.PERMISSION_DENIED) this.avisarSemLocalizacao();
      },
      { enableHighAccuracy: true, maximumAge: 2000 },
    );
  }

  // Wake Lock: a tela nao apaga durante a viagem (o navegador solta ao trocar de aba)
  private async travarTela() {
    const wakeLock = (navigator as Navigator & { wakeLock?: { request(tipo: 'screen'): Promise<{ release(): Promise<void> }> } }).wakeLock;
    if (!wakeLock || this.trava) return;
    try {
      this.trava = await wakeLock.request('screen');
      const voltar = () => {
        if (document.visibilityState === 'visible' && this.estado() === 'viagem') {
          this.trava = null;
          this.travarTela();
        }
      };
      document.addEventListener('visibilitychange', voltar, { once: true });
    } catch {
      this.trava = null;
    }
  }

  private encerrarConexao() {
    this.socket?.fechar();
    this.socket = undefined;
    if (this.vigiaGps !== null) navigator.geolocation?.clearWatch(this.vigiaGps);
    this.vigiaGps = null;
    this.trava?.release().catch(() => {});
    this.trava = null;
  }

  // ---------- alunos ----------

  seloDe(aluno: tripStudent) {
    return SELO_DO_ALUNO[aluno.status] ?? SELO_DO_ALUNO.PENDING;
  }

  podeEmbarcar(aluno: tripStudent): boolean {
    return aluno.action === 'BOARDING' && aluno.status === 'PENDING';
  }

  podeDescer(aluno: tripStudent): boolean {
    return aluno.action === 'ALIGHTING' && aluno.status === 'BOARDED';
  }

  // "Entregue às 07:32" depois da entrega; senao a tarefa ("Buscar em casa")
  detalheFinal(aluno: tripStudent): string {
    if (aluno.status === 'DELIVERED' && aluno.deliveredAt) return `Entregue às ${horaDe(aluno.deliveredAt)}`;
    if (aluno.status === 'ABSENT') return 'Não foi hoje';
    return tarefaDoAluno(aluno);
  }

  marcar(aluno: tripStudent, acao: 'board' | 'alight' | 'absent') {
    const tripId = this.tripId();
    if (!tripId || this.ocupados().has(aluno.studentId)) return;
    this.ocupados.update((s) => new Set(s).add(aluno.studentId));
    const pedido: Observable<unknown> =
      acao === 'board'
        ? this.tripService.board(tripId, aluno.studentId)
        : acao === 'alight'
          ? this.tripService.alight(tripId, aluno.studentId)
          : this.tripService.absent(tripId, aluno.studentId);
    const liberar = () =>
      this.ocupados.update((s) => {
        const novo = new Set(s);
        novo.delete(aluno.studentId);
        return novo;
      });
    // a tela muda com o trip-update que chega logo depois
    pedido.subscribe({
      next: liberar,
      error: (erro: HttpErrorResponse) => {
        liberar();
        const mensagem = erro?.error?.message || 'Tente de novo em instantes.';
        // Figma "erro 409 longe do ponto": a mensagem traz a distancia
        if (erro?.status === 409) this.popUpService.show(mensagem, 'warning', 'Não foi possível concluir');
        else this.popUpService.show(mensagem, 'error');
      },
    });
  }

  iniciais(nome: string | null | undefined): string {
    return iniciaisDe(nome);
  }

  private carregarFotos(atualizacao: { stops?: { students: tripStudent[] }[] | null }) {
    const conhecidas = this.fotos();
    const chaves = new Set<string>();
    for (const ponto of atualizacao.stops ?? []) {
      for (const aluno of ponto.students) if (aluno.photoKey && !conhecidas[aluno.photoKey]) chaves.add(aluno.photoKey);
    }
    for (const chave of chaves) {
      this.fotos.update((f) => ({ ...f, [chave]: '' }));
      this.storageService.getDownloadUrl(chave).subscribe({
        next: (url) => this.fotos.update((f) => ({ ...f, [chave]: url })),
        error: () => {},
      });
    }
  }

  // ---------- ocorrência ----------

  abrirOcorrencia() {
    this.tipoOcorrencia.set('TRAFFIC');
    this.descricao = '';
    this.registrando.set(true);
  }

  fecharOcorrencia() {
    if (!this.enviando()) this.registrando.set(false);
  }

  registrar() {
    const tripId = this.tripId();
    if (!tripId || this.enviando()) return;
    const descricao = this.descricao.trim();
    if (!descricao) {
      this.popUpService.show('Conte o que aconteceu', 'warning', 'Campos Incompletos');
      return;
    }
    this.enviando.set(true);
    this.tripService.addOccurrence(tripId, this.tipoOcorrencia(), descricao).subscribe({
      next: () => {
        this.enviando.set(false);
        this.registrando.set(false);
        this.popUpService.show('Os responsáveis foram avisados.', 'success', 'Ocorrência registrada');
      },
      error: () => this.enviando.set(false),
    });
  }

  // ---------- finalizar ----------

  finalizar() {
    const tripId = this.tripId();
    if (!tripId || this.enviando()) return;
    this.enviando.set(true);
    this.tripService.finish(tripId).subscribe({
      next: () => {
        this.enviando.set(false);
        this.encerrarConexao();
        this.mostrarFinalizada();
      },
      error: () => this.enviando.set(false),
    });
  }

  // volta pra escolha da viagem (aba do meio)
  fecharEncerrar() {
    if (!this.enviando()) this.confirmandoEncerrar.set(false);
  }

  // "Encerrar rota" no meio da viagem: o back aceita finalizar a qualquer momento com a viagem aberta
  encerrar() {
    this.confirmandoEncerrar.set(false);
    this.finalizar();
  }

  voltar() {
    this.router.navigate(['/driver-routes']);
  }
}
