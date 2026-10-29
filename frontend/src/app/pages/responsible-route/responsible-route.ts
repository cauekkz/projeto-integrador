import { ChangeDetectorRef, Component, computed, inject, OnInit, signal } from '@angular/core';
import { Router } from '@angular/router';
import { NotificationService } from '../../services/notification.service';
import { AuthService } from '../../services/auth.service';
import { UserMe } from '../../models/user.model';
import { Footer } from '../../shared/footer/footer';
import { ProfileSelect } from '../../components/profile-select/profile-select';

type SituacaoParada = 'feita' | 'atual' | 'proxima';

// privacidade: o responsavel so ve as paradas do proprio dependente (casa e escola).
// paradas de outros alunos (e a garagem do motorista) chegam so como quantidade, sem nome, endereco nem horario
// TODO: o backend e quem tem que filtrar isso na resposta; o front nunca deve receber a casa dos outros
type ParadaRota =
  | { tipo: 'dependente'; titulo: string; endereco: string; horario: string; situacao: SituacaoParada }
  | { tipo: 'outras'; quantidade: number; situacao: SituacaoParada };

interface RotaDependente {
  dependente: string;
  status: 'a-caminho' | 'embarcado' | 'entregue';
  minutos: number;
  motorista: { nome: string; foto: string | null };
  van: { placa: string; descricao: string };
  paradas: ParadaRota[];
}

const STATUS_TEXTO: Record<RotaDependente['status'], string> = {
  'a-caminho': 'Motorista a caminho',
  embarcado: 'Embarcado na van',
  entregue: 'Entregue na escola',
};

// rota do motorista vista pelo responsavel: so o front por enquanto (dados de exemplo)
// TODO: trocar pela viagem do dia do dependente quando o back expuser (trip em andamento + paradas + posicao da van)
@Component({
  selector: 'app-responsible-route',
  standalone: true,
  imports: [Footer, ProfileSelect],
  templateUrl: './responsible-route.html',
  styleUrl: './responsible-route.css',
})
export class ResponsibleRoute implements OnInit {
  mostrarPerfil = false;
  usuarioLogado: UserMe | null = null;

  protected notificacoes = inject(NotificationService);
  private authService = inject(AuthService);
  private router = inject(Router);
  private cdr = inject(ChangeDetectorRef);

  readonly rotas = signal<RotaDependente[]>([
    {
      dependente: 'Ana',
      status: 'a-caminho',
      minutos: 12,
      motorista: { nome: 'Gustavo Lima', foto: null },
      van: { placa: 'AVT-1914', descricao: 'Van branca com detalhe amarelo' },
      paradas: [
        { tipo: 'outras', quantidade: 2, situacao: 'feita' },
        { tipo: 'dependente', titulo: 'Embarque da Ana', endereco: 'Sua casa · R. Libertadores, 2026', horario: '07:05', situacao: 'atual' },
        { tipo: 'outras', quantidade: 1, situacao: 'proxima' },
        { tipo: 'dependente', titulo: 'Colégio Santa Clara', endereco: 'Av. Paulista, 1500', horario: '07:30', situacao: 'proxima' },
      ],
    },
    {
      dependente: 'Pedro',
      status: 'embarcado',
      minutos: 8,
      motorista: { nome: 'Gustavo Lima', foto: null },
      van: { placa: 'AVT-1914', descricao: 'Van branca com detalhe amarelo' },
      paradas: [
        { tipo: 'dependente', titulo: 'Embarque do Pedro', endereco: 'Sua casa · R. Libertadores, 2026', horario: '07:05', situacao: 'feita' },
        { tipo: 'outras', quantidade: 1, situacao: 'atual' },
        { tipo: 'dependente', titulo: 'Escola Monteiro Lobato', endereco: 'R. Augusta, 300', horario: '07:25', situacao: 'proxima' },
      ],
    },
  ]);

  readonly selecionado = signal(0);
  readonly rota = computed(() => this.rotas()[this.selecionado()]);
  readonly statusTexto = computed(() => STATUS_TEXTO[this.rota().status]);
  // conta as paradas anonimas pela quantidade, senao a barra andaria errado
  readonly progresso = computed(() => {
    const peso = (p: ParadaRota) => (p.tipo === 'outras' ? p.quantidade : 1);
    const paradas = this.rota().paradas;
    const total = paradas.reduce((soma, p) => soma + peso(p), 0);
    const feitas = paradas.filter((p) => p.situacao === 'feita').reduce((soma, p) => soma + peso(p), 0);
    return total ? Math.round((feitas / total) * 100) : 0;
  });
  // so as paradas do dependente contam no botao "Ver paradas"
  readonly paradasDoDependente = computed(() => this.rota().paradas.filter((p) => p.tipo === 'dependente').length);
  // painel com as paradas: comeca fechado pra deixar o mapa a vista
  readonly paradasAbertas = signal(false);

  ngOnInit() {
    this.notificacoes.iniciar();
    this.authService.getMe().subscribe({
      next: (user) => {
        this.usuarioLogado = user;
        this.cdr.detectChanges();
      },
      error: (err) => console.error('Erro ao buscar usuário:', err),
    });
  }

  selecionar(indice: number) {
    this.selecionado.set(indice);
  }

  alternarParadas() {
    this.paradasAbertas.update((aberto) => !aberto);
  }

  textoOutras(quantidade: number, situacao: SituacaoParada): string {
    const paradas = quantidade === 1 ? '1 parada' : `${quantidade} paradas`;
    if (situacao === 'feita') return `${paradas} concluída${quantidade === 1 ? '' : 's'}`;
    if (situacao === 'atual') return 'Van em outra parada';
    return `${paradas} no caminho`;
  }

  iniciais(nome: string): string {
    return nome
      .split(' ')
      .filter(Boolean)
      .slice(0, 2)
      .map((parte) => parte[0].toUpperCase())
      .join('');
  }

  irParaChat() {
    this.router.navigate(['/chat'], { queryParams: { from: 'responsible-home' } });
  }

  irParaPerfil() {
    this.mostrarPerfil = true;
  }

  fecharPerfil() {
    this.mostrarPerfil = false;
  }
}
