import { ComponentFixture, TestBed } from '@angular/core/testing';

import { Button } from './button';

describe('Button', () => {
  let component: Button;
  let fixture: ComponentFixture<Button>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Button],
    }).compileComponents();

    fixture = TestBed.createComponent(Button);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('fica type="button" por padrão (dentro de form não envia o form junto com o clique)', () => {
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('button').type).toBe('button');
  });

  it('vira submit quando pedido', () => {
    fixture.componentRef.setInput('type', 'submit');
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('button').type).toBe('submit');
  });
});
