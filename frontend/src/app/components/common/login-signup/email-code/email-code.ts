import { Component, EventEmitter, Input, Output, signal } from '@angular/core';
import { AuthService } from '../../../../services/auth.service';
import { FormsModule } from '@angular/forms';
import { CodeInput } from '../../../../shared/code-input/code-input';
import { Header } from '../../../../shared/header/header';
import { Router } from '@angular/router';

@Component({
  selector: 'app-email-code',
  standalone: true,
  imports: [FormsModule, CodeInput, Header],
  templateUrl: './email-code.html',
  styleUrl: './email-code.css',
})
export class EmailCode {
  // so pra tela: barra no botao Verificar enquanto o codigo e conferido
  protected verificando = signal(false);
  @Output() verificadoEvent = new EventEmitter<void>();
  @Output() voltarEvent = new EventEmitter<void>();
  @Input() email: string = '';

  constructor(
    private authService: AuthService,
    private router: Router,
  ) {}

  voltar() {
    if (this.voltarEvent.observed) {
      this.voltarEvent.emit();
    } else {
      this.router.navigate(['/']);
    }
  }


  onSend(codigo: string) {
    if (this.verificando()) return;
    this.verificando.set(true);
    this.authService.verifyEmail({ email: this.email, code: codigo }).subscribe({
      next: () => {
        this.verificando.set(false);
        this.verificadoEvent.emit();
      },
      error: (err: any) => {
        this.verificando.set(false);
        console.error(err);
      },
    });
  }

  resend() {
    if (this.email !== '') {
      this.authService.sendCode(this.email).subscribe({
        next: (response: any) => console.log(response),
        error: (err: any) => console.error(err),
      });
    }
  }
}
