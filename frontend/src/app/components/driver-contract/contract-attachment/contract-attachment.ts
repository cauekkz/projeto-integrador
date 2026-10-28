import { Component, inject, input, signal } from '@angular/core';
import { ContractDraftService, PDF_TAMANHO_MAXIMO } from '../../../pages/driver-contract/contract-draft.service';

// area do PDF opcional que vai junto com a proposta
@Component({
  selector: 'app-contract-attachment',
  standalone: true,
  templateUrl: './contract-attachment.html',
  styleUrl: './contract-attachment.css',
})
export class ContractAttachment {
  // durante o envio nao deixa trocar o arquivo
  readonly bloqueado = input(false);

  protected draft = inject(ContractDraftService);
  protected erro = signal('');
  protected tamanhoMaximoMb = PDF_TAMANHO_MAXIMO / (1024 * 1024);

  selecionar(event: Event) {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    // limpa pra conseguir escolher o mesmo arquivo de novo depois de remover
    input.value = '';

    if (!file) return;

    const ehPdf = file.type === 'application/pdf' || file.name.toLowerCase().endsWith('.pdf');
    if (!ehPdf) {
      this.erro.set('Selecione um arquivo PDF.');
      return;
    }
    if (file.size > PDF_TAMANHO_MAXIMO) {
      this.erro.set(`O PDF deve ter no máximo ${this.tamanhoMaximoMb} MB.`);
      return;
    }
    if (file.size === 0) {
      this.erro.set('O arquivo está vazio.');
      return;
    }

    this.erro.set('');
    this.draft.pdf.set(file);
  }

  remover() {
    this.erro.set('');
    this.draft.pdf.set(null);
  }

  tamanho(bytes: number): string {
    if (bytes < 1024 * 1024) return `${Math.max(1, Math.round(bytes / 1024))} KB`;
    return `${(bytes / (1024 * 1024)).toFixed(1).replace('.', ',')} MB`;
  }
}
