import { Component } from '@angular/core';
import { Location, CommonModule } from '@angular/common';
import { Header } from '../../shared/header/header';
import { ProfileSelect } from '../../components/profile-select/profile-select';

@Component({
  selector: 'app-driver-route',
  standalone: true,
  imports: [CommonModule, Header, ProfileSelect],
  templateUrl: './driver-route.html',
  styleUrl: './driver-route.css',
})
export class DriverRoute {
  mostrarPerfil = false;
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

  constructor(private location: Location) {}

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
