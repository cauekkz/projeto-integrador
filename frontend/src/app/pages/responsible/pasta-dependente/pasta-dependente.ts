import { Component, OnInit, inject, signal } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { Location } from '@angular/common';
import { TrailHeader } from '../../../shared/trail-header/trail-header';
import { Contracts } from '../../common/contracts/contracts';
import { Payments } from '../../common/payments/payments';
import { GlisseSegmentado, OpcaoSegmentada } from '../../../shared/glisse-segmentado/glisse-segmentado';

type Secao = 'contratos' | 'parcelas';

// pasta de um filho (aberta na tela de Contratos do responsavel): todos os contratos dele, de qualquer status,
// e todas as parcelas dele. Reaproveita as listas de /contracts e /payments filtradas pelo filho
@Component({
  selector: 'app-pasta-dependente',
  standalone: true,
  imports: [TrailHeader, Contracts, Payments, GlisseSegmentado],
  templateUrl: './pasta-dependente.html',
  styleUrls: ['../../../shared/folha.css', './pasta-dependente.css'],
})
export class PastaDependente implements OnInit {
  private route = inject(ActivatedRoute);
  private location = inject(Location);

  protected alunoId = '';
  protected nome = '';
  protected secao = signal<Secao>('contratos');
  protected readonly secoes: OpcaoSegmentada[] = [
    { value: 'contratos', label: 'Contratos' },
    { value: 'parcelas', label: 'Parcelas' },
  ];
  // se o React da Glisse nao carregar, ficam os botoes de reserva
  protected glisseFalhou = signal(false);

  ngOnInit() {
    this.alunoId = this.route.snapshot.paramMap.get('studentId') ?? '';
    this.nome = this.route.snapshot.queryParamMap.get('nome') ?? 'Dependente';
    if (this.route.snapshot.queryParamMap.get('secao') === 'parcelas') this.secao.set('parcelas');
  }

  trocar(secao: string) {
    if (secao === 'contratos' || secao === 'parcelas') this.secao.set(secao);
  }

  voltar() {
    this.location.back();
  }
}
