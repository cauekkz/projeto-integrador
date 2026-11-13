import { Component, EventEmitter, Input, OnInit, Output, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { AuthService } from '../../../services/auth.service';
import { PopUpService } from '../../../shared/pop-up';

export type BloqueioExclusao = 'contratos' | 'parcelas';

// Figma "Etapa 10 · Privacidade": folhas "Excluir sua conta?" e "Confirme com sua senha"
// usada em /privacy e no "Não aceito" de /terms (la o aviso ja aparece em pop-up, entao pula a primeira folha)
// erros: o interceptor mostra o pop-up com a mensagem do backend (400 "Senha incorreta", 409 contrato/parcela)
@Component({
  selector: 'app-delete-account',
  standalone: true,
  imports: [FormsModule],
  templateUrl: './delete-account.html',
  styleUrls: ['../../../shared/folha.css', './delete-account.css'],
})
export class DeleteAccount implements OnInit {
  private authService = inject(AuthService);
  private popUpService = inject(PopUpService);
  private router = inject(Router);

  @Input() pularAviso = false;
  @Output() fecharEvent = new EventEmitter<void>();
  // 409: a tela mostra o botao "Ver contratos" ou "Ver parcelas"
  @Output() bloqueadoEvent = new EventEmitter<BloqueioExclusao>();

  protected passo = signal<'aviso' | 'senha'>('aviso');
  protected senha = '';
  protected enviando = signal(false);

  ngOnInit() {
    if (this.pularAviso) this.passo.set('senha');
  }

  continuar() {
    this.passo.set('senha');
  }

  fechar() {
    if (!this.enviando()) this.fecharEvent.emit();
  }

  excluir() {
    if (this.enviando()) return;
    if (!this.senha) {
      this.popUpService.show('Digite sua senha', 'warning', 'Campos Incompletos');
      return;
    }

    this.enviando.set(true);
    this.authService.deleteMyAccount(this.senha).subscribe({
      // 204: a conta nao existe mais; o WebSocket de notificacoes fecha sozinho (1008) e nao reconecta sem token
      next: () => {
        this.enviando.set(false);
        this.authService.clearTokens();
        this.popUpService.show('Sua conta foi excluída.', 'success', 'Conta excluída');
        this.router.navigate(['/']);
      },
      error: (erro: HttpErrorResponse) => {
        this.enviando.set(false);
        this.senha = '';
        if (erro.status === 409) {
          const mensagem = String(erro.error?.message ?? '').toLowerCase();
          this.bloqueadoEvent.emit(mensagem.includes('parcela') ? 'parcelas' : 'contratos');
          this.fecharEvent.emit();
        }
      },
    });
  }
}
