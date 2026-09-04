import { Component, OnInit } from '@angular/core';
import { Router, ActivatedRoute } from '@angular/router';
import { Location } from '@angular/common';
import { Header } from '../../shared/header/header';
import { Footer } from '../../shared/footer/footer';

@Component({
  selector: 'app-chat',
  standalone: true,
  imports: [Header, Footer],
  templateUrl: './chat.html',
  styleUrl: './chat.css',
})
export class Chat implements OnInit {
  origem = 'home-screen';

  conversas = [
    {
      id: 1,
      nome: 'Gustavo Gomez',
      ultimaMensagem: 'Ola, Gustavo. Tudo sim, e com voce?',
      hora: '19:14',
      foto: '/testee.jpg',
    }
  ];

  constructor(private router: Router, private route: ActivatedRoute) {}

  ngOnInit() {
    this.origem = this.route.snapshot.queryParamMap.get('from') || 'home-screen';
  }

  abrirConversa(conversa: any) {
    this.router.navigate(['/chat-details', conversa.id], {
      queryParams: { from: this.origem }
    });
  }

  voltar() {
    this.router.navigate([`/${this.origem}`]);
  }
}
