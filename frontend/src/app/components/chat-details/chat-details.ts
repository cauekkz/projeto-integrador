import { Component, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { CommonModule } from '@angular/common';
import { Header } from '../../shared/header/header';

@Component({
  selector: 'app-chat-details',
  standalone: true,
  imports: [CommonModule, FormsModule, Header],
  templateUrl: './chat-details.html',
  styleUrl: './chat-details.css',
})
export class ChatDetails implements OnInit {
  mensagem = '';
  nomeContato = 'Gustavo Gomez';
  fotoContato = '/testee.jpg';
  origem = 'home-screen';

  mensagens = [
    { texto: 'Olá, Andreas. Tudo bem?', minha: true, hora: '19:14', lida: true },
    { texto: 'Olá, Andreas. Tudo bem?', minha: false, hora: '19:14', lida: false },
    { texto: 'Olá, Gustavo. Tudo sim, e com você?', minha: false, hora: '19:14', lida: false },
  ];

  // vou excluir isso dai de cima, claro

  // como pego chatID?
  // de resto só criar conexão, carregar as msg pelo get message
  // a cada msg enviada chama o rest e o onmessage pra carregar as msg q vão ser enviadas no momento da conversa
  // primeiro tenho q ver como pegar o chatID e separar isso aq do chat-details e do chat normal em questão de qual endpoint puxa

  constructor(private route: ActivatedRoute, private router: Router) {}

  ngOnInit() {
    this.origem = this.route.snapshot.queryParamMap.get('from') || 'home-screen';
  }

  voltar() {
    this.router.navigate(['/chat'], { queryParams: { from: this.origem } });
  }

  enviar() {
    if (!this.mensagem.trim()) return;
    this.mensagens.push({
      texto: this.mensagem,
      minha: true,
      hora: new Date().toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' }),
      lida: false,
    });
    this.mensagem = '';
  }
}
