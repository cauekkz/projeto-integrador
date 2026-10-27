import { Component, ChangeDetectorRef } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { Header } from '../../../shared/header/header';
import { Button } from '../../../shared/button/button';
import { StudentService } from '../../../services/student.service';
import { NameFormatDirective, DateMaskDirective, CepMaskDirective } from '../../../shared/directives';
import { dataParaIso, limparMascara } from '../../../shared/utils';
import { PopUpService } from '../../../shared/pop-up';

@Component({
  selector: 'app-add-student',
  standalone: true,
  imports: [FormsModule, Header, Button, NameFormatDirective, DateMaskDirective, CepMaskDirective],
  templateUrl: './add-student.html',
  styleUrl: './add-student.css',
})
export class AddStudent {
  etapa: 'form' | 'code' = 'form';

  enteredName = '';
  enteredBirthdate = '';
  enteredObservacoes = '';
  enteredRelationType = '';
  // endereco do dependente (obrigatorio no backend; vira o ponto de embarque no mapa)
  enteredCep = '';
  enteredStreet = '';
  enteredNumber = '';
  enteredNeighborhood = '';
  enteredCity = '';
  enteredState = '';
  maxObs = 1200;
  mostrarBottomSheet = false;

  codigoGerado = '';
  copiado = false;

  constructor(
    private studentService: StudentService,
    private router: Router,
    private cdr: ChangeDetectorRef,
    private popUpService: PopUpService,
  ) {}

  voltar() {
    this.router.navigate(['/responsible-home']);
  }

  pronto() {
    const obrigatorios = [this.enteredName, this.enteredBirthdate, this.enteredRelationType,
      this.enteredCep, this.enteredStreet, this.enteredCity];
    if (obrigatorios.some((valor) => !valor.trim())) {
      this.popUpService.show('Preencha nome, nascimento, tipo de relação, CEP, rua e cidade.', 'warning', 'Campos Incompletos');
      return;
    }

    const birthDate = dataParaIso(this.enteredBirthdate);
    const data = {
      name: this.enteredName,
      notes: this.enteredObservacoes,
      birthDate: birthDate,
      relationType: this.enteredRelationType,
      address: {
        zipCode: limparMascara(this.enteredCep),
        street: this.enteredStreet.trim(),
        number: Number.parseInt(this.enteredNumber, 10) || null,
        neighborhood: this.enteredNeighborhood.trim(),
        city: this.enteredCity.trim(),
        state: this.enteredState.trim().toUpperCase(),
      },
    };

    this.studentService.createStudent(data).subscribe({
      next: (studentResponsible) => {
        const studentId = studentResponsible.studentResponseDTO.id;
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
