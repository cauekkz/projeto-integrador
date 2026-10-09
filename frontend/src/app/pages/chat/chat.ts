import { Component } from '@angular/core';
import { ChatList } from '../../components/chat/chat-list/chat-list';

@Component({
  selector: 'app-chat',
  standalone: true,
  imports: [ChatList],
  template: `<app-chat-list />`,
})
export class Chat {}
