import { Component } from '@angular/core';
import { Router } from '@angular/router';
import { Footer } from '../../shared/footer/footer';
import { ProfileSelect } from '../../components/profile-select/profile-select';

@Component({
  selector: 'app-driver-home',
  standalone: true,
  imports: [Footer, ProfileSelect],
  templateUrl: './driver-home.html',
  styleUrl: './driver-home.css',
})
export class DriverHome {
  temRota = true;
  mostrarPerfil = false;
  usuarioLogado: any = null;

  constructor(private router: Router) {}

  irParaRota() {
    this.router.navigate(['/driver-route']);
  }

  irParaPerfil() {
    this.mostrarPerfil = true;
  }

  irParaChat() {
    this.router.navigate(['/chat'], { queryParams: { from: 'driver-home' } });
  }

  fecharPerfil() {
    this.mostrarPerfil = false;
  }
}
