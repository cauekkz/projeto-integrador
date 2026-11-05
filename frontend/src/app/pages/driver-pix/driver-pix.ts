import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { Location } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { TrailHeader } from '../../shared/trail-header/trail-header';
import { Footer } from '../../shared/footer/footer';
import { PopUpService } from '../../shared/pop-up';
import { DriverService } from '../../services/driver.service';
import { PIX_TIPOS, pixKey, PixKeyType, pixTipoLabel } from '../../services/dto/driver/pix.dto';

// Figma "Etapa 3 · Motorista preparado": Chave Pix (sem chave, com chave, trocar, remover)
// a chave e conferida e normalizada no backend: a tela mostra o que voltar
@Component({
  selector: 'app-driver-pix',
  standalone: true,
  imports: [TrailHeader, Footer, FormsModule],
  templateUrl: './driver-pix.html',
  styleUrls: ['../../shared/folha.css', './driver-pix.css'],
})
export class DriverPix implements OnInit {
  private driverService = inject(DriverService);
  private popUpService = inject(PopUpService);
  private location = inject(Location);

  protected chave = signal<pixKey | null>(null);
  protected carregando = signal(true);
  protected erroCarregar = signal(false);
  protected enviando = signal(false);

  // formulario: aberto sem chave ou ao tocar "Trocar chave"
  protected editando = signal(false);
  protected tipo = signal<PixKeyType>('CPF');
  protected valor = '';
  protected removerAberto = signal(false);

  protected readonly tipos = PIX_TIPOS;
  protected temChave = computed(() => !!this.chave()?.key);
  protected tipoAtual = computed(() => PIX_TIPOS.find((t) => t.value === this.tipo()) ?? PIX_TIPOS[0]);
  protected readonly tipoLabel = pixTipoLabel;

  ngOnInit() {
    this.carregar();
  }

  carregar() {
    this.carregando.set(true);
    this.erroCarregar.set(false);
    this.driverService.getPixKey().subscribe({
      next: (chave) => {
        this.chave.set(chave);
        this.carregando.set(false);
        if (!chave?.key) this.abrirFormulario();
      },
      error: (error) => {
        console.error('Erro ao carregar a chave Pix:', error);
        this.erroCarregar.set(true);
        this.carregando.set(false);
      },
    });
  }

  private abrirFormulario() {
    const atual = this.chave();
    this.tipo.set(atual?.type ?? 'CPF');
    this.valor = atual?.key ?? '';
    this.editando.set(true);
    if (!atual?.key) {
      this.popUpService.show(
        'Os responsáveis veem a sua chave nas parcelas para copiar e pagar. O app não recebe dinheiro.',
        'info',
        'Sem chave Pix cadastrada',
      );
    }
  }

  trocar() {
    this.abrirFormulario();
  }

  cancelarTroca() {
    if (!this.enviando()) this.editando.set(false);
  }

  escolherTipo(tipo: PixKeyType) {
    if (this.tipo() === tipo) return;
    this.tipo.set(tipo);
    this.valor = '';
  }

  salvar() {
    if (this.enviando()) return;
    const chave = this.valor.trim();
    if (!chave) {
      this.popUpService.show('Informe a chave Pix', 'warning', 'Campos Incompletos');
      return;
    }

    this.enviando.set(true);
    this.driverService.setPixKey(this.tipo(), chave).subscribe({
      next: (salva) => {
        this.enviando.set(false);
        this.chave.set(salva);
        this.editando.set(false);
        this.popUpService.show('Os responsáveis já veem a chave nas parcelas.', 'success', 'Chave Pix salva');
      },
      // 400 ("Chave Pix inválida para o tipo e-mail") vem no popup global com o texto do backend
      error: () => this.enviando.set(false),
    });
  }

  abrirRemover() {
    this.removerAberto.set(true);
  }

  fecharRemover() {
    if (!this.enviando()) this.removerAberto.set(false);
  }

  remover() {
    if (this.enviando()) return;
    this.enviando.set(true);
    this.driverService.removePixKey().subscribe({
      next: () => {
        this.enviando.set(false);
        this.removerAberto.set(false);
        this.chave.set({ type: null, key: null });
        this.abrirFormulario();
      },
      error: () => this.enviando.set(false),
    });
  }

  voltar() {
    this.location.back();
  }
}
