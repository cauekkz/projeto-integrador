import { Component, Input } from '@angular/core';

@Component({
  selector: 'app-email-code',
  standalone: true,
  imports: [],
  templateUrl: './email-code.html',
  styleUrl: './email-code.css',
})
export class EmailCode {
  @Input() email: string = '';

  codes = ['', '', '', '', '', ''];

  onInput(event: Event, index: number) {
    const input = event.target as HTMLInputElement;
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
}
