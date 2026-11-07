import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Location } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { ActivatedRoute } from '@angular/router';
import { catchError, of } from 'rxjs';
import { Header } from '../../../shared/header/header';
import { PopUpService } from '../../../shared/pop-up';
import { ProfileService } from '../../../services/profile.service';
import { StorageService } from '../../../services/storage.service';
import { dataBr, driverProfile, notaBr, ratingResponse } from '../../../services/dto/profile/profile.dto';

const ESTRELAS = [1, 2, 3, 4, 5];

// Figma "Responsável · /driver-profile/:id" (1057:635, 1057:709, 1057:784, 1057:994) e "Avaliar" (1057:823, 1057:904, 1057:945)
@Component({
  selector: 'app-driver-profile',
  standalone: true,
  imports: [FormsModule, Header],
  templateUrl: './driver-profile.html',
  styleUrls: ['../../../shared/folha.css', '../../../shared/rota.css', '../../../shared/perfil.css', './driver-profile.css'],
})
export class DriverProfile implements OnInit {
  private route = inject(ActivatedRoute);
  private location = inject(Location);
  private profileService = inject(ProfileService);
  private storageService = inject(StorageService);
  private popUpService = inject(PopUpService);

  protected readonly estrelas = ESTRELAS;
  protected readonly dataBr = dataBr;
  protected readonly notaBr = notaBr;

  private driverId = '';
  protected carregando = signal(true);
  protected indisponivel = signal(false);
  protected perfil = signal<driverProfile | null>(null);
  protected foto = signal<string | null>(null);

  // tela cheia "Avaliar motorista"
  protected avaliando = signal(false);
  protected nota = signal(0);
  protected comentario = '';
  protected enviando = signal(false);
  protected confirmandoApagar = signal(false);

  ngOnInit() {
    this.driverId = this.route.snapshot.paramMap.get('driverId') ?? '';
    this.carregar();
  }

  private carregar() {
    this.carregando.set(true);
    this.profileService.getDriverProfile(this.driverId).subscribe({
      next: (perfil) => {
        this.perfil.set(perfil);
        this.carregando.set(false);
        if (perfil.photoKey) {
          this.storageService
            .getDownloadUrl(perfil.photoKey)
            .pipe(catchError(() => of(null)))
            .subscribe((url) => this.foto.set(url));
        }
      },
      // Figma 1057:994: tela vazia com o aviso e "Voltar"
      error: (erro: HttpErrorResponse) => {
        this.carregando.set(false);
        this.indisponivel.set(true);
        const mensagem =
          erro?.status === 403
            ? 'Você só vê o perfil de um motorista com quem conversa ou tem contrato'
            : 'Não foi possível carregar o perfil. Tente de novo em instantes.';
        this.popUpService.show(mensagem, 'error', 'Perfil indisponível');
      },
    });
  }

  voltar() {
    this.location.back();
  }

  iniciais(nome: string | undefined): string {
    return (nome ?? '')
      .split(' ')
      .filter(Boolean)
      .slice(0, 2)
      .map((p) => p[0].toUpperCase())
      .join('');
  }

  // estrela cheia ate a nota (media arredondada)
  cheia(i: number, nota: number | null | undefined) {
    return nota != null && i <= Math.round(nota);
  }

  // ---------- avaliar ----------

  abrirAvaliar() {
    const minha = this.perfil()?.myRating;
    this.nota.set(minha?.score ?? 0);
    this.comentario = minha?.comment ?? '';
    this.avaliando.set(true);
  }

  fecharAvaliar() {
    if (!this.enviando()) this.avaliando.set(false);
  }

  enviar() {
    if (this.enviando()) return;
    if (!this.nota()) {
      this.popUpService.show('Escolha de 1 a 5 estrelas.', 'warning', 'Campos Incompletos');
      return;
    }
    const comment = this.comentario.trim();
    const editando = !!this.perfil()?.myRating;
    this.enviando.set(true);
    this.profileService.rateDriver(this.driverId, { score: this.nota(), ...(comment ? { comment } : {}) }).subscribe({
      next: () => {
        this.enviando.set(false);
        this.avaliando.set(false);
        this.popUpService.show(editando ? 'Sua avaliação foi atualizada.' : 'Obrigado por avaliar o motorista.', 'success', 'Avaliação enviada');
        this.carregar();
      },
      // Figma 1057:865: o aviso aparece por cima da tela de avaliar
      error: (erro: HttpErrorResponse) => {
        this.enviando.set(false);
        const mensagem = erro?.error?.message || 'Tente de novo em instantes.';
        this.popUpService.show(mensagem, 'error', erro?.status === 403 ? 'Sem permissão' : 'Não foi possível concluir');
      },
    });
  }

  apagar() {
    if (this.enviando()) return;
    this.enviando.set(true);
    this.profileService.deleteRating(this.driverId).subscribe({
      next: () => {
        this.enviando.set(false);
        this.confirmandoApagar.set(false);
        this.avaliando.set(false);
        this.popUpService.show('Sua nota e seu comentário saíram do perfil do motorista.', 'success', 'Avaliação apagada');
        this.carregar();
      },
      error: (erro: HttpErrorResponse) => {
        this.enviando.set(false);
        this.popUpService.show(erro?.error?.message || 'Tente de novo em instantes.', 'error', 'Não foi possível concluir');
      },
    });
  }

  fecharApagar() {
    if (!this.enviando()) this.confirmandoApagar.set(false);
  }

  // avaliacoes recentes sem repetir a minha (ela aparece em "Sua avaliação")
  recentes(p: driverProfile): ratingResponse[] {
    const minha = p.myRating;
    if (!minha) return p.recentRatings;
    return p.recentRatings.filter((r) => !(r.date === minha.date && r.score === minha.score && r.comment === minha.comment));
  }
}
