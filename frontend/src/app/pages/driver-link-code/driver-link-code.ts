import { Component, OnInit, signal } from '@angular/core';
import { Router } from '@angular/router';
import { Header } from '../../shared/header/header';
import { Button } from '../../shared/button/button';
import { ChatWebSocketService } from '../../services/chat.service';

@Component({
  selector: 'app-driver-link-code',
  standalone: true,
  imports: [Header, Button],
  templateUrl: './driver-link-code.html',
  styleUrl: './driver-link-code.css',
})
export class DriverLinkCode implements OnInit {
  codigo = signal('');
  carregando = signal(true);
  erro = signal('');
  copiado = signal(false);

  constructor(
    private router: Router,
    private chatService: ChatWebSocketService,
  ) {}

  ngOnInit() {
    this.carregarCodigo();
  }

  // o codigo e gerado no cadastro e fica salvo no backend, aqui so consulta
  carregarCodigo() {
    this.carregando.set(true);
    this.erro.set('');

    this.chatService.getDriverLinkCode().subscribe({
      next: (resposta) => {
        this.codigo.set(resposta?.linkCode ?? '');
        if (!this.codigo()) {
          this.erro.set('Seu código de conversa não está disponível no momento.');
        }
        this.carregando.set(false);
      },
      error: (err) => {
        if (err?.status === 0) {
          this.erro.set('Não foi possível conectar ao servidor. Verifique sua conexão.');
        } else if (err?.status === 404) {
          this.erro.set('Não encontramos um código de conversa para a sua conta.');
        } else {
          this.erro.set('Não foi possível carregar seu código. Tente novamente.');
        }
        this.carregando.set(false);
      },
    });
  }

  // mostra separado de 3 em 3 igual a tela de digitar o codigo (XXX-XXX-XXX)
  codigoFormatado() {
    return this.codigo().match(/.{1,3}/g)?.join('-') ?? '';
  }

  copiarCodigo() {
    if (!this.codigo()) return;

    navigator.clipboard
      .writeText(this.codigo())
      .then(() => {
        this.copiado.set(true);
        setTimeout(() => this.copiado.set(false), 2000);
      })
      .catch(() => {
        this.erro.set('Não foi possível copiar automaticamente. Copie o código manualmente.');
      });
  }

  voltarAoPerfil() {
    this.router.navigate(['/driver-home'], { queryParams: { perfil: 'aberto' } });
  }
}
