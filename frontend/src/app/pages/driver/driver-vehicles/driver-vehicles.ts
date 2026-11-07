import { Component, OnInit, inject, signal } from '@angular/core';
import { Location } from '@angular/common';
import { TrailHeader } from '../../../shared/trail-header/trail-header';
import { Footer } from '../../../shared/footer/footer';
import { PopUpService } from '../../../shared/pop-up';
import { VehicleService } from '../../../services/vehicle.service';
import { vehicleResponse, VehicleStatus } from '../../../services/dto/vehicle/vehicle.dto';
import { isoParaData } from '../../../services/dto/contract/contract.dto';

// mesmo limite dos outros arquivos (anexo e PDF do contrato)
export const CRLV_TAMANHO_MAXIMO = 10 * 1024 * 1024;

const STATUS: Record<VehicleStatus, { texto: string; classe: string }> = {
  ACTIVE: { texto: 'Ativa', classe: 'ativa' },
  INACTIVE: { texto: 'Inativa', classe: 'inativa' },
  UNDER_MAINTENANCE: { texto: 'Em manutenção', classe: 'manutencao' },
};

// CRLV vem em maiusculas: "SPRINTER 415" -> "Sprinter 415"
export function formatarCrlv(texto: string | null | undefined): string {
  return (texto ?? '')
    .toLowerCase()
    .split(' ')
    .filter(Boolean)
    .map((parte) => parte[0].toUpperCase() + parte.slice(1))
    .join(' ');
}

// Figma "Etapa 3 · Motorista preparado": Minhas vans (lista, sem vans, adicionar pelo CRLV, parar de usar)
@Component({
  selector: 'app-driver-vehicles',
  standalone: true,
  imports: [TrailHeader, Footer],
  templateUrl: './driver-vehicles.html',
  styleUrls: ['../../../shared/folha.css', './driver-vehicles.css'],
})
export class DriverVehicles implements OnInit {
  private vehicleService = inject(VehicleService);
  private popUpService = inject(PopUpService);
  private location = inject(Location);

  protected vans = signal<vehicleResponse[]>([]);
  protected carregando = signal(true);
  protected erroCarregar = signal(false);

  // "Adicionar van": tela do CRLV por cima da lista
  protected adicionando = signal(false);
  protected crlv = signal<File | null>(null);
  protected enviando = signal(false);

  // van esperando a confirmacao de "Parar de usar"
  protected parar = signal<vehicleResponse | null>(null);

  ngOnInit() {
    this.carregar();
  }

  carregar() {
    this.carregando.set(true);
    this.erroCarregar.set(false);
    this.vehicleService.getMyVehicles().subscribe({
      next: (vans) => {
        this.vans.set(vans ?? []);
        this.carregando.set(false);
      },
      error: (error) => {
        console.error('Erro ao carregar as vans:', error);
        this.erroCarregar.set(true);
        this.carregando.set(false);
      },
    });
  }

  status(van: vehicleResponse) {
    return STATUS[van.status] ?? STATUS.INACTIVE;
  }

  // "Sprinter · 2022 · Prata"
  resumo(van: vehicleResponse): string {
    return [formatarCrlv(van.model), van.year ?? '', formatarCrlv(van.color)].filter((p) => p !== '').join(' · ');
  }

  // "15 lugares · Licenciamento 2026 · Em uso desde 10/01/2026"
  detalhes(van: vehicleResponse): string {
    const partes: string[] = [];
    if (van.capacity) partes.push(`${van.capacity} lugares`);
    if (van.licensingYear) partes.push(`Licenciamento ${van.licensingYear}`);
    if (van.linkedSince) partes.push(`Em uso desde ${isoParaData(van.linkedSince)}`);
    return partes.join(' · ');
  }

  // ---------- adicionar pelo CRLV ----------

  abrirAdicionar() {
    this.crlv.set(null);
    this.adicionando.set(true);
  }

  fecharAdicionar() {
    if (!this.enviando()) this.adicionando.set(false);
  }

  selecionarCrlv(event: Event) {
    const input = event.target as HTMLInputElement;
    const arquivo = input.files?.[0];
    input.value = '';
    if (!arquivo) return;

    if (arquivo.type !== 'application/pdf') {
      this.popUpService.show('Envie o CRLV digital em PDF', 'error', 'Dados inválidos');
      return;
    }
    if (arquivo.size > CRLV_TAMANHO_MAXIMO) {
      this.popUpService.show('O arquivo pode ter no máximo 10 MB', 'error', 'Dados inválidos');
      return;
    }
    this.crlv.set(arquivo);
  }

  enviar() {
    if (this.enviando()) return;
    const arquivo = this.crlv();
    if (!arquivo) {
      this.popUpService.show('Envie o CRLV digital em PDF', 'warning', 'Campos Incompletos');
      return;
    }

    this.enviando.set(true);
    this.vehicleService.addVehicle(arquivo).subscribe({
      next: (van) => {
        this.enviando.set(false);
        this.adicionando.set(false);
        this.vans.update((lista) => [van, ...lista.filter((v) => v.id !== van.id)]);
        this.popUpService.show(`A van ${van.plate} foi adicionada.`, 'success', 'Van adicionada');
      },
      // 422 ("O licenciamento da van está vencido...") vem no popup global com o texto do backend
      error: () => this.enviando.set(false),
    });
  }

  // ---------- parar de usar ----------

  confirmarParar(van: vehicleResponse) {
    this.parar.set(van);
  }

  fecharParar() {
    if (!this.enviando()) this.parar.set(null);
  }

  pararDeUsar() {
    const van = this.parar();
    if (!van || this.enviando()) return;
    this.enviando.set(true);
    this.vehicleService.unlinkVehicle(van.id).subscribe({
      next: () => {
        this.enviando.set(false);
        this.parar.set(null);
        this.vans.update((lista) => lista.filter((v) => v.id !== van.id));
        this.popUpService.show(`A van ${van.plate} saiu da sua lista.`, 'success', 'Van removida');
      },
      error: () => this.enviando.set(false),
    });
  }

  voltar() {
    this.location.back();
  }
}
