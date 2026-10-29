import { Directive, ElementRef, EventEmitter, HostListener, Output, inject } from '@angular/core';
import { PopUpService } from '../pop-up';
import { limparMascara } from '../utils';

export interface EnderecoCep {
  rua: string;
  bairro: string;
  cidade: string;
  uf: string;
}

/**
 * Com o CEP completo (8 digitos) busca o endereco no ViaCEP e emite (enderecoCep) pra tela preencher
 * rua, bairro, cidade e UF; a pessoa so completa o numero. Usa fetch e nao o HttpClient pra nao passar
 * pelo authInterceptor (o token do app nao pode ir pra uma API de fora).
 */
@Directive({
  selector: '[appCepBusca]',
  standalone: true,
})
export class CepBuscaDirective {
  @Output() enderecoCep = new EventEmitter<EnderecoCep>();

  private el = inject<ElementRef<HTMLInputElement>>(ElementRef);
  private popUpService = inject(PopUpService);
  // evita buscar de novo o mesmo CEP a cada tecla/blur
  private ultimoCep = '';

  @HostListener('input')
  @HostListener('blur')
  onAlterar(): void {
    const cep = limparMascara(this.el.nativeElement.value);
    if (cep.length !== 8 || cep === this.ultimoCep) return;
    this.ultimoCep = cep;
    this.buscar(cep);
  }

  private async buscar(cep: string): Promise<void> {
    try {
      const resposta = await fetch(`https://viacep.com.br/ws/${cep}/json/`);
      if (!resposta.ok) throw new Error(`ViaCEP ${resposta.status}`);
      const dados = await resposta.json();

      // a pessoa pode ter trocado o CEP enquanto a busca rodava
      if (cep !== limparMascara(this.el.nativeElement.value)) return;

      if (dados.erro) {
        this.ultimoCep = '';
        this.popUpService.show('CEP não encontrado. Confira o número ou preencha o endereço.', 'warning', 'CEP');
        return;
      }

      this.enderecoCep.emit({
        rua: dados.logradouro ?? '',
        bairro: dados.bairro ?? '',
        cidade: dados.localidade ?? '',
        uf: dados.uf ?? '',
      });
    } catch (error) {
      console.error('Erro ao buscar CEP:', error);
      this.ultimoCep = '';
      this.popUpService.show('Não foi possível buscar o CEP agora. Preencha o endereço.', 'warning', 'CEP');
    }
  }
}
