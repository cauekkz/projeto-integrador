import { ComponentFixture, TestBed } from '@angular/core/testing';

import { HomeScreen } from './home-screen';

describe('HomeScreen', () => {
  let component: HomeScreen;
  let fixture: ComponentFixture<HomeScreen>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [HomeScreen]
    })
    .compileComponents();

    fixture = TestBed.createComponent(HomeScreen);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
