import { ComponentFixture, TestBed } from '@angular/core/testing';

import { StudentCode } from './student-code';

describe('StudentCode', () => {
  let component: StudentCode;
  let fixture: ComponentFixture<StudentCode>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [StudentCode],
    }).compileComponents();

    fixture = TestBed.createComponent(StudentCode);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
