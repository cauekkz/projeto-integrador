import { Component, ElementRef, QueryList, ViewChildren } from '@angular/core';
import { Router } from '@angular/router';
import { Header } from '../../components/header/header';
import { StudentService } from '../../services/student.service';

@Component({
  selector: 'app-student-code',
  standalone: true,
  imports: [Header],
  templateUrl: './student-code.html',
  styleUrl: './student-code.css',
})
export class StudentCode {
  @ViewChildren('codeInput')
  codeInputs!: QueryList<ElementRef<HTMLInputElement>>;

  codigo: string[] = ['', '', '', '', '', '', '', '', ''];

  constructor(
    private StudentService: StudentService,
    private router: Router,
  ) {}

  /**
   * Volta para a tela anterior.
   */
  voltar(): void {
    this.router.navigate(['/home-screen']);
  }

  /**
   * Trata a digitação de cada posição do código.
   */
  onInput(event: Event, index: number): void {
    const input = event.target as HTMLInputElement;

    // Permite somente letras e números
    const valor = input.value
      .replace(/[^a-zA-Z0-9]/g, '')
      .toUpperCase()
      .slice(-1);

    this.codigo[index] = valor;
    input.value = valor;

    // Vai automaticamente para o próximo campo
    if (valor && index < this.codigo.length - 1) {
      this.focarInput(index + 1);
    }
  }

  /**
   * Trata teclas especiais, principalmente Backspace.
   */
  onKeydown(event: KeyboardEvent, index: number): void {
    if (event.key === 'Backspace') {
      const input = event.target as HTMLInputElement;

      if (!input.value && index > 0) {
        this.codigo[index - 1] = '';

        this.focarInput(index - 1);
      }

      return;
    }

    // Permite navegação com as setas
    if (event.key === 'ArrowLeft' && index > 0) {
      event.preventDefault();
      this.focarInput(index - 1);
      return;
    }

    if (event.key === 'ArrowRight' && index < this.codigo.length - 1) {
      event.preventDefault();
      this.focarInput(index + 1);
      return;
    }

    // Permite somente uma tecla válida
    const teclaPermitida = /^[a-zA-Z0-9]$/.test(event.key);

    const teclasEspeciais = [
      'Tab',
      'Shift',
      'Control',
      'Alt',
      'Meta',
      'CapsLock',
      'Escape',
      'Enter',
    ];

    if (!teclaPermitida && !teclasEspeciais.includes(event.key)) {
      event.preventDefault();
    }
  }

  /**
   * Coloca o foco em um campo específico.
   */
  private focarInput(index: number): void {
    setTimeout(() => {
      const inputs = this.codeInputs?.toArray();

      if (inputs?.[index]) {
        inputs[index].nativeElement.focus();
        inputs[index].nativeElement.select();
      }
    });
  }

  /**
   * Retorna o código sem os separadores.
   */
  get codigoCompleto(): string {
    return this.codigo.join('');
  }

  /**
   * Retorna o código formatado como XXX-XXX-XXX.
   */
  get codigoFormatado(): string {
    const codigo = this.codigoCompleto;

    if (codigo.length <= 3) {
      return codigo;
    }

    if (codigo.length <= 6) {
      return `${codigo.slice(0, 3)}-${codigo.slice(3)}`;
    }

    return `${codigo.slice(0, 3)}-${codigo.slice(3, 6)}-${codigo.slice(6, 9)}`;
  }

  /**
   * Verifica se os 9 caracteres foram preenchidos.
   */
  get codigoValido(): boolean {
    return this.codigoCompleto.length === 9;
  }

  confirmar(): void {
    if (!this.codigoValido) {
      return;
    }

    const codigo = this.codigoCompleto;

    this.StudentService.confirmCode(codigo).subscribe({
      next: () => {
        this.router.navigate(['/home-screen']);
      },
      error: (err: any) => {
        console.error('Erro ao confirmar código:', err);
      },
    });
  }
}
