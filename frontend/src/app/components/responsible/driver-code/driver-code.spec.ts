import { ComponentFixture, TestBed } from '@angular/core/testing';

import { DriverCode } from './driver-code';

describe('DriverCode', () => {
  let component: DriverCode;
  let fixture: ComponentFixture<DriverCode>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [DriverCode],
    }).compileComponents();

    fixture = TestBed.createComponent(DriverCode);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
