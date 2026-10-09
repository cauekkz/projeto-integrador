import { ComponentFixture, TestBed } from '@angular/core/testing';

import { ChatDetails } from './chat-details';

describe('ChatDetails', () => {
  let component: ChatDetails;
  let fixture: ComponentFixture<ChatDetails>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ChatDetails],
    }).compileComponents();

    fixture = TestBed.createComponent(ChatDetails);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
