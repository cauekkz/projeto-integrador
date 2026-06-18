import { ComponentFixture, TestBed } from '@angular/core/testing';

import { SignupResponsible } from './signup-responsible';

describe('SignupResponsible', () => {
  let component: SignupResponsible;
  let fixture: ComponentFixture<SignupResponsible>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [SignupResponsible]
    })
    .compileComponents();

    fixture = TestBed.createComponent(SignupResponsible);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
