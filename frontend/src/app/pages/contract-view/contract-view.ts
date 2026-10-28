import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { Location } from '@angular/common';
import { ContractService } from '../../services/contract.service';
import { StorageService } from '../../services/storage.service';
import { AuthService } from '../../services/auth.service';
import { TrailHeader } from '../../shared/trail-header/trail-header';
import {
  ContractStatus,
  contractDetailsResponse,
  isoParaData,
  periodicidadeLabel,
  valorEmReais,
} from '../../services/dto/contract/contract.dto';

const STATUS: Record<ContractStatus, { texto: string; classe: string }> = {
  PENDING: { texto: 'Recebido', classe: 'pendente' },
  ACTIVE: { texto: 'Aceito', classe: 'aceito' },
  CANCELED: { texto: 'Negado', classe: 'negado' },
  INACTIVE: { texto: 'Inativo', classe: 'neutro' },
  SUSPENDED: { texto: 'Suspenso', classe: 'negado' },
  OVERDUE: { texto: 'Em atraso', classe: 'negado' },
};

// analise do contrato (aberta pelo card do chat ou pela lista de contratos); ?chatId= volta pra conversa
@Component({
  selector: 'app-contract-view',
  standalone: true,
  imports: [TrailHeader],
  templateUrl: './contract-view.html',
  styleUrl: './contract-view.css',
})
export class ContractView implements OnInit {
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private location = inject(Location);
  private contractService = inject(ContractService);
  private storageService = inject(StorageService);
  private authService = inject(AuthService);

  protected contractId = '';
  protected chatId: string | null = null;
  // o motorista enviou o contrato; pra ele o pendente aparece como "Enviado"
  protected ehMotorista = false;

  protected contrato = signal<contractDetailsResponse | null>(null);
  protected carregando = signal(true);
  protected erroCarregar = signal(false);
  protected linkPdf = signal<string | null>(null);
  protected carregandoPdf = signal(false);

  protected statusInfo = computed(() => {
    const status = this.contrato()?.status;
    if (!status) return null;
    if (status === 'PENDING' && this.ehMotorista) return { texto: 'Enviado', classe: 'pendente' };
    return STATUS[status];
  });
  protected periodicidade = computed(() => periodicidadeLabel(this.contrato()?.periodicity));

  protected readonly valor = valorEmReais;
  protected readonly data = isoParaData;

  ngOnInit() {
    this.contractId = this.route.snapshot.paramMap.get('contractId') || '';
    this.chatId = this.route.snapshot.queryParamMap.get('chatId');
    this.ehMotorista = this.authService.getRolesFromToken().includes('ROLE_DRIVER');
    this.carregar();
  }

  carregar() {
    this.carregando.set(true);
    this.erroCarregar.set(false);
    this.contractService.getContract(this.contractId).subscribe({
      next: (contrato) => {
        this.contrato.set(contrato);
        this.carregando.set(false);
        if (contrato.documentFileKey) this.carregarPdf(contrato.documentFileKey);
      },
      error: (error) => {
        console.error('Erro ao carregar o contrato:', error);
        this.erroCarregar.set(true);
        this.carregando.set(false);
      },
    });
  }

  // o link do PDF vale poucos minutos: gera ao abrir a tela
  private carregarPdf(fileKey: string) {
    this.carregandoPdf.set(true);
    this.storageService.getDownloadUrl(fileKey).subscribe({
      next: (url) => {
        this.linkPdf.set(url);
        this.carregandoPdf.set(false);
      },
      error: () => this.carregandoPdf.set(false),
    });
  }

  voltar() {
    if (this.chatId) {
      this.router.navigate(['/chat-details', this.chatId]);
    } else {
      this.location.back();
    }
  }
}
