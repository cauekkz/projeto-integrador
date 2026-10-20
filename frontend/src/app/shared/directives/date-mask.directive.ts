import { Directive, ElementRef, HostListener, OnInit, Optional, Self } from '@angular/core';
import { NgControl } from '@angular/forms';
import { formatarData } from '../utils/mask.utils';

@Directive({
  selector: '[appDateMask]',
  standalone: true,
})
export class DateMaskDirective implements OnInit {
  constructor(
    private el: ElementRef<HTMLInputElement>,
    @Optional() @Self() private ngControl: NgControl
  ) {}

  ngOnInit(): void {
    this.applyMask();
  }

  @HostListener('input')
  onInput(): void {
    this.applyMask();
  }

  @HostListener('blur')
  onBlur(): void {
    this.applyMask();
  }

  private applyMask(): void {
    const input = this.el.nativeElement;
    const rawValue = input.value;
    const formatted = formatarData(rawValue);

    if (input.value !== formatted) {
      input.value = formatted;
      if (this.ngControl && this.ngControl.control) {
        this.ngControl.control.setValue(formatted, { emitEvent: false });
      }
    }
  }
}
