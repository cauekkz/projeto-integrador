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
