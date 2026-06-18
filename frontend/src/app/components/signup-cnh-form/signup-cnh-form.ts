import { Component, EventEmitter, Output } from '@angular/core';
import { RegisterService } from '../../services/register.service';

@Component({
  selector: 'app-signup-cnh-form',
  standalone: true,
  imports: [],
  templateUrl: './signup-cnh-form.html',
  styleUrl: './signup-cnh-form.css',
})
export class SignupCnhForm {
  @Output() prontoEvent = new EventEmitter<void>();
  @Output() voltarEvent = new EventEmitter<void>();

  constructor(protected registerService: RegisterService) {}
  mostrarBottomSheet = false;

  abrirBottomSheet() {
    this.mostrarBottomSheet = true;
  }

  fecharBottomSheet() {
    this.mostrarBottomSheet = false;
  }

  onUploadConcluido() {
    this.mostrarBottomSheet = false;
    this.prontoEvent.emit();
  }

  pronto() {
    if (this.registerService.selectedFile == null){
      return
    }
    this.prontoEvent.emit();
  }

  voltar() {
    this.voltarEvent.emit();
  }



onFileSelected(event: Event) {
  const input = event.target as HTMLInputElement;

  if (input.files?.length) {
    this.registerService.selectedFile = input.files[0];
    console.log(this.registerService.selectedFile)
  }
}

delete() {
  this.registerService.selectedFile = null;
}


}
