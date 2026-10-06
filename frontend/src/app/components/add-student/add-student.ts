import { Component } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { Header } from '../header/header';
import { StudentService } from '../../services/student.service';

@Component({
  selector: 'app-add-student',
  standalone: true,
  imports: [FormsModule, Header],
  templateUrl: './add-student.html',
  styleUrl: './add-student.css',
})
export class AddStudent {
  etapa: 'form' | 'code' = 'form';

  enteredName = '';
  enteredBirthdate = '';
  enteredObservacoes = '';
  enteredRelationType = '';
  maxObs = 1200;
  mostrarBottomSheet = false;

  codigoGerado = '';
  copiado = false;

  constructor(
    private StudentService: StudentService,
    private router: Router,
  ) {}

  voltar() {
    if (this.etapa === 'code') {
      this.etapa = 'form';
      return;
    }

    this.router.navigate(['/home-screen']);
  }

  pronto() {
    const data = {
      name: this.enteredName,
      notes: this.enteredObservacoes,
      birthDate: this.enteredBirthdate,
      relationType: this.enteredRelationType,
    };

    this.StudentService.createStudent(data).subscribe({
      next: (studentResponsible) => {
        const studentId = studentResponsible.student.id;

        this.StudentService.generateStudentLink({
          id: studentId,
          relationType: data.relationType,
        }).subscribe({
          next: (codigo) => {
            this.codigoGerado = codigo;
            this.etapa = 'code';
          },
        });
      },
    });
  }

  copiarCodigo() {
    if (!this.codigoGerado) return;

    navigator.clipboard.writeText(this.codigoGerado).then(() => {
      this.copiado = true;

      setTimeout(() => {
        this.copiado = false;
      }, 2000);
    });
  }

  concluir() {
    this.router.navigate(['/home-screen']);
  }

  abrirBottomSheet() {
    this.mostrarBottomSheet = true;
  }

  fecharBottomSheet() {
    this.mostrarBottomSheet = false;
  }
}
