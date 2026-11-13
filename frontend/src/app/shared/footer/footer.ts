import { Component, Input, OnInit, inject, signal } from '@angular/core';
import { TripService } from '../../services/trip.service';
import { StudentService } from '../../services/student.service';
import { CommonModule } from '@angular/common';
import { Router, NavigationEnd } from '@angular/router';
import { filter } from 'rxjs/operators';
import { AbaGlisse, GlisseTabBar } from '../glisse-tab-bar/glisse-tab-bar';

type Aba = 'home' | 'meio' | 'config' | '';
const POSICAO: Record<Exclude<Aba, ''>, number> = { home: 0, meio: 1, config: 2 };

// cada tela tem o proprio rodape: guarda onde a pilula estava pra ela deslizar ate a aba nova
let ultimaPosicao: number | null = null;

@Component({
  selector: 'app-footer',
  standalone: true,
  imports: [CommonModule, GlisseTabBar],
  templateUrl: './footer.html',
  styleUrl: './footer.css',
})
export class Footer implements OnInit {
  @Input() tipoUsuario: 'responsavel' | 'motorista' = 'responsavel';

  abaAtiva: Aba = 'home';
  // pilula azul por tras das abas: onde ela fica (0, 1, 2; null = nenhuma) e de onde ela veio (null = nao anda).
  // a chave recria a pilula pra animacao CSS comecar de novo
  protected posicao = signal<number | null>(null);
  protected origem = signal<number | null>(null);
  protected chave = signal(0);
  // a barra so aparece nas telas pra onde as abas levam
  protected visivel = signal(false);

  private tripService = inject(TripService);
  private studentService = inject(StudentService);
  private procurandoViagem = false;

  // rodape com a TabBar da Glisse (motorista e responsavel). Se o React nao carregar, volta pro rodape antigo
  protected glisseFalhou = signal(false);
  protected readonly abasMotorista: AbaGlisse[] = [
    { value: 'home', label: 'Início', iconClass: 'bi bi-house-door-fill' },
    { value: 'meio', label: 'Rotas', iconClass: 'icone-rota' },
    { value: 'config', label: 'Conversas', iconClass: 'bi bi-chat-dots' },
  ];
  protected readonly abasResponsavel: AbaGlisse[] = [
    { value: 'home', label: 'Início', iconClass: 'bi bi-house-door-fill' },
    { value: 'meio', label: 'Transporte', iconClass: 'bi bi-truck' },
    { value: 'config', label: 'Conversas', iconClass: 'bi bi-chat-dots' },
  ];

  protected escolheuGlisse(valor: string) {
    if (valor === 'home') this.clicarHome();
    else if (valor === 'meio' || valor === 'config') this.selecionarAba(valor);
  }

  constructor(private router: Router) {}

  ngOnInit() {
    this.atualizarAba(this.router.url);

    this.router.events
      .pipe(filter((e): e is NavigationEnd => e instanceof NavigationEnd))
      .subscribe(e => this.atualizarAba(e.urlAfterRedirects));
  }

  private atualizarAba(url: string) {
    const rota = url.split('?')[0];

    // rotas do responsável
    // casinha: o acompanhamento da viagem (a home do responsavel); meio: a lista dos filhos
    const homeResponsavel = ['/responsible-route'];
    const meioResponsavel = ['/responsible-home', '/home-screen'];

    // rotas do motorista
    // casinha: a lista das rotas (/driver-home) e o editor (/driver-routes/:id)
    const homeMotorista = ['/driver-home', '/driver-routes/'];
    // meio: a escolha da viagem (/driver-routes) e a viagem (/driver-route/:id)
    const meioMotorista = ['/driver-routes', '/driver-route/', '/driver-groups', '/trip-history'];

    const home = this.tipoUsuario === 'motorista' ? homeMotorista : homeResponsavel;
    const meio = this.tipoUsuario === 'motorista' ? meioMotorista : meioResponsavel;

    const bate = (lista: string[]) => lista.some(r => rota.startsWith(r));

    // telas que as abas abrem: Inicio, Rotas/Transporte (escolha ou viagem) e Conversas
    const destinos =
      this.tipoUsuario === 'motorista'
        ? rota === '/driver-home' || rota === '/driver-routes' || rota.startsWith('/driver-route/')
        : rota === '/responsible-home' || rota === '/responsible-route' || rota.startsWith('/responsible-route/');
    this.visivel.set(destinos || rota === '/chat');

    if (rota.startsWith('/chat')) {
      this.abaAtiva = 'config';
    } else if (bate(home)) {
      // casinha antes do meio: o editor (/driver-routes/:id) comeca igual a aba do meio (/driver-routes)
      this.abaAtiva = 'home';
    } else if (bate(meio)) {
      this.abaAtiva = 'meio';
    } else {
      this.abaAtiva = '';
    }
    this.moverPilula(this.abaAtiva);
  }

  // cada tela tem o proprio rodape: o novo nasce ja na aba certa e a animacao CSS (footer.css)
  // faz a pilula sair de onde estava na tela anterior
  private moverPilula(aba: Aba) {
    const alvo = aba ? POSICAO[aba] : null;
    const atual = this.posicao() ?? ultimaPosicao;
    ultimaPosicao = alvo;
    this.origem.set(alvo !== null && atual !== null && atual !== alvo ? atual : null);
    this.posicao.set(alvo);
    this.chave.update((n) => n + 1);
  }
  // o visual agora e o mesmo em todas as telas; mantido so pra nao quebrar quem ainda passa tipo="mapa"
  @Input() tipo: 'normal' | 'mapa' = 'normal';

  clicarHome() {
    if (this.tipoUsuario === 'responsavel') this.acompanharViagem();
    else this.router.navigate(['/driver-home']);
  }

  // responsavel: abre o acompanhamento da viagem que esta rodando agora com um dos filhos
  private acompanharViagem() {
    if (this.procurandoViagem) return;
    this.procurandoViagem = true;
    this.studentService.viagemEmAndamento().subscribe((viagem) => {
      this.procurandoViagem = false;
      if (viagem) {
        this.router.navigate(['/responsible-route', viagem.tripId], { queryParams: { aluno: viagem.filho } });
      } else {
        // ja procurou: a tela "Nao ha uma rota" nao procura de novo
        this.router.navigate(['/responsible-route'], { state: { semViagem: true } });
      }
    });
  }

  selecionarAba(aba: 'meio' | 'config') {
    if (aba === 'meio') {
      if (this.tipoUsuario === 'motorista') {
        // com viagem aberta hoje: direto pro mapa da viagem; sem: escolher a rota pra iniciar
        if (this.procurandoViagem) return;
        this.procurandoViagem = true;
        this.tripService.rotaComViagemAberta().subscribe((routeId) => {
          this.procurandoViagem = false;
          if (routeId) this.router.navigate(['/driver-route', routeId], { queryParams: { continuar: 1 } });
          else this.router.navigate(['/driver-routes']);
        });
      } else {
        this.router.navigate(['/responsible-home']);
      }
    }

    if (aba === 'config') {
      const from = this.tipoUsuario === 'motorista' ? 'driver-home' : 'responsible-home';
      this.router.navigate(['/chat'], { queryParams: { from } });
    }
  }
}
