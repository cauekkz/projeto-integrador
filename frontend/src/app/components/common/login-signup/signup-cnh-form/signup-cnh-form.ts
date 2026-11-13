import { Component, EventEmitter, Output, signal } from '@angular/core';
import { AuthService } from '../../../../services/auth.service';
import { RegisterService } from '../../../../services/register.service';
import { Header } from '../../../../shared/header/header';

import { BotaoCarregandoDirective } from '../../../../shared/botao-carregando/botao-carregando.directive';
@Component({
  selector: 'app-signup-cnh-form',
  standalone: true,
  imports: [BotaoCarregandoDirective, Header],
  templateUrl: './signup-cnh-form.html',
  styleUrls: ['../../../../shared/cadastro.css', './signup-cnh-form.css'],
})
export class SignupCnhForm {
  @Output() prontoEvent = new EventEmitter<void>();
  @Output() voltarEvent = new EventEmitter<void>();

  protected validando = signal(false);

  constructor(
    protected registerService: RegisterService,
    private authService: AuthService,
  ) {}

  // confere a assinatura da CNH antes do formulario: quem manda um PDF invalido nao preenche tudo a toa.
  // recusada (422): o popup global mostra a mensagem do backend e a tela continua aqui
  pronto() {
    const arquivo = this.registerService.selectedFile;
    if (arquivo == null || this.validando()) return;
    this.validando.set(true);
    this.authService.verifyCNH({ documentPDF: arquivo }).subscribe({
      next: () => {
        this.validando.set(false);
        this.prontoEvent.emit();
      },
      error: () => this.validando.set(false),
    });
  }

  voltar() {
    this.voltarEvent.emit();
  }

  onFileSelected(event: Event) {
    const input = event.target as HTMLInputElement;
    if (input.files?.length) {
      this.registerService.selectedFile = input.files[0];
    }
  }

  delete(input: HTMLInputElement) {
    this.registerService.selectedFile = null;
    // limpa o input pra conseguir escolher o mesmo PDF de novo
    input.value = '';
  }
}
