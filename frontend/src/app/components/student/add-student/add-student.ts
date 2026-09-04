import { Component, ChangeDetectorRef } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { Header } from '../../../shared/header/header';
import { Button } from '../../../shared/button/button';
import { StudentService } from '../../../services/student.service';
import { NameFormatDirective, DateMaskDirective } from '../../../shared/directives';

@Component({
  selector: 'app-add-student',
  standalone: true,
  imports: [FormsModule, Header, Button, NameFormatDirective, DateMaskDirective],
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
    private studentService: StudentService,
    private router: Router,
    private cdr: ChangeDetectorRef,
  ) {}

  voltar() {
    this.router.navigate(['/home-screen']);
  }

  pronto() {
    const [dia, mes, ano] = this.enteredBirthdate.split('/');
    const birthDate = `${ano}-${mes}-${dia}`;
    const data = {
      name: this.enteredName,
      notes: this.enteredObservacoes,
      birthDate: birthDate,
      relationType: this.enteredRelationType,
    };

    this.studentService.createStudent(data).subscribe({
      next: (studentResponsible) => {
        const studentId = studentResponsible.student.id;
        this.studentService
          .generateStudentLink({
            id: studentId,
            relationType: data.relationType,
          })
          .subscribe({
            next: (codigo) => {
              this.codigoGerado = codigo;
              this.etapa = 'code';
              this.cdr.detectChanges();
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
    this.router.navigate(['/dependentes']);
  }

  abrirBottomSheet() {
    this.mostrarBottomSheet = true;
  }
  fecharBottomSheet() {
    this.mostrarBottomSheet = false;
  }

  vincularDependente() {
    this.router.navigate(['/student-code']);
  }
}
