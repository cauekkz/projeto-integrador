import { Component } from '@angular/core';
import { AddStudent } from '../../components/add-student/add-student';

@Component({
  selector: 'app-home-screen',
  standalone: true,
  imports: [AddStudent],
  templateUrl: './home-screen.html',
  styleUrl: './home-screen.css',
})
export class HomeScreen {
  mostrarAddStudent = false;

  abrirAddStudent() { this.mostrarAddStudent = true; }
  fecharAddStudent() { this.mostrarAddStudent = false; }
}