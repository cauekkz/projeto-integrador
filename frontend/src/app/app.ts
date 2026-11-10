import { Component, signal } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { PopUp } from './shared/pop-up';
import { Confirmacao } from './shared/confirmacao/confirmacao';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, PopUp, Confirmacao],
  templateUrl: './app.html',
  styleUrl: './app.css'
})
export class App {
  protected readonly title = signal('frontend');
}
