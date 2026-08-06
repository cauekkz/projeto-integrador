import { Component, EventEmitter, Input, Output } from '@angular/core';
import { AuthService } from '../../services/auth.service';
import { FormsModule } from '@angular/forms';

@Component({
  selector: 'app-email-code',
  standalone: true,
  imports: [FormsModule],
  templateUrl: './email-code.html',
  styleUrl: './email-code.css',
})
export class EmailCode {
  @Output() verificadoEvent = new EventEmitter<void>();
  constructor(private authService: AuthService) {}
  @Input() email: string = '';

  codes = ['', '', '', '', '', ''];
  completed = 0;

  onInput(event: Event, index: number) {
    const input = event.target as HTMLInputElement;

    input.classList.remove('animate');
    void input.offsetWidth;
    input.classList.add('animate');

    if (input.value.length === 1) {
      const inputs = document.querySelectorAll('.code-input');
      const next = inputs[index + 1] as HTMLInputElement;
      if (next) next.focus();
    }
  }

  onKeydown(event: KeyboardEvent, index: number) {
    const input = event.target as HTMLInputElement;
    if (event.key === 'Backspace' && input.value === '') {
      const inputs = document.querySelectorAll('.code-input');
      const prev = inputs[index - 1] as HTMLInputElement;
      if (prev) prev.focus();
    }
  }

  onSend() {
    this.completed = 0;
    for (let i = 0; i < this.codes.length; i++) {
      if (this.codes[i] !== '') {
        this.completed++;
      }
    }

    if (this.completed === 6) {
      const codigo = this.codes.join('');
      this.authService
        .verifyEmail({
          email: this.email,
          code: codigo,
        })
        .subscribe({
          next: (response) => {
            console.log(response);
            this.verificadoEvent.emit();
          },
          error: (err) => {
            console.error(err);
          },
        });
    }
  }

  resend() {
    if (this.email !== '') {
      this.authService.sendCode(this.email).subscribe({
        next: (response) => {
          console.log(response);
        },
        error: (err) => {
          console.error(err);
        },
      });
    }
  }
}
