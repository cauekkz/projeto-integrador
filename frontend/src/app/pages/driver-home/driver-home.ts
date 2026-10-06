import { Component } from '@angular/core';
import { Router } from '@angular/router';

@Component({
  selector: 'app-driver-home',
  standalone: true,
  imports: [],
  templateUrl: './driver-home.html',
  styleUrl: './driver-home.css',
})
export class DriverHome {
  temRota = true;

  constructor(private router: Router) {}

  irParaRota() {
    this.router.navigate(['/driver-route']);
  }
}
