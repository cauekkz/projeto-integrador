import { ComponentFixture, TestBed } from '@angular/core/testing';

import { SignupDriver } from './signup-driver';

describe('SignupDriver', () => {
  let component: SignupDriver;
  let fixture: ComponentFixture<SignupDriver>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [SignupDriver]
    })
    .compileComponents();

    fixture = TestBed.createComponent(SignupDriver);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
