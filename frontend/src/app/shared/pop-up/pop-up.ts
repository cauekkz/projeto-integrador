import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { PopUpService } from './pop-up.service';

@Component({
  selector: 'app-pop-up',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './pop-up.html',
  styleUrl: './pop-up.css',
})
export class PopUp {
  constructor(public popUpService: PopUpService) {}

  close(id: string): void {
    this.popUpService.remove(id);
  }
}
