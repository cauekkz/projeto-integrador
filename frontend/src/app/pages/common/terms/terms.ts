import { Skeleton } from '../../../shared/skeleton/skeleton';
import { Component, OnInit, inject, signal } from '@angular/core';
import { Location } from '@angular/common';
import { ActivatedRoute, Router } from '@angular/router';
import { Header } from '../../../shared/header/header';
import { TermsService, Terms } from '../../../services/terms.service';
import { AuthService } from '../../../services/auth.service';
import { PopUpService } from '../../../shared/pop-up';
import { DeleteAccount } from '../../../components/common/delete-account/delete-account';
import { MarkdownText } from '../../../shared/markdown-text/markdown-text';
import { ConfirmacaoService } from '../../../shared/confirmacao/confirmacao.service';

import { BotaoCarregandoDirective } from '../../../shared/botao-carregando/botao-carregando.directive';
// dois modos:
// - reaceite: termos mudaram depois do cadastro, o backend responde 403 TERMS_NOT_ACCEPTED e o interceptor traz pra ca
//   (Figma "Ambos · /terms · aceitar nova versão" e "não aceitou")
// - leitura (?leitura=1): aberto por "Privacidade e dados", so mostra o texto (Figma "/terms · leitura pelo menu")
@Component({
  selector: 'app-terms',
  standalone: true,
  imports: [BotaoCarregandoDirective, Skeleton, Header, DeleteAccount, MarkdownText],
  templateUrl: './terms.html',
  styleUrls: ['../../../shared/folha.css', './terms.css'],
})
export class TermsPage implements OnInit {
  private termsService = inject(TermsService);
  private authService = inject(AuthService);
  private popUpService = inject(PopUpService);
  private confirmacao = inject(ConfirmacaoService);
  private router = inject(Router);
  private route = inject(ActivatedRoute);
  private location = inject(Location);

  protected termos = signal<Terms | null>(null);
  protected enviando = signal(false);
  protected leitura = false;
  protected recusou = signal(false);
  protected excluirAberto = signal(false);

  ngOnInit() {
    this.leitura = this.route.snapshot.queryParamMap.get('leitura') === '1';
    this.termsService.getCurrent().subscribe({
      next: (termos) => {
        this.termos.set(termos);
        if (!this.leitura) {
          this.popUpService.show(
            `Para continuar usando o VanRoute, leia e aceite a versão ${termos.version}.`,
            'info',
            'Os termos mudaram',
          );
        }
      },
      error: () => {},
    });
  }

  aceitar() {
    const termos = this.termos();
    if (!termos || this.enviando()) return;
    this.enviando.set(true);
    this.termsService.accept(termos.version).subscribe({
      next: () => {
        this.enviando.set(false);
        this.popUpService.show('Termos aceitos.', 'success', 'Tudo certo');
        this.router.navigate([this.authService.getHomeRoute() ?? '/']);
      },
      error: () => this.enviando.set(false),
    });
  }

  naoAceitar() {
    this.recusou.set(true);
    this.popUpService.show(
      'Seus dados pessoais e arquivos enviados somem. Contratos, parcelas e mensagens ficam no histórico das outras partes, sem o seu nome.',
      'warning',
      'Antes de excluir',
    );
  }

  lerDeNovo() {
    this.recusou.set(false);
  }

  abrirExcluir() {
    this.excluirAberto.set(true);
  }

  fecharExcluir() {
    this.excluirAberto.set(false);
  }

  voltar() {
    if (this.leitura) this.location.back();
    else this.sair();
  }

  async sair() {
    if (!(await this.confirmacao.perguntar({ titulo: 'Sair da conta?', acao: 'Sair da conta' }))) return;
    this.authService.logout();
    this.router.navigate(['/']);
  }
}
