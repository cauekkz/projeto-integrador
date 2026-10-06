import { ComponentFixture, TestBed } from '@angular/core/testing';

import { EmailCode } from './email-code';

describe('EmailCode', () => {
  let component: EmailCode;
  let fixture: ComponentFixture<EmailCode>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [EmailCode],
    }).compileComponents();

    fixture = TestBed.createComponent(EmailCode);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
