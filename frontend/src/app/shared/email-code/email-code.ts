import { Component, EventEmitter, Input, Output } from '@angular/core';
import { AuthService } from '../../../services/auth.service';
import { FormsModule } from '@angular/forms';
import { CodeInput } from '../../../shared/code-input/code-input';
import { Header } from '../../../shared/header/header';
import { Router } from '@angular/router';

@Component({
  selector: 'app-email-code',
  standalone: true,
  imports: [FormsModule, CodeInput, Header],
  templateUrl: './email-code.html',
  styleUrl: './email-code.css',
})
export class EmailCode {
  @Output() verificadoEvent = new EventEmitter<void>();
  @Input() email: string = '';

  constructor(
    private authService: AuthService,
    private router: Router,
  ) {}

  voltar() {
    this.router.navigate(['/']);
  }

  onSend(codigo: string) {
    this.authService.verifyEmail({ email: this.email, code: codigo }).subscribe({
      next: () => this.verificadoEvent.emit(),
      error: (err) => console.error(err),
    });
  }

  resend() {
    if (this.email !== '') {
      this.authService.sendCode(this.email).subscribe({
        next: (response) => console.log(response),
        error: (err) => console.error(err),
      });
    }
  }
}
