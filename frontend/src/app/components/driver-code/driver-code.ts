import { Component, signal } from '@angular/core';
import { Router } from '@angular/router';
import { Header } from '../../shared/header/header';
import { CodeInput } from '../../shared/code-input/code-input';
import { Button } from '../../shared/button/button';
import { ChatWebSocketService } from '../../services/chat.service';


@Component({
  selector: 'app-driver-code',
  standalone: true,
  imports: [Header, CodeInput, Button],
  templateUrl: './driver-code.html',
  styleUrl: './driver-code.css',
})
export class DriverCode {
  enviando = signal(false);
  erro = signal('');
  chatId = signal('');

  constructor(
    private router: Router,
    private chatService: ChatWebSocketService,
  ) {}

  voltar() {
    this.router.navigate(['/responsible-home'], { queryParams: { perfil: 'aberto' } });
  }

  confirmar(codigo: string) {
    // trava envio duplo enquanto a requisicao anterior nao voltou
    if (this.enviando()) return;

    const codigoLimpo = codigo.trim().toUpperCase();
    if (!/^[A-Z0-9]{9}$/.test(codigoLimpo)) {
      this.erro.set('O código deve ter 9 letras ou números.');
      return;
    }

    this.enviando.set(true);
    this.erro.set('');

    // o backend valida o codigo, acha o motorista e cria (ou recupera) o chat
    this.chatService.createChatMessage(codigoLimpo).subscribe({
      next: (response) => {
        this.enviando.set(false);
        const chatId = response.body;

        if (!chatId) {
          this.erro.set('O vínculo não retornou uma conversa. Tente novamente.');
          return;
        }

        this.chatId.set(chatId);
      },
      error: (err) => {
        this.enviando.set(false);
        this.erro.set(this.mensagemDeErro(err?.status));
      },
    });
  }

  // o redeem hoje joga RuntimeException quando o codigo nao existe, entao o backend responde 500 nesse caso
  private mensagemDeErro(status: number | undefined): string {
    if (status === 0) return 'Não foi possível conectar ao servidor. Verifique sua conexão.';
    if (status === 400) return 'Código em formato inválido. Confira os 9 caracteres.';
    if (status === 403) return 'Sua conta não tem permissão para vincular motoristas.';
    if (status === 404 || (status !== undefined && status >= 500)) {
      return 'Código inválido ou não encontrado. Confira o código com o motorista.';
    }
    return 'Não foi possível vincular o motorista. Tente novamente.';
  }

  abrirConversa() {
    this.router.navigate(['/chat-details', this.chatId()], {
      queryParams: { from: 'responsible-home' },
    });
  }

  irParaConversas() {
    this.router.navigate(['/chat'], { queryParams: { from: 'responsible-home' } });
  }
}
