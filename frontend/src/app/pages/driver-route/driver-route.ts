import { Component, OnInit, ChangeDetectorRef, inject } from '@angular/core';
import { NotificationService } from '../../services/notification.service';
import { Location, CommonModule } from '@angular/common';
import { ProfileSelect } from '../../components/profile-select/profile-select';
import { Footer } from '../../shared/footer/footer';
import { AuthService } from '../../services/auth.service';
import { UserMe } from '../../models/user.model';
import { Passageiro, PassengersSheet } from '../../components/passengers-sheet/passengers-sheet';

@Component({
  selector: 'app-driver-route',
  standalone: true,
  imports: [CommonModule, ProfileSelect, Footer, PassengersSheet],
  templateUrl: './driver-route.html',
  styleUrl: './driver-route.css',
})
export class DriverRoute implements OnInit {
  mostrarPerfil = false;
  usuarioLogado: UserMe | null = null;
  // comecou a arrastar a bolinha ou painel aberto: o footer some
  footerOculto = false;

  // TODO: trocar pelos alunos da viagem do dia (GET /api/trip/{tripId}) quando a tela receber a rota
  alunosEsperando: Passageiro[] = [
    { nome: 'Fiaco Lopez', endereco: 'R. Libertadores 2026' },
    { nome: 'Agustin Giay', endereco: 'Av. Paulistão 2026' },
    { nome: 'Paulinho', endereco: 'Av. Raça Fia 3' },
    { nome: 'Jhon Arias', endereco: 'Av. Botafogo e bairro 2024' },
  ];

  alunosEmbarcados: Passageiro[] = [
    { nome: 'Joaquin Piquerez', endereco: 'João Freire, 59' },
    { nome: 'Marlon Freitas', endereco: 'Av. Palmeiras 1914' },
    { nome: 'Andreas Pereira', endereco: 'Av. Carrollo 1951' },
  ];

  // badge do sino: nao lidas de verdade (GET /api/notifications/unread-count + WebSocket)
  protected notificacoes = inject(NotificationService);

  constructor(
    private location: Location,
    private authService: AuthService,
    private cdr: ChangeDetectorRef,
  ) {}

  ngOnInit() {
    this.notificacoes.iniciar();
    this.authService.getMe().subscribe({
      next: (user) => {
        this.usuarioLogado = user;
        this.cdr.detectChanges();
      },
      error: (err) => console.error('Erro ao buscar motorista:', err),
    });
  }

  voltar() {
    this.location.back();
  }

  irParaPerfil() {
    this.mostrarPerfil = true;
  }

  fecharPerfil() {
    this.mostrarPerfil = false;
  }
}
