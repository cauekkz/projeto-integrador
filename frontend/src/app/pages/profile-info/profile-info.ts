import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { Location } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { AuthService } from '../../services/auth.service';
import { Header } from '../../shared/header/header';

@Component({
  selector: 'app-profile-info',
  standalone: true,
  imports: [Header, FormsModule],
  templateUrl: './profile-info.html',
  styleUrl: './profile-info.css',
})
export class ProfileInfo implements OnInit {
  tipoUsuario = '';
  modoEdicao = false;

  usuario: any = {
    name: '',
    document: '',
    email: '',
    phone: '',
    address: '',
    photoUrl: '',
  };

  enteredName = '';
  enteredEmail = '';
  enteredPhone = '';
  enteredCode = '';
  enteredPassword = '';
  enteredPasswordCode = '';
  mensagem = '';
  salvando = false;

  constructor(
    private route: ActivatedRoute,
    private location: Location,
    private authService: AuthService,
  ) {}

  ngOnInit() {
    this.tipoUsuario = this.route.snapshot.paramMap.get('tipo') || '';
    const id = this.authService.getUserIdFromToken();
    if (id) {
      this.authService.getUserByID(id).subscribe({
        next: (user) => {
          this.usuario = user || {};
          this.enteredName = user?.name || '';
          this.enteredEmail = user?.email || '';
          this.enteredPhone = user?.phone || '';
        },
        error: (err) => console.error(err),
      });
    }
  }

  voltar() {
    this.location.back();
  }

  editar() { this.modoEdicao = true; }

  cancelar() {
    this.modoEdicao = false;
    this.enteredName = this.usuario?.name || '';
    this.enteredEmail = this.usuario?.email || '';
    this.enteredPhone = this.usuario?.phone || '';
    this.enteredCode = '';
    this.enteredPassword = '';
    this.enteredPasswordCode = '';
    this.mensagem = '';
  }

  solicitarCodigo() {
    this.mensagem = '';
    this.authService.sendCodeToUpdateUser().subscribe({
      next: () => {
        this.mensagem = 'Código de verificação enviado para o seu e-mail.';
      },
      error: (err) => {
        console.error(err);
        this.mensagem = 'Não foi possível enviar o código de verificação.';
      },
    });
  }

  salvar() {
    if (!this.enteredCode.trim()) {
      this.mensagem = 'Solicite e informe o código de verificação antes de salvar.';
      return;
    }

    this.salvando = true;
    this.mensagem = '';
    this.authService
      .updateUser({
        email: this.enteredEmail,
        phone: this.enteredPhone,
        code: this.enteredCode,
      })
      .subscribe({
        next: () => {
          this.usuario = {
            ...this.usuario,
            email: this.enteredEmail,
            phone: this.enteredPhone,
          };
          this.modoEdicao = false;
          this.enteredCode = '';
          this.salvando = false;
          this.mensagem = 'Dados atualizados com sucesso.';
        },
        error: (err) => {
          console.error(err);
          this.salvando = false;
          this.mensagem = 'Não foi possível atualizar os dados.';
        },
      });
  }

  alterarSenha() {
    if (!this.enteredPasswordCode.trim() || !this.enteredPassword) {
      this.mensagem = 'Informe a nova senha e o código de verificação.';
      return;
    }

    this.salvando = true;
    this.mensagem = '';
    this.authService
      .updatePassword({
        newPassword: this.enteredPassword,
        code: this.enteredPasswordCode,
      })
      .subscribe({
        next: () => {
          this.enteredPasswordCode = '';
          this.enteredPassword = '';
          this.salvando = false;
          this.mensagem = 'Senha atualizada com sucesso.';
        },
        error: (err) => {
          console.error(err);
          this.salvando = false;
          this.mensagem = 'Não foi possível atualizar a senha.';
        },
      });
  }
}
