import { Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { StatusTopbar } from '../../../shared/status-topbar/status-topbar';
import { PopUpService } from '../../../shared/pop-up';
import { PontoNoMapa, TripMap } from '../../../components/common/trip/trip-map/trip-map';
import { ProfileSelect } from '../../../components/common/profile-select/profile-select';
import { AuthService } from '../../../services/auth.service';
import { ChatWebSocketService } from '../../../services/chat.service';
import { NotificationService } from '../../../services/notification.service';
import { StorageService } from '../../../services/storage.service';
import { StudentService } from '../../../services/student.service';
import { TripService, TripSocket } from '../../../services/trip.service';
import { formatarCrlv } from '../../driver/driver-vehicles/driver-vehicles';
import { UserMe } from '../../../models/user.model';
import { horaDe, minutosAte, studentTripToday, tripStudent, tripUpdate, viagemAtiva } from '../../../services/dto/trip/trip.dto';

// avisos da viagem que viram pop-up nesta tela (Figma "ON_BOARD · pop-up de embarque")
const AVISOS_DA_VIAGEM = ['VAN_ON_THE_WAY', 'STUDENT_BOARDED', 'STUDENT_DELIVERED', 'STUDENT_ABSENT', 'OCCURRENCE'];

// "Sprinter prata · ABC1D23" (o CRLV vem "M.BENZ/SPRINTER" e "PRATA")
export function textoDaVanDaViagem(viagem: studentTripToday): string {
  const modelo = formatarCrlv((viagem.vehicleModel ?? '').split('/').pop());
  const cor = (viagem.vehicleColor ?? '').toLowerCase();
  const van = [modelo, cor].filter(Boolean).join(' ');
  return [van, viagem.vehiclePlate].filter(Boolean).join(' · ');
}

// Figma "Responsável · /responsible-route/:id": FOLLOWING, ON_BOARD, STATUS (e DONE/encerrada, sem tela no Figma)
import { Footer } from '../../../shared/footer/footer';
import { GlisseVidro } from '../../../shared/glisse-vidro/glisse-vidro';
@Component({
  selector: 'app-responsible-route',
  standalone: true,
  imports: [StatusTopbar, TripMap, ProfileSelect, Footer, GlisseVidro],
  templateUrl: './responsible-route.html',
  styleUrl: './responsible-route.css',
})
export class ResponsibleRoute implements OnInit {
  private activatedRoute = inject(ActivatedRoute);
  private router = inject(Router);
  private tripService = inject(TripService);
  private studentService = inject(StudentService);
  private storageService = inject(StorageService);
  private chatService = inject(ChatWebSocketService);
  private authService = inject(AuthService);
  private notificacoes = inject(NotificationService);
  private popUpService = inject(PopUpService);
  private destroyRef = inject(DestroyRef);

  protected tripId = signal('');
  // filho que abriu o acompanhamento (?aluno=); sem ele, o primeiro filho da viagem
  private alunoPedido: string | null = null;
  protected viagem = signal<tripUpdate | null>(null);
  protected dadosDoDia = signal<studentTripToday | null>(null);
  protected fotoMotorista = signal<string | null>(null);
  protected usuario = signal<UserMe | null>(null);
  protected mostrarPerfil = signal(false);
  // cartao de baixo: recolhido mostra so o resumo; a seta pra cima puxa os dados
  protected aberto = signal(false);
  protected abrindoChat = signal(false);
  private socket?: TripSocket;
  private buscouDados = false;

  protected aluno = computed<tripStudent | null>(() => {
    const alunos = this.viagem()?.students ?? [];
    return alunos.find((a) => a.studentId === this.alunoPedido) ?? alunos[0] ?? null;
  });
  protected primeiroNome = computed(() => (this.aluno()?.studentName ?? '').split(' ')[0]);
  protected encerrada = computed(() => {
    const status = this.viagem()?.status;
    return !!status && !viagemAtiva(status);
  });
  // o que a tela mostra: o mode do backend, mais "encerrada" (FINISHED) e "ausente" (falta nesta viagem)
  protected momento = computed<'conectando' | 'FOLLOWING' | 'ON_BOARD' | 'DONE' | 'ausente' | 'aguardando' | 'encerrada'>(() => {
    const v = this.viagem();
    if (!v) return 'conectando';
    if (this.aluno()?.status === 'DELIVERED' || v.mode === 'DONE') return 'DONE';
    if (this.aluno()?.status === 'ABSENT') return 'ausente';
    if (this.encerrada()) return 'encerrada';
    if (v.mode === 'FOLLOWING') return 'FOLLOWING';
    if (v.mode === 'ON_BOARD') return 'ON_BOARD';
    return 'aguardando';
  });
  protected textoStatus = computed(() => {
    switch (this.momento()) {
      case 'FOLLOWING':
        return 'A caminho';
      case 'ON_BOARD':
        return 'Na van';
      case 'DONE':
        return 'Entregue';
      case 'encerrada':
      case 'ausente':
        return 'Sem percurso';
      default:
        return 'Em percurso';
    }
  });
  protected minutos = computed(() => minutosAte(this.viagem()?.etaSeconds));
  // "07:02": agora + o tempo que falta
  protected previsao = computed(() => {
    const segundos = this.viagem()?.etaSeconds;
    if (segundos == null) return null;
    const chegada = new Date(Date.now() + segundos * 1000);
    return `${String(chegada.getHours()).padStart(2, '0')}:${String(chegada.getMinutes()).padStart(2, '0')}`;
  });
  protected van = computed(() => {
    const v = this.viagem();
    if (this.momento() !== 'FOLLOWING' && this.momento() !== 'ON_BOARD') return null;
    return v?.latitude != null && v?.longitude != null ? { latitude: v.latitude, longitude: v.longitude } : null;
  });
  protected caminho = computed(() => (this.momento() === 'FOLLOWING' ? this.viagem()?.path ?? null : null));
  // o destino do filho e o fim da linha (o pai nao recebe os pontos): casa ou escola
  protected destino = computed<PontoNoMapa[]>(() => {
    const linha = this.caminho();
    const aluno = this.aluno();
    if (!linha?.length || !aluno) return [];
    const [longitude, latitude] = linha[linha.length - 1];
    return [{ id: 'destino', latitude, longitude, tipo: aluno.locationType === 'SCHOOL' ? 'escola' : 'casa', alvo: true }];
  });
  protected rotuloDestino = computed(() => {
    const aluno = this.aluno();
    if (aluno?.locationType === 'SCHOOL') return 'Escola';
    return aluno?.addressLabel || 'Casa';
  });
  protected textoVan = computed(() => {
    const dados = this.dadosDoDia();
    return dados ? textoDaVanDaViagem(dados) : '';
  });

  protected readonly horaDe = horaDe;

  ngOnInit() {
    this.alunoPedido = this.activatedRoute.snapshot.queryParamMap.get('aluno');
    this.authService.getMe().subscribe({ next: (u) => this.usuario.set(u), error: () => {} });
    this.notificacoes.chegou.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((n) => {
      if (AVISOS_DA_VIAGEM.includes(n.type) && n.payload?.['tripId'] === this.tripId()) {
        this.popUpService.show(n.message, 'info', n.title);
      }
    });
    this.destroyRef.onDestroy(() => this.fecharSocket());
    // a viagem nova (recomeçada) chega pelo mesmo componente com outro :tripId
    this.activatedRoute.paramMap.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((params) => {
      this.abrir(params.get('tripId') ?? '');
    });
  }

  private abrir(tripId: string) {
    this.fecharSocket();
    this.tripId.set(tripId);
    this.viagem.set(null);
    this.buscouDados = false;
    if (this.alunoPedido) this.buscarDadosDoDia(this.alunoPedido);
    this.socket = this.tripService.conectar(tripId);
    this.socket.atualizacoes.subscribe((atualizacao) => this.receber(atualizacao));
    this.socket.fechou.subscribe(() => this.semAcesso());
  }

  private receber(atualizacao: tripUpdate) {
    if (atualizacao.tripId !== this.tripId()) return;
    if (atualizacao.status === 'CANCELED') {
      // o motorista recomeçou: a viagem nova tem outro id
      this.irParaViagemNova();
      return;
    }
    this.viagem.set(atualizacao);
    const aluno = this.aluno();
    if (!this.buscouDados && aluno) this.buscarDadosDoDia(aluno.studentId);
    if (atualizacao.status === 'FINISHED') this.fecharSocket();
  }

  // motorista e van desta viagem vem do today-trips do filho
  private buscarDadosDoDia(alunoId: string) {
    this.buscouDados = true;
    this.studentService.getTodayTrips(alunoId).subscribe({
      next: (viagens) => {
        const dados = (viagens ?? []).find((v) => v.tripId === this.tripId()) ?? null;
        this.dadosDoDia.set(dados);
        if (dados?.driverPhotoKey) {
          this.storageService.getDownloadUrl(dados.driverPhotoKey).subscribe({
            next: (url) => this.fotoMotorista.set(url),
            error: () => {},
          });
        }
      },
      error: (erro: HttpErrorResponse) => {
        if (erro?.status === 403) this.semAcesso();
      },
    });
  }

  private irParaViagemNova() {
    const alunoId = this.aluno()?.studentId ?? this.alunoPedido;
    const rotaId = this.dadosDoDia()?.routeId;
    this.fecharSocket();
    if (!alunoId) {
      this.voltar();
      return;
    }
    this.studentService.getTodayTrips(alunoId).subscribe({
      next: (viagens) => {
        const nova = (viagens ?? []).find((v) => v.tripId !== this.tripId() && viagemAtiva(v.tripStatus) && (!rotaId || v.routeId === rotaId));
        if (!nova) {
          this.voltar();
          return;
        }
        this.popUpService.show('O motorista recomeçou a rota.', 'info', 'A rota foi reiniciada');
        this.router.navigate(['/responsible-route', nova.tripId], { queryParams: { aluno: alunoId }, replaceUrl: true });
      },
      error: () => this.voltar(),
    });
  }

  // Figma "acesso removido": fechou com 1003 ou today-trips deu 403
  private semAcesso() {
    this.fecharSocket();
    this.popUpService.show('Você foi removido dos responsáveis deste filho.', 'warning', 'Você não tem mais acesso a esta viagem');
    this.voltar();
  }

  private fecharSocket() {
    this.socket?.fechar();
    this.socket = undefined;
  }

  estrelas(nota: number | null | undefined): string {
    return nota == null ? '' : nota.toFixed(1).replace('.', ',');
  }

  iniciais(nome: string | null | undefined): string {
    return (
      (nome ?? '')
        .split(' ')
        .filter(Boolean)
        .slice(0, 2)
        .map((p) => p[0].toUpperCase())
        .join('') || '?'
    );
  }

  // conversa com o motorista (GET /api/chats pelo otherUserId)
  abrirChat() {
    const motorista = this.dadosDoDia()?.driverId;
    if (!motorista || this.abrindoChat()) return;
    this.abrindoChat.set(true);
    this.chatService.findChatWith(motorista).subscribe((chatId) => {
      this.abrindoChat.set(false);
      if (chatId) this.router.navigate(['/chat-details', chatId], { queryParams: { from: 'responsible-home' } });
      else this.popUpService.show('Ainda não há conversa com este motorista.', 'info', 'Sem conversa');
    });
  }

  voltar() {
    this.router.navigate(['/responsible-home']);
  }
}
