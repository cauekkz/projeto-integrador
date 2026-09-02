import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Button } from '../../shared/button/button';

@Component({
  selector: 'app-home',
  imports: [RouterLink, Button],
  templateUrl: './home.html',
  styleUrl: './home.css',
})
export class Home {}
