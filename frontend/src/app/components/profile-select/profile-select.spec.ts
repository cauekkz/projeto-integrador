import { ComponentFixture, TestBed } from '@angular/core/testing';

import { ProfileSelect } from './profile-select';

describe('ProfileSelect', () => {
  let component: ProfileSelect;
  let fixture: ComponentFixture<ProfileSelect>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ProfileSelect],
    }).compileComponents();

    fixture = TestBed.createComponent(ProfileSelect);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
