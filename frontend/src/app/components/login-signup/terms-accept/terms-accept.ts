import { Component, EventEmitter, OnDestroy, OnInit, Output, signal } from '@angular/core';
import { TermsService, Terms } from '../../../services/terms.service';
import { Header } from '../../../shared/header/header';
import { MarkdownText } from '../../../shared/markdown-text/markdown-text';

// checkbox "Li e aceito" do cadastro: busca a versao atual e devolve ela quando a pessoa marca (null quando desmarca)
// o link abre os termos numa pagina em tela cheia por cima do cadastro (o formulario nao se perde),
// no estilo de "Privacidade e dados"; "Li e aceito" de la ja marca o checkbox
@Component({
  selector: 'app-terms-accept',
  standalone: true,
  imports: [Header, MarkdownText],
  templateUrl: './terms-accept.html',
  styleUrls: ['../../../shared/folha.css', './terms-accept.css'],
})
export class TermsAccept implements OnInit, OnDestroy {
  @Output() versaoAceitaChange = new EventEmitter<string | null>();

  protected termos = signal<Terms | null>(null);
  protected aceito = signal(false);
  protected paginaAberta = signal(false);

  constructor(private termsService: TermsService) {}

  ngOnInit() {
    this.termsService.getCurrent().subscribe({
      next: (termos) => this.termos.set(termos),
      error: () => {},
    });
  }

  ngOnDestroy() {
    this.travarRolagem(false);
  }

  alternar(marcado: boolean) {
    this.aceito.set(marcado);
    const termos = this.termos();
    this.versaoAceitaChange.emit(marcado && termos ? termos.version : null);
  }

  abrirPagina() {
    if (!this.termos()) return;
    this.paginaAberta.set(true);
    this.travarRolagem(true);
  }

  fecharPagina() {
    this.paginaAberta.set(false);
    this.travarRolagem(false);
  }

  aceitarPelaPagina() {
    this.alternar(true);
    this.fecharPagina();
  }

  // o cadastro por baixo nao rola enquanto a pagina dos termos esta aberta
  private travarRolagem(travar: boolean) {
    document.body.style.overflow = travar ? 'hidden' : '';
  }
}
