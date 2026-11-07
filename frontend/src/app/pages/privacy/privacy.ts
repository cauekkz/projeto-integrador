import { Component, OnDestroy, OnInit, computed, inject, signal } from '@angular/core';
import { Location } from '@angular/common';
import { Router } from '@angular/router';
import { Header } from '../../shared/header/header';
import { PopUpService } from '../../shared/pop-up';
import { AuthService } from '../../services/auth.service';
import { BloqueioExclusao, DeleteAccount } from '../../components/delete-account/delete-account';

// o backend so deixa exportar de novo depois de 5 minutos (429): a tela guarda quando exportou pra desligar o item
const CHAVE_EXPORTADO_EM = 'vanroute.exportadoEm';
const ESPERA_EXPORTAR_MS = 5 * 60 * 1000;

// Figma "Etapa 10 · Privacidade" (1129:5566): termos, exportar meus dados e excluir minha conta (os dois perfis)
@Component({
  selector: 'app-privacy',
  standalone: true,
  imports: [Header, DeleteAccount],
  templateUrl: './privacy.html',
  styleUrls: ['../../shared/folha.css', './privacy.css'],
})
export class Privacy implements OnInit, OnDestroy {
  private authService = inject(AuthService);
  private popUpService = inject(PopUpService);
  private router = inject(Router);
  private location = inject(Location);

  protected exportando = signal(false);
  private exportadoEm = signal<number | null>(null);
  protected esperandoExportar = computed(() => this.exportadoEm() !== null);
  private liberarExportar?: ReturnType<typeof setTimeout>;

  protected excluirAberto = signal(false);
  protected bloqueio = signal<BloqueioExclusao | null>(null);

  ngOnInit() {
    const salvo = Number(this.lerStorage() ?? 0);
    if (salvo && Date.now() - salvo < ESPERA_EXPORTAR_MS) this.marcarExportado(salvo);
  }

  ngOnDestroy() {
    clearTimeout(this.liberarExportar);
  }

  abrirTermos() {
    this.router.navigate(['/terms'], { queryParams: { leitura: 1 } });
  }

  exportar() {
    if (this.exportando() || this.esperandoExportar()) return;
    this.exportando.set(true);
    this.authService.exportMyData().subscribe({
      next: (dados) => {
        this.exportando.set(false);
        this.baixar(dados);
        const agora = Date.now();
        this.gravarStorage(String(agora));
        this.marcarExportado(agora);
        this.popUpService.show('O arquivo meus-dados.json foi baixado.', 'success', 'Dados exportados');
      },
      // 429 ("Você já exportou há pouco...") vem no pop-up global
      error: () => this.exportando.set(false),
    });
  }

  abrirExcluir() {
    this.bloqueio.set(null);
    this.excluirAberto.set(true);
  }

  fecharExcluir() {
    this.excluirAberto.set(false);
  }

  bloqueado(motivo: BloqueioExclusao) {
    this.bloqueio.set(motivo);
  }

  verBloqueio() {
    if (this.bloqueio() === 'parcelas') {
      this.router.navigate(['/payments'], { queryParams: { status: 'OVERDUE' } });
    } else {
      this.router.navigate(['/contracts']);
    }
  }

  voltar() {
    this.location.back();
  }

  private marcarExportado(em: number) {
    this.exportadoEm.set(em);
    clearTimeout(this.liberarExportar);
    this.liberarExportar = setTimeout(
      () => this.exportadoEm.set(null),
      Math.max(0, ESPERA_EXPORTAR_MS - (Date.now() - em)),
    );
  }

  private baixar(dados: unknown) {
    const blob = new Blob([JSON.stringify(dados, null, 2)], { type: 'application/json' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = 'meus-dados.json';
    link.click();
    URL.revokeObjectURL(url);
  }

  private lerStorage(): string | null {
    try {
      return localStorage.getItem(CHAVE_EXPORTADO_EM);
    } catch {
      return null;
    }
  }

  private gravarStorage(valor: string) {
    try {
      localStorage.setItem(CHAVE_EXPORTADO_EM, valor);
    } catch {}
  }
}
