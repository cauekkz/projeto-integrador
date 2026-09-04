import { Component, EventEmitter, Input, Output } from '@angular/core';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-header',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './header.html',
  styleUrls: ['./header.css'],
})
export class Header {
  // Se não passar nada, assume 'geral' por padrão
  @Input() tipo: 'geral' | 'driver' = 'geral';

  // Props do header normal/geral
  @Input() titulo: string = '';

  // Props do header de motorista
  @Input() nomeUsuario: string = '';
  @Input() fotoUrl: string = '/testee.jpg';
  @Input() role: string = 'Motorista';
  @Input() temNotificacao: boolean = false;
  @Input() qtdNotificacoes: number = 0;

  // Eventos
  @Output() voltarEvent = new EventEmitter<void>();
  @Output() perfilEvent = new EventEmitter<void>();
  @Output() notificacaoEvent = new EventEmitter<void>();

  voltar() {
    this.voltarEvent.emit();
  }

  irParaPerfil() {
    this.perfilEvent.emit();
  }

  abrirNotificacoes() {
    this.notificacaoEvent.emit();
  }
}
