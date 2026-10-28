import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { Location } from '@angular/common';
import { ContractService } from '../../services/contract.service';
import { AuthService } from '../../services/auth.service';
import { TrailHeader } from '../../shared/trail-header/trail-header';
import { Footer } from '../../shared/footer/footer';
import {
  ContractStatus,
  contractSummaryResponse,
  periodicidadeLabel,
} from '../../services/dto/contract/contract.dto';

type Aba = 'todos' | 'pendentes' | 'aprovados' | 'bloqueados';

const ABAS: { id: Aba; label: string; status: ContractStatus[] | null }[] = [
  { id: 'todos', label: 'Todos', status: null },
  { id: 'pendentes', label: 'Pendentes', status: ['PENDING'] },
  { id: 'aprovados', label: 'Aprovados', status: ['ACTIVE'] },
  // negado, suspenso, em atraso e inativo: nenhum deles esta valendo
  { id: 'bloqueados', label: 'Bloqueados', status: ['CANCELED', 'SUSPENDED', 'OVERDUE', 'INACTIVE'] },
];

const STATUS: Record<ContractStatus, { texto: string; icone: string }> = {
  PENDING: { texto: 'Pendente', icone: 'bi-clock' },
  ACTIVE: { texto: 'Aprovado', icone: 'bi-check-circle' },
  CANCELED: { texto: 'Negado', icone: 'bi-x-circle' },
  INACTIVE: { texto: 'Inativo', icone: 'bi-slash-circle' },
  SUSPENDED: { texto: 'Suspenso', icone: 'bi-pause-circle' },
  OVERDUE: { texto: 'Em atraso', icone: 'bi-exclamation-circle' },
};

// contratos do usuario logado (aba "Contratos" do profile select)
@Component({
  selector: 'app-contracts',
  standalone: true,
  imports: [TrailHeader, Footer],
  templateUrl: './contracts.html',
  styleUrl: './contracts.css',
})
export class Contracts implements OnInit {
  private contractService = inject(ContractService);
  private authService = inject(AuthService);
  private router = inject(Router);
  private location = inject(Location);

  protected readonly abas = ABAS;
  protected readonly statusInfo = STATUS;
  protected readonly periodicidade = periodicidadeLabel;

  protected ehMotorista = false;
  protected abaAtiva = signal<Aba>('todos');
  protected contratos = signal<contractSummaryResponse[]>([]);
  protected carregando = signal(true);
  protected erroCarregar = signal(false);

  protected filtrados = computed(() => {
    const status = ABAS.find((a) => a.id === this.abaAtiva())?.status;
    const todos = this.contratos();
    return status ? todos.filter((c) => status.includes(c.status)) : todos;
  });

  ngOnInit() {
    this.ehMotorista = this.authService.getRolesFromToken().includes('ROLE_DRIVER');
    this.carregar();
  }

  carregar() {
    this.carregando.set(true);
    this.erroCarregar.set(false);
    this.contractService.getMyContracts().subscribe({
      next: (contratos) => {
        this.contratos.set(contratos ?? []);
        this.carregando.set(false);
      },
      error: (error) => {
        console.error('Erro ao carregar contratos:', error);
        this.erroCarregar.set(true);
        this.carregando.set(false);
      },
    });
  }

  visualizar(contrato: contractSummaryResponse) {
    this.router.navigate(['/contract', contrato.contractId]);
  }

  voltar() {
    this.location.back();
  }
}
