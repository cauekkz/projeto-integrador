import { Component, Input, OnInit } from '@angular/core';
import { Router, ActivatedRoute } from '@angular/router';
import { Header } from '../../../shared/header/header';
import { Footer } from '../../../shared/footer/footer';

@Component({
  selector: 'app-chat-list',
  standalone: true,
  imports: [Header, Footer],
  templateUrl: './chat-list.html',
  styleUrl: './chat-list.css',
})
export class ChatList implements OnInit {
  @Input() from = 'responsible-home';
  origem = 'responsible-home';

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
    this.origem = this.route.snapshot.queryParamMap.get('from') || this.from;
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

export { ChatList as ChatListComponent };
