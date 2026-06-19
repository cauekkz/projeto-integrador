import { ComponentFixture, TestBed } from '@angular/core/testing';

import { SignupCnhForm } from './signup-cnh-form';

describe('SignupCnhForm', () => {
  let component: SignupCnhForm;
  let fixture: ComponentFixture<SignupCnhForm>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [SignupCnhForm]
    })
    .compileComponents();

    fixture = TestBed.createComponent(SignupCnhForm);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
