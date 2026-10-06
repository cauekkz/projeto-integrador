import { Component, EventEmitter, Input, Output } from '@angular/core';
import { Location } from '@angular/common';
import { Router } from '@angular/router';

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

  constructor(private location: Location, private router: Router) {}

  voltar() {
    this.voltarEvent.emit();
    if (window.history.length > 1) {
      this.location.back();
    } else {
      this.router.navigateByUrl('/');
    }
  }
}
