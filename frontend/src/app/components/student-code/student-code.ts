import { Component } from '@angular/core';

@Component({
  selector: 'app-student-code',
  standalone: true,
  imports: [],
  templateUrl: './student-code.html',
  styleUrl: './student-code.css',
})
export class StudentCode {


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
}
