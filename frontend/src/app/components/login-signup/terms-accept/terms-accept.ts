import { Component, EventEmitter, OnInit, Output } from '@angular/core';
import { TermsService, Terms } from '../../../services/terms.service';

// checkbox "Li e aceito" do cadastro: busca a versao atual e devolve ela quando a pessoa marca (null quando desmarca)
@Component({
  selector: 'app-terms-accept',
  standalone: true,
  templateUrl: './terms-accept.html',
  styleUrl: './terms-accept.css',
})
export class TermsAccept implements OnInit {
  @Output() versaoAceitaChange = new EventEmitter<string | null>();

  termos: Terms | null = null;
  aceito = false;
  mostrarTexto = false;

  constructor(private termsService: TermsService) {}

  ngOnInit() {
    this.termsService.getCurrent().subscribe({
      next: (termos) => (this.termos = termos),
      error: () => {},
    });
  }

  alternar(marcado: boolean) {
    this.aceito = marcado;
    this.versaoAceitaChange.emit(marcado && this.termos ? this.termos.version : null);
  }
}
