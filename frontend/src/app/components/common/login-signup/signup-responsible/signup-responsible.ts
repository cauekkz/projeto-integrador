import { Component, EventEmitter, Output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { AuthService } from '../../../../services/auth.service';
import { Header } from '../../../../shared/header/header';
import { CommonModule } from '@angular/common';
import { CpfMaskDirective, PhoneMaskDirective, NameFormatDirective, CepMaskDirective, CepBuscaDirective, EnderecoCep, PasswordToggleDirective } from '../../../../shared/directives';
import { PopUpService } from '../../../../shared/pop-up';
import { limparMascara } from '../../../../shared/utils';
import { TermsAccept } from '../terms-accept/terms-accept';

import { BotaoCarregandoDirective } from '../../../../shared/botao-carregando/botao-carregando.directive';
@Component({
  selector: 'app-signup-responsible',
  standalone: true,
  imports: [BotaoCarregandoDirective, FormsModule, Header, CommonModule, CpfMaskDirective, PhoneMaskDirective, NameFormatDirective, CepMaskDirective, CepBuscaDirective, PasswordToggleDirective, TermsAccept],
  templateUrl: './signup-responsible.html',
  styleUrls: ['../../../../shared/cadastro.css', './signup-responsible.css'],
})
export class SignupResponsible {
  // so pra tela: barra no botao enquanto o cadastro responde
  protected cadastrando = signal(false);
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
  // versao dos termos marcada no checkbox (o backend exige a atual no cadastro)
  versaoTermos: string | null = null;

  // o backend exige o endereco no cadastro (ele vira coordenada pro mapa da rota)
  enteredCep = '';
  enteredStreet = '';
  enteredNumber = '';
  enteredNeighborhood = '';
  enteredCity = '';
  enteredState = '';

  // CEP achado no ViaCEP: preenche o que veio (CEP de cidade pequena pode vir sem rua/bairro)
  preencherEndereco(endereco: EnderecoCep) {
    if (endereco.rua) this.enteredStreet = endereco.rua;
    if (endereco.bairro) this.enteredNeighborhood = endereco.bairro;
    if (endereco.cidade) this.enteredCity = endereco.cidade;
    if (endereco.uf) this.enteredState = endereco.uf;
  }

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

    if (!this.versaoTermos) {
      this.popUpService.show('Aceite os Termos de Uso e a Política de Privacidade para continuar.', 'warning', 'Termos de Uso');
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
      acceptedTermsVersion: this.versaoTermos,
    };

    if (this.cadastrando()) return;
    this.cadastrando.set(true);
    this.authService.createUser(data).subscribe({
      next: () => {
        this.cadastrando.set(false);
        this.cadastroConcluidoEvent.emit({
          email: this.enteredEmail,
        });
      },
      // o popup global mostra a mensagem do backend (CPF invalido, e-mail em uso, senha fraca...)
      error: () => this.cadastrando.set(false),
    });
  }
}
