import { Component, OnInit, ChangeDetectionStrategy } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { AuthService } from '../../services/auth.service';
import { Header } from '../../components/header/header';

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

  // INICIALIZE COMO OBJETO VAZIO em vez de 'null'
  // Isso impede que o [(ngModel)] trave a renderização do template
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

  constructor(
    private route: ActivatedRoute,
    private router: Router,
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
    this.router.navigate([this.tipoUsuario === 'motorista' ? '/driver-home' : '/home-screen']);
  }

  editar() {
    this.modoEdicao = true;
  }

  cancelar() {
    this.modoEdicao = false;
    this.enteredName = this.usuario?.name || '';
    this.enteredEmail = this.usuario?.email || '';
    this.enteredPhone = this.usuario?.phone || '';
  }

  salvar() {
    this.modoEdicao = false;
  }
}
