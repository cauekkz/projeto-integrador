import { Component, ChangeDetectorRef, OnDestroy } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { Header } from '../../../shared/header/header';
import { Button } from '../../../shared/button/button';
import { StudentService } from '../../../services/student.service';
import { NameFormatDirective, DateMaskDirective, CepMaskDirective } from '../../../shared/directives';
import { dataParaIso, limparMascara } from '../../../shared/utils';
import { PopUpService } from '../../../shared/pop-up';
import { StudentPhotoService } from '../../../services/student-photo.service';

const TIPOS_FOTO = ['image/png', 'image/jpeg', 'image/webp'];
const TAMANHO_MAX_FOTO = 5 * 1024 * 1024;

@Component({
  selector: 'app-add-student',
  standalone: true,
  imports: [FormsModule, Header, Button, NameFormatDirective, DateMaskDirective, CepMaskDirective],
  templateUrl: './add-student.html',
  styleUrl: './add-student.css',
})
export class AddStudent implements OnDestroy {
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
  // foto escolhida da galeria; e guardada pelo StudentPhotoService depois que o dependente e criado
  fotoPreview: string | null = null;
  private fotoArquivo: File | null = null;

  codigoGerado = '';
  copiado = false;
  // trava o "Pronto" enquanto cria: sem isso um clique duplo criava dois dependentes
  salvando = false;
  // dependente ja criado mas o codigo falhou: tentar de novo so gera o codigo, nao cria outro
  private studentIdCriado: string | null = null;

  constructor(
    private studentService: StudentService,
    private router: Router,
    private cdr: ChangeDetectorRef,
    private popUpService: PopUpService,
    private studentPhotoService: StudentPhotoService,
  ) {}

  voltar() {
    this.router.navigate(['/responsible-home']);
  }

  pronto() {
    if (this.salvando) return;

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

    this.salvando = true;

    if (this.studentIdCriado) {
      this.gerarCodigo(this.studentIdCriado, data.relationType);
      return;
    }

    this.studentService.createStudent(data).subscribe({
      next: async (studentResponsible) => {
        const studentId = studentResponsible.studentResponseDTO.id;
        this.studentIdCriado = studentId;
        if (this.fotoArquivo && !(await this.studentPhotoService.salvar(studentId, this.fotoArquivo))) {
          this.popUpService.show('O dependente foi criado, mas a foto não pôde ser salva.', 'warning', 'Foto não salva');
        }
        this.gerarCodigo(studentId, data.relationType);
      },
      // o popup de erro ja vem do interceptor; so libera o botao pra tentar de novo
      error: () => this.liberar(),
    });
  }

  private gerarCodigo(studentId: string, relationType: string) {
    this.studentService.generateStudentLink({ id: studentId, relationType }).subscribe({
      next: (codigo) => {
        this.codigoGerado = codigo;
        this.etapa = 'code';
        this.salvando = false;
        this.cdr.detectChanges();
      },
      error: () => this.liberar(),
    });
  }

  private liberar() {
    this.salvando = false;
    this.cdr.detectChanges();
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

  selecionarFoto(event: Event) {
    const input = event.target as HTMLInputElement;
    const arquivo = input.files?.[0];
    input.value = '';
    if (!arquivo) return;

    if (!TIPOS_FOTO.includes(arquivo.type)) {
      this.popUpService.show('Escolha uma imagem PNG, JPG ou WEBP.', 'warning', 'Formato inválido');
      return;
    }
    if (arquivo.size > TAMANHO_MAX_FOTO) {
      this.popUpService.show('A foto pode ter no máximo 5 MB.', 'warning', 'Imagem muito grande');
      return;
    }

    if (this.fotoPreview) URL.revokeObjectURL(this.fotoPreview);
    this.fotoPreview = URL.createObjectURL(arquivo);
    this.fotoArquivo = arquivo;
  }

  removerFoto() {
    if (this.fotoPreview) URL.revokeObjectURL(this.fotoPreview);
    this.fotoPreview = null;
    this.fotoArquivo = null;
  }

  ngOnDestroy() {
    if (this.fotoPreview) URL.revokeObjectURL(this.fotoPreview);
  }

  vincularDependente() {
    this.router.navigate(['/student-code']);
  }
}
