import { Component, OnInit, inject, signal } from '@angular/core';
import { Location } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { ActivatedRoute } from '@angular/router';
import { catchError, of } from 'rxjs';
import { Header } from '../../../shared/header/header';
import { PopUpService } from '../../../shared/pop-up';
import { formatarTelefone } from '../../../shared/utils';
import { ProfileService } from '../../../services/profile.service';
import { StorageService } from '../../../services/storage.service';
import { responsibleProfile } from '../../../services/dto/profile/profile.dto';
import { ResponsibleSituation } from '../../../components/common/responsible-situation/responsible-situation';
import { iniciaisDe } from '../../../shared/iniciais/iniciais';

// Figma "Motorista · /responsible-profile/:id" (1057:1059 em dia, 1057:1100 com atraso, 1057:1136 erro 403)
@Component({
  selector: 'app-responsible-profile',
  standalone: true,
  imports: [Header, ResponsibleSituation],
  templateUrl: './responsible-profile.html',
  styleUrls: ['../../../shared/folha.css', '../../../shared/perfil.css'],
})
export class ResponsibleProfile implements OnInit {
  private route = inject(ActivatedRoute);
  private location = inject(Location);
  private profileService = inject(ProfileService);
  private storageService = inject(StorageService);
  private popUpService = inject(PopUpService);

  protected readonly formatarTelefone = formatarTelefone;
  protected carregando = signal(true);
  protected indisponivel = signal(false);
  protected perfil = signal<responsibleProfile | null>(null);
  protected foto = signal<string | null>(null);

  ngOnInit() {
    const id = this.route.snapshot.paramMap.get('responsibleId') ?? '';
    this.profileService.getResponsibleProfile(id).subscribe({
      next: (perfil) => {
        this.perfil.set(perfil);
        this.carregando.set(false);
        if (perfil.photoKey) {
          this.storageService.getDownloadUrl(perfil.photoKey).pipe(catchError(() => of(null))).subscribe((url) => this.foto.set(url));
        }
        // Figma 1057:1100: o aviso de atraso aparece ao abrir
        if (perfil.financialStatus === 'OVERDUE') {
          const n = perfil.overduePayments;
          this.popUpService.show('Conta só as parcelas dos contratos com você.', 'error', n === 1 ? '1 parcela atrasada' : `${n} parcelas atrasadas`);
        }
      },
      error: (erro: HttpErrorResponse) => {
        this.carregando.set(false);
        this.indisponivel.set(true);
        this.popUpService.show(
          erro?.status === 403
            ? 'Você só vê o perfil de um responsável com quem conversa ou tem contrato'
            : 'Não foi possível carregar o perfil. Tente de novo em instantes.',
          'error',
          'Perfil indisponível',
        );
      },
    });
  }

  voltar() {
    this.location.back();
  }

  iniciais(nome: string | null | undefined): string {
    return iniciaisDe(nome);
  }
}
