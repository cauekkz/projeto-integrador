import { Component, DestroyRef, PLATFORM_ID, inject, signal } from '@angular/core';
import { isPlatformBrowser } from '@angular/common';
import { ligarArrastarFolha } from './shared/arrastar-folha/arrastar-folha';
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

  constructor() {
    // puxar pra baixo fecha qualquer folha de baixo do app
    if (isPlatformBrowser(inject(PLATFORM_ID))) {
      const desligar = ligarArrastarFolha();
      inject(DestroyRef).onDestroy(desligar);
    }
  }
}
