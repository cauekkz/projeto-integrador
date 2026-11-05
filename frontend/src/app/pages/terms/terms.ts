import { Component, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { Header } from '../../shared/header/header';
import { Button } from '../../shared/button/button';
import { TermsService, Terms } from '../../services/terms.service';
import { AuthService } from '../../services/auth.service';
import { PopUpService } from '../../shared/pop-up';

// termos mudaram depois do cadastro: o backend responde 403 TERMS_NOT_ACCEPTED em quase tudo
// e o interceptor traz pra ca. Aceitar libera o app; sair volta pro inicio
@Component({
  selector: 'app-terms',
  standalone: true,
  imports: [Header, Button],
  templateUrl: './terms.html',
  styleUrl: './terms.css',
})
export class TermsPage implements OnInit {
  termos: Terms | null = null;
  enviando = false;

  constructor(
    private termsService: TermsService,
    private authService: AuthService,
    private popUpService: PopUpService,
    private router: Router,
  ) {}

  ngOnInit() {
    this.termsService.getCurrent().subscribe({
      next: (termos) => (this.termos = termos),
      error: () => {},
    });
  }

  aceitar() {
    if (!this.termos || this.enviando) return;
    this.enviando = true;
    this.termsService.accept(this.termos.version).subscribe({
      next: () => {
        this.enviando = false;
        this.popUpService.show('Termos aceitos.', 'success', 'Tudo certo');
        this.router.navigate([this.authService.getHomeRoute() ?? '/']);
      },
      error: () => (this.enviando = false),
    });
  }

  sair() {
    this.authService.logout();
    this.router.navigate(['/']);
  }
}
