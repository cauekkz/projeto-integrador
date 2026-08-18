import { ComponentFixture, TestBed } from '@angular/core/testing';

import { AddStudent } from './add-student';

describe('AddStudent', () => {
  let component: AddStudent;
  let fixture: ComponentFixture<AddStudent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AddStudent]
    })
    .compileComponents();

    fixture = TestBed.createComponent(AddStudent);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
