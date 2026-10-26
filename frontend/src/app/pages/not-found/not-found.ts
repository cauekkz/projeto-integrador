import { Component } from '@angular/core';
import { Router } from '@angular/router';
import { Button } from '../../shared/button/button';

@Component({
  selector: 'app-not-found',
  standalone: true,
  imports: [Button],
  templateUrl: './not-found.html',
  styleUrl: './not-found.css',
})
export class NotFound {
  constructor(private router: Router) {}

  // '/' tem guestGuard: se tiver logado ja cai na home da role
  voltarInicio() {
    this.router.navigate(['/']);
  }
}
