import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ProfileInfo } from './profile-info';

describe('ProfileInfo', () => {
  let component: ProfileInfo;
  let fixture: ComponentFixture<ProfileInfo>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ProfileInfo],
    }).compileComponents();

    fixture = TestBed.createComponent(ProfileInfo);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
