import { ComponentFixture, TestBed } from '@angular/core/testing';

import { DriverChat } from './driver-chat';

describe('DriverChat', () => {
  let component: DriverChat;
  let fixture: ComponentFixture<DriverChat>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [DriverChat],
    }).compileComponents();

    fixture = TestBed.createComponent(DriverChat);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
