import { Component } from '@angular/core';
import { Router } from '@angular/router';
import { Header } from '../../shared/header/header';
import { CodeInput } from '../../shared/code-input/code-input';
import { ChatWebSocketService } from '../../services/chat.service';


@Component({
  selector: 'app-driver-code',
  standalone: true,
  imports: [Header, CodeInput],
  templateUrl: './driver-code.html',
  styleUrl: './driver-code.css',
})
export class DriverCode {
  constructor(
    private router: Router,
    private chatService: ChatWebSocketService,
  ) {}

  voltar() {
    this.router.navigate(['/responsible-home']);
  }

  confirmar(codigo: string) {

    this.chatService.createChatMessage(codigo).subscribe({
      next: (response) => {
        const chatId = response.body;
        // url se alguma hora precisar
        const url = response.headers.get('Location');

        if (!chatId) return;

        // manda pro chat
        this.router.navigate(['/chat-details', chatId]);
      },
      error: (err) => {
        console.error(err);
      },
    });
  }
}
