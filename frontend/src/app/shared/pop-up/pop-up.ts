import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { PopUpService } from './pop-up.service';
import { GlisseVidro } from '../glisse-vidro/glisse-vidro';

@Component({
  selector: 'app-pop-up',
  standalone: true,
  imports: [CommonModule, GlisseVidro],
  templateUrl: './pop-up.html',
  styleUrl: './pop-up.css',
})
export class PopUp {
  constructor(public popUpService: PopUpService) {}

  close(id: string): void {
    this.popUpService.remove(id);
  }
}
