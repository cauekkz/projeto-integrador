import { Component, OnInit, ChangeDetectorRef } from '@angular/core';
import { Location, CommonModule } from '@angular/common';
import { ProfileSelect } from '../../components/profile-select/profile-select';
import { Footer } from '../../shared/footer/footer';
import { AuthService } from '../../services/auth.service';
import { User } from '../../models/user.model';

@Component({
  selector: 'app-driver-route',
  standalone: true,
  imports: [CommonModule, ProfileSelect, Footer],
  templateUrl: './driver-route.html',
  styleUrl: './driver-route.css',
})
export class DriverRoute implements OnInit {
  mostrarPerfil = false;
  usuarioLogado: User | null = null;
  alunosEsperandoAberto = false;
  alunosEmbarcadosAberto = false;

  alunosEsperando = [
    { nome: 'Fiaco Lopez', endereco: 'R. Libertadores 2026' },
    { nome: 'Agustin Giay', endereco: 'Av. Paulistão 2026' },
    { nome: 'Paulinho', endereco: 'Av. Raça Fia 3' },
    { nome: 'Jhon Arias', endereco: 'Av. Botafogo e bairro 2024' },
  ];

  alunosEmbarcados = [
    { nome: 'Joaquin Piquerez', endereco: 'João Freire, 59' },
    { nome: 'Marlon Freitas', endereco: 'Av. Palmeiras 1914' },
    { nome: 'Andreas Pereira', endereco: 'Av. Carrollo 1951' },
  ];

  constructor(
    private location: Location,
    private authService: AuthService,
    private cdr: ChangeDetectorRef,
  ) {}

  ngOnInit() {
    const userId = this.authService.getUserIdFromToken();
    if (userId) {
      this.authService.getUserByID(userId).subscribe({
        next: (user) => {
          this.usuarioLogado = user;
          this.cdr.detectChanges();
        },
        error: (err) => console.error('Erro ao buscar motorista:', err),
      });
    }
  }

  voltar() {
    this.location.back();
  }

  irParaPerfil() {
    this.mostrarPerfil = true;
  }

  fecharPerfil() {
    this.mostrarPerfil = false;
  }

  toggleEsperando() {
    this.alunosEsperandoAberto = !this.alunosEsperandoAberto;
  }

  toggleEmbarcados() {
    this.alunosEmbarcadosAberto = !this.alunosEmbarcadosAberto;
  }
}
