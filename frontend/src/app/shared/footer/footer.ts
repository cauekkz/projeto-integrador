import { Component, Input, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router, NavigationEnd } from '@angular/router';
import { filter } from 'rxjs/operators';

@Component({
  selector: 'app-footer',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './footer.html',
  styleUrl: './footer.css',
})
export class Footer implements OnInit {
  @Input() tipoUsuario: 'responsavel' | 'motorista' = 'responsavel';

  abaAtiva: 'home' | 'meio' | 'config' | '' = 'home';

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
    const homeResponsavel = ['/home-screen'];
    const meioResponsavel: string[] = []; // coloque aqui a rota do mapa do responsável

    // rotas do motorista
    const homeMotorista = ['/driver-home'];
    const meioMotorista = ['/driver-route'];

    const home = this.tipoUsuario === 'motorista' ? homeMotorista : homeResponsavel;
    const meio = this.tipoUsuario === 'motorista' ? meioMotorista : meioResponsavel;

    const bate = (lista: string[]) => lista.some(r => rota.startsWith(r));

    if (rota.startsWith('/chat')) {
      this.abaAtiva = 'config';
    } else if (bate(meio)) {
      this.abaAtiva = 'meio';
    } else if (bate(home)) {
      this.abaAtiva = 'home';
    } else {
      this.abaAtiva = '';
    }
  }

  clicarHome() {
    this.router.navigate([
      this.tipoUsuario === 'responsavel' ? '/home-screen' : '/driver-home',
    ]);
  }

  selecionarAba(aba: 'meio' | 'config') {
    if (aba === 'meio' && this.tipoUsuario === 'motorista') {
      this.router.navigate(['/driver-route']);
    }

    if (aba === 'config') {
      const from = this.tipoUsuario === 'motorista' ? 'driver-home' : 'home-screen';
      this.router.navigate(['/chat'], { queryParams: { from } });
    }
  }
}
