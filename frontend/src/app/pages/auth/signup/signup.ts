import { Component, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { Location } from '@angular/common';

import { SignupResponsible } from '../../../components/signup-responsible/signup-responsible';
import { SignupDriver } from '../../../components/signup-driver/signup-driver';
import { SignupCnhForm } from '../../../components/signup-cnh-form/signup-cnh-form';
import { EmailCode } from '../../../components/email-code/email-code';
import { Button } from '../../../components/button/button';

@Component({
  selector: 'app-signup',
  standalone: true,
  imports: [SignupResponsible, SignupDriver, SignupCnhForm, EmailCode, Button],
  templateUrl: './signup.html',
  styleUrl: './signup.css',
})
export class Signup implements OnInit {
  tipoUsuario = '';
  etapa: 'cnh' | 'driver' = 'cnh';

  cadastroConcluido = false;
  mostrarCodigo = false;

  email = '';

  constructor(
    private route: ActivatedRoute,
    private location: Location,
    private router: Router,
  ) {}

  ngOnInit() {
    this.tipoUsuario = this.route.snapshot.paramMap.get('tipo') || '';
  }

  mostrarTelaCodigo(dados: { email: string }) {
    this.email = dados.email;
    this.mostrarCodigo = true;
  }

  irParaDriver() {
    this.etapa = 'driver';
  }

  concluirCadastro() {
    this.cadastroConcluido = true;
    this.mostrarCodigo = false;
  }

  irParaProximaPagina() {
    this.router.navigate(['/login']);
  }

  voltar() {
    if (this.etapa === 'driver') {
      this.etapa = 'cnh';
    } else {
      this.location.back();
    }
  }
}