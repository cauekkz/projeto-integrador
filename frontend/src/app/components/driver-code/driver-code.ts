import { Component } from '@angular/core';
import { Router } from '@angular/router';
import { Header } from '../../shared/header/header';
import { CodeInput } from '../../shared/code-input/code-input';


@Component({
  selector: 'app-driver-code',
  standalone: true,
  imports: [Header, CodeInput],
  templateUrl: './driver-code.html',
  styleUrl: './driver-code.css',
})
export class DriverCode {

  constructor(
    private router: Router,
  ) {}

  voltar() {
    this.router.navigate(['/responsible-home']);
  }

  confirmar(codigo: string) {
    // this.driverService.confirmCode(codigo).subscribe({
    //   next: () => {
    //     this.router.navigate(['/responsible-home']);
    //   },
    //   error: (err: any) => {
       // console.error('Erro ao vincular motorista:', err);
      //},
   // });
  }
}
