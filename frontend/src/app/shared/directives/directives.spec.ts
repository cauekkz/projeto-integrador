import { Component } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormsModule } from '@angular/forms';
import { describe, it, expect, beforeEach } from 'vitest';
import { CpfMaskDirective } from './cpf-mask.directive';
import { PhoneMaskDirective } from './phone-mask.directive';
import { CepMaskDirective } from './cep-mask.directive';
import { DateMaskDirective } from './date-mask.directive';

@Component({
  standalone: true,
  imports: [
    FormsModule,
    CpfMaskDirective,
    PhoneMaskDirective,
    CepMaskDirective,
    DateMaskDirective,
  ],
  template: `
    <input id="cpfInput" type="text" [(ngModel)]="cpfValue" appCpfMask />
    <input id="phoneInput" type="text" [(ngModel)]="phoneValue" appPhoneMask />
    <input id="cepInput" type="text" [(ngModel)]="cepValue" appCepMask />
    <input id="dateInput" type="text" [(ngModel)]="dateValue" appDateMask />
  `,
})
class TestHostComponent {
  cpfValue = '';
  phoneValue = '';
  cepValue = '';
  dateValue = '';
}

describe('Mask Directives', () => {
  let fixture: ComponentFixture<TestHostComponent>;
  let component: TestHostComponent;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [TestHostComponent],
    }).compileComponents();

    fixture = TestBed.createComponent(TestHostComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should format CPF while typing', () => {
    const input: HTMLInputElement = fixture.nativeElement.querySelector('#cpfInput');
    input.value = '12345678900';
    input.dispatchEvent(new Event('input'));
    fixture.detectChanges();

    expect(input.value).toBe('123.456.789-00');
    expect(component.cpfValue).toBe('123.456.789-00');
  });

  it('should format Phone while typing', () => {
    const input: HTMLInputElement = fixture.nativeElement.querySelector('#phoneInput');
    input.value = '11987654321';
    input.dispatchEvent(new Event('input'));
    fixture.detectChanges();

    expect(input.value).toBe('(11) 98765-4321');
    expect(component.phoneValue).toBe('(11) 98765-4321');
  });

  it('should format CEP while typing', () => {
    const input: HTMLInputElement = fixture.nativeElement.querySelector('#cepInput');
    input.value = '04165011';
    input.dispatchEvent(new Event('input'));
    fixture.detectChanges();

    expect(input.value).toBe('04165-011');
    expect(component.cepValue).toBe('04165-011');
  });

  it('should format Date while typing', () => {
    const input: HTMLInputElement = fixture.nativeElement.querySelector('#dateInput');
    input.value = '15032012';
    input.dispatchEvent(new Event('input'));
    fixture.detectChanges();

    expect(input.value).toBe('15/03/2012');
    expect(component.dateValue).toBe('15/03/2012');
  });
});
