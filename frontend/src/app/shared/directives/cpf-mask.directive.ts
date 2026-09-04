import { Directive, ElementRef, HostListener, Optional, Self } from '@angular/core';
import { NgControl } from '@angular/forms';

@Directive({
  selector: '[appCpfMask]',
  standalone: true,
})
export class CpfMaskDirective {
  constructor(
    private el: ElementRef<HTMLInputElement>,
    @Optional() @Self() private ngControl: NgControl
  ) {}

  @HostListener('input')
  onInput(): void {
    const input = this.el.nativeElement;
    const rawValue = input.value;
    const formatted = this.formatCpf(rawValue);

    if (input.value !== formatted) {
      input.value = formatted;
      if (this.ngControl && this.ngControl.control) {
        this.ngControl.control.setValue(formatted, { emitEvent: false });
      }
    }
  }

  private formatCpf(value: string): string {
    const digits = value.replace(/\D/g, '').slice(0, 11);
    if (!digits) return '';
    if (digits.length <= 3) return digits;
    if (digits.length <= 6) return `${digits.slice(0, 3)}.${digits.slice(3)}`;
    if (digits.length <= 9) return `${digits.slice(0, 3)}.${digits.slice(3, 6)}.${digits.slice(6)}`;
    return `${digits.slice(0, 3)}.${digits.slice(3, 6)}.${digits.slice(6, 9)}-${digits.slice(9, 11)}`;
  }
}
