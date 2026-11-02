import { Directive, ElementRef, HostListener, Optional, Self } from '@angular/core';
import { NgControl } from '@angular/forms';

@Directive({
  selector: '[appNameFormat]',
  standalone: true,
})
export class NameFormatDirective {
  constructor(
    private el: ElementRef<HTMLInputElement>,
    @Optional() @Self() private ngControl: NgControl
  ) {}

  @HostListener('input')
  onInput(): void {
    const input = this.el.nativeElement;
    const rawValue = input.value;
    const selectionStart = input.selectionStart;
    const selectionEnd = input.selectionEnd;

    const formatted = this.formatName(rawValue);

    if (input.value !== formatted) {
      input.value = formatted;
      if (selectionStart !== null && selectionEnd !== null) {
        input.setSelectionRange(selectionStart, selectionEnd);
      }
      if (this.ngControl && this.ngControl.control) {
        this.ngControl.control.setValue(formatted, { emitEvent: false });
      }
    }
  }

  private formatName(value: string): string {
    if (!value) return '';

    const prepositions = new Set(['de', 'da', 'do', 'dos', 'das', 'e']);
    const parts = value.split(' ');
    let isFirstWordFound = false;

    const formattedParts = parts.map((part) => {
      if (!part) return part;

      const lower = part.toLowerCase();
      if (!isFirstWordFound) {
        isFirstWordFound = true;
        return lower.charAt(0).toUpperCase() + lower.slice(1);
      }

      if (prepositions.has(lower)) {
        return lower;
      }

      return lower.charAt(0).toUpperCase() + lower.slice(1);
    });

    return formattedParts.join(' ');
  }
}
