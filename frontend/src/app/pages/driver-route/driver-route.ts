import { Component } from '@angular/core';
import { Location, CommonModule } from '@angular/common';
import { Header } from '../../shared/header/header';
import { ProfileSelect } from '../../components/profile-select/profile-select'; // <--- Importe o ProfileSelect

@Component({
  selector: 'app-driver-route',
  standalone: true,
  imports: [CommonModule, Header, ProfileSelect], // <--- Adicione aqui no imports
  templateUrl: './driver-route.html',
  styleUrl: './driver-route.css',
})
export class DriverRoute {
  mostrarPerfil = false; // <--- Controle de exibição do modal
  alunosEsperandoAberto = false;
  alunosEmbarcadosAberto = false;

  alunosEsperando = [
    { nome: 'Fiaco Lopez', endereco: 'R. Libertadores 2026' },
    { nome: 'Agustin Giay', endereco: 'Av. Paulistao 2026' },
    { nome: 'Paulinho', endereco: 'Av. Raca Fia 3' },
    { nome: 'Jhon Arias', endereco: 'Av. Botafogo e bairro 2024' },
  ];

  alunosEmbarcados = [
    { nome: 'Joaquin Piquerez', endereco: 'Joao freire, 59' },
    { nome: 'Marlon Freitas', endereco: 'Av. Palmeiras 1914' },
    { nome: 'Andreas Pereira', endereco: 'Av. Carrollo 1951' },
  ];

  constructor(private location: Location) {}

  voltar() {
    this.location.back();
  }

  // Abre o perfil no modal/bottom-sheet por cima
  irParaPerfil() {
    this.mostrarPerfil = true;
  }

  // Fecha o perfil
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
