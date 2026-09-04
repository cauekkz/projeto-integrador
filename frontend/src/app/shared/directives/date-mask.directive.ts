import { Directive, ElementRef, HostListener, Optional, Self } from '@angular/core';
import { NgControl } from '@angular/forms';

@Directive({
  selector: '[appDateMask]',
  standalone: true,
})
export class DateMaskDirective {
  constructor(
    private el: ElementRef<HTMLInputElement>,
    @Optional() @Self() private ngControl: NgControl
  ) {}

  @HostListener('input')
  onInput(): void {
    const input = this.el.nativeElement;
    const rawValue = input.value;
    const formatted = this.formatDate(rawValue);

    if (input.value !== formatted) {
      input.value = formatted;
      if (this.ngControl && this.ngControl.control) {
        this.ngControl.control.setValue(formatted, { emitEvent: false });
      }
    }
  }

  private formatDate(value: string): string {
    const digits = value.replace(/\D/g, '').slice(0, 8);
    if (!digits) return '';
    if (digits.length <= 2) return digits;
    if (digits.length <= 4) return `${digits.slice(0, 2)}/${digits.slice(2)}`;
    return `${digits.slice(0, 2)}/${digits.slice(2, 4)}/${digits.slice(4, 8)}`;
  }
}
