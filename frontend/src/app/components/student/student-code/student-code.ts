import { Component } from '@angular/core';
import { Router } from '@angular/router';
import { Header } from '../../../shared/header/header';
import { CodeInput } from '../../../shared/code-input/code-input';
import { StudentService } from '../../../services/student.service';

@Component({
  selector: 'app-student-code',
  standalone: true,
  imports: [Header, CodeInput],
  templateUrl: './student-code.html',
  styleUrl: './student-code.css',
})
export class StudentCode {

  constructor(
    private studentService: StudentService,
    private router: Router,
  ) {}

  voltar(): void {
    this.router.navigate(['/add-student']);
  }

  confirmar(codigo: string): void {
    this.studentService.confirmCode(codigo).subscribe({
      next: () => {
        this.router.navigate(['/responsible-home']);
      },
      error: (err: any) => {
        console.error('Erro ao confirmar código:', err);
      },
    });
  }
}
