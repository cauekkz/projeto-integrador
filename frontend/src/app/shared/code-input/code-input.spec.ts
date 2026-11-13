import { ComponentFixture, TestBed } from '@angular/core/testing';

import { CodeInput } from './code-input';

describe('CodeInput', () => {
  let component: CodeInput;
  let fixture: ComponentFixture<CodeInput>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [CodeInput],
    }).compileComponents();

    fixture = TestBed.createComponent(CodeInput);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('digitar por cima de uma caixa preenchida troca o número', () => {
    fixture.detectChanges();
    const caixas = fixture.nativeElement.querySelectorAll('input.code-input') as NodeListOf<HTMLInputElement>;
    caixas[0].value = '0';
    caixas[0].dispatchEvent(new Event('input'));
    caixas[0].focus();
    caixas[0].setSelectionRange(1, 1);

    const tecla = new KeyboardEvent('keydown', { key: '8', cancelable: true });
    caixas[0].dispatchEvent(tecla);

    expect(tecla.defaultPrevented).toBe(true);
    expect(caixas[0].value).toBe('8');
    expect(component.codes[0]).toBe('8');
  });
});
