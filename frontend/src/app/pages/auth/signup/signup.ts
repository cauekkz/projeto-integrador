import { Component, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { Location } from '@angular/common';
import { SignupResponsible } from '../../../components/signup-responsible/signup-responsible';
import { SignupDriver } from '../../../components/signup-driver/signup-driver';
import { SignupCnhForm } from '../../../components/signup-cnh-form/signup-cnh-form';

@Component({
  selector: 'app-signup',
  standalone: true,
  imports: [SignupResponsible, SignupDriver, SignupCnhForm],
  templateUrl: './signup.html',
  styleUrl: './signup.css',
})
export class Signup implements OnInit {
  tipoUsuario = '';
  etapa: 'cnh' | 'driver' = 'cnh';
  cadastroConcluido = false;

  constructor(
    private route: ActivatedRoute,
    private location: Location,
    private router: Router,
  ) {}

  ngOnInit() {
    this.tipoUsuario = this.route.snapshot.paramMap.get('tipo') || '';
  }

  irParaDriver() {
    this.etapa = 'driver';
  }

  concluirCadastro() {
    this.cadastroConcluido = true;
  }

  irParaProximaPagina() {
    if (this.tipoUsuario === 'motorista') {
      this.router.navigate(['/dashboard/motorista']); // troca pela rota do motorista
    } else {
      this.router.navigate(['/dashboard/responsavel']); // troca pela rota do responsável
    }
  }

  voltar() {
    if (this.etapa === 'driver') {
      this.etapa = 'cnh';
    } else {
      this.location.back();
    }
  }
}
