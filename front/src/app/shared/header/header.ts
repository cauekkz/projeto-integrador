import { Component, EventEmitter, Input, Output } from '@angular/core';
@Component({
  selector: 'app-header',
  standalone: true,
  imports: [],
  templateUrl: './header.html',
  styleUrls: ['./header.css'],
})
export class Header {
  @Input() titulo = '';
  @Output() voltarEvent = new EventEmitter<void>();
  voltar() {
    this.voltarEvent.emit();
  }
}
