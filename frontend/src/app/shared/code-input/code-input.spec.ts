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
});
