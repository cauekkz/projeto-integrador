import { Component, inject } from '@angular/core';
import { Location } from '@angular/common';
import { Header } from '../../shared/header/header';
import { DriverSchools } from '../../components/driver-schools/driver-schools';

// aba "Adicionar escola" do profile select do motorista
@Component({
  selector: 'app-driver-schools-page',
  standalone: true,
  imports: [Header, DriverSchools],
  template: `
    <div class="pagina">
      <app-header titulo="Escolas" (voltarEvent)="voltar()" />
      <main class="conteudo">
        <app-driver-schools />
      </main>
    </div>
  `,
  styles: `
    :host {
      display: block;
      background: #ffffff;
    }

    .pagina {
      min-height: 100dvh;
      display: flex;
      flex-direction: column;
    }

    .conteudo {
      width: 100%;
      max-width: 480px;
      margin: 0 auto;
      padding: 8px 16px 32px;
    }
  `,
})
export class DriverSchoolsPage {
  private location = inject(Location);

  voltar() {
    this.location.back();
  }
}
