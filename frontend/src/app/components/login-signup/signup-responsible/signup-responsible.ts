import { Component, EventEmitter, Output } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { AuthService } from '../../../services/auth.service';
import { Header } from '../../../shared/header/header';
import { Button } from '../../../shared/button/button';
import { CommonModule } from '@angular/common';
import { CpfMaskDirective, PhoneMaskDirective, NameFormatDirective, CepMaskDirective } from '../../../shared/directives';
import { PopUpService } from '../../../shared/pop-up';
import { limparMascara } from '../../../shared/utils';

@Component({
  selector: 'app-signup-responsible',
  standalone: true,
  imports: [FormsModule, Header, Button, CommonModule, CpfMaskDirective, PhoneMaskDirective, NameFormatDirective, CepMaskDirective],
  templateUrl: './signup-responsible.html',
  styleUrl: './signup-responsible.css',
})
export class SignupResponsible {
  @Output() voltarEvent = new EventEmitter<void>();
  @Output() cadastroConcluidoEvent = new EventEmitter<{
    email: string;
  }>();

  constructor(
    private authService: AuthService,
    private router: Router,
    private popUpService: PopUpService,
  ) {}

  cpfInvalido: boolean = false;
  nameInvalido: boolean = false;
  emailInvalido: boolean = false;
  passInvalido: boolean = false;
  confirmPassInvalido: boolean = false;
  phoneInvalido: boolean = false;
  passwordsDontMatch: boolean = false;
  cepInvalido: boolean = false;
  streetInvalido: boolean = false;
  cityInvalido: boolean = false;

  enteredCpf = '';
  enteredName = '';
  enteredEmail = '';
  enteredPass = '';
  enteredConfirmPass = '';
  enteredPhone = '';

  // o backend exige o endereco no cadastro (ele vira coordenada pro mapa da rota)
  enteredCep = '';
  enteredStreet = '';
  enteredNumber = '';
  enteredNeighborhood = '';
  enteredCity = '';
  enteredState = '';

  verification(value: string): boolean {
    return value.trim() === '';
  }

  voltar() {
    this.voltarEvent.emit();
  }

  signup() {
    this.cpfInvalido = this.verification(this.enteredCpf);
    this.nameInvalido = this.verification(this.enteredName);
    this.emailInvalido = this.verification(this.enteredEmail);
    this.passInvalido = this.verification(this.enteredPass);
    this.confirmPassInvalido = this.verification(this.enteredConfirmPass);
    this.phoneInvalido = this.verification(this.enteredPhone);
    this.cepInvalido = this.verification(this.enteredCep);
    this.streetInvalido = this.verification(this.enteredStreet);
    this.cityInvalido = this.verification(this.enteredCity);

    if (
      this.cpfInvalido ||
      this.nameInvalido ||
      this.emailInvalido ||
      this.passInvalido ||
      this.confirmPassInvalido ||
      this.phoneInvalido ||
      this.cepInvalido ||
      this.streetInvalido ||
      this.cityInvalido
    ) {
      this.popUpService.show('Por favor, preencha todos os campos obrigatórios.', 'warning', 'Campos Incompletos');
      return;
    }

    this.passwordsDontMatch = false;

    if (this.enteredPass !== this.enteredConfirmPass) {
      this.passwordsDontMatch = true;
      this.popUpService.show('As senhas digitadas não coincidem.', 'error', 'Senhas Incompatíveis');
      return;
    }

    const data = {
      name: this.enteredName,
      cpf: limparMascara(this.enteredCpf),
      email: this.enteredEmail,
      phone: limparMascara(this.enteredPhone),
      password: this.enteredPass,
      confirmPassword: this.enteredConfirmPass,
      address: {
        zipCode: limparMascara(this.enteredCep),
        street: this.enteredStreet.trim(),
        number: Number.parseInt(this.enteredNumber, 10) || null,
        neighborhood: this.enteredNeighborhood.trim(),
        city: this.enteredCity.trim(),
        state: this.enteredState.trim().toUpperCase(),
      },
    };

    this.authService.createUser(data).subscribe({
      next: (response) => {
        this.cadastroConcluidoEvent.emit({
          email: this.enteredEmail,
        });
        console.log(response);
      },
      error: (err) => {
        console.error(err);
      },
    });
  }
}
