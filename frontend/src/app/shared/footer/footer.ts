import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';

@Component({
  selector: 'app-footer',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './footer.html',
  styleUrl: './footer.css',
})
export class Footer {
  @Input() tipoUsuario: 'responsavel' | 'motorista' = 'responsavel';

  abaAtiva: 'home' | 'meio' | 'config' = 'home';

  constructor(private router: Router) {}

  clicarHome() {
    this.abaAtiva = 'home';
    if (this.tipoUsuario === 'responsavel') {
      this.router.navigate(['/home-screen']);
    } else {
      this.router.navigate(['/driver-home']);
    }
  }

  selecionarAba(aba: 'meio' | 'config') {
    this.abaAtiva = aba;

    if (aba === 'meio' && this.tipoUsuario === 'motorista') {
      this.router.navigate(['/driver-route']);
    }

    if (aba === 'config') {
      const from = this.tipoUsuario === 'motorista' ? 'driver-home' : 'home-screen';
      this.router.navigate(['/chat'], { queryParams: { from } });
    }
  }
}
