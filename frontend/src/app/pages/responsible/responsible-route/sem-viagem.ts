import { Component } from '@angular/core';
import { Footer } from '../../../shared/footer/footer';

// aba do meio do responsavel quando nenhum filho esta em viagem agora
@Component({
  selector: 'app-sem-viagem',
  standalone: true,
  imports: [Footer],
  template: `
    <main class="vazio">
      <i class="bi bi-truck" aria-hidden="true"></i>
      <h1>Não há uma rota para acompanhar</h1>
      <p>Quando a van sair com um dos seus filhos, o acompanhamento aparece aqui.</p>
    </main>
    <app-footer tipoUsuario="responsavel" />
  `,
  styles: `
    :host {
      display: block;
      min-height: 100dvh;
      background: #ffffff;
    }

    .vazio {
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      gap: 8px;
      min-height: 100dvh;
      max-width: 400px;
      margin: 0 auto;
      padding: 24px 24px 120px;
      text-align: center;
    }

    .vazio i {
      font-size: 40px;
      color: #94a3b8;
    }

    .vazio h1 {
      font-size: 20px;
      font-weight: 600;
      color: #0f172a;
    }

    .vazio p {
      font-size: 14px;
      color: #62748e;
    }
  `,
})
export class SemViagem {}
