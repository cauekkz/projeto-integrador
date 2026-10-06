import { ComponentFixture, TestBed } from '@angular/core/testing';

import { DriverRoute } from './driver-route';

describe('DriverRoute', () => {
  let component: DriverRoute;
  let fixture: ComponentFixture<DriverRoute>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [DriverRoute],
    }).compileComponents();

    fixture = TestBed.createComponent(DriverRoute);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
