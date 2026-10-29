import { Component, OnInit, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { FormsModule } from '@angular/forms';
import { CepBuscaDirective, CepMaskDirective, EnderecoCep, PhoneMaskDirective } from '../../shared/directives';
import { mensagemDoBackend, PopUpService } from '../../shared/pop-up';
import { limparMascara } from '../../shared/utils';
import { SchoolService } from '../../services/school.service';
import { schoolResponse } from '../../services/dto/school/school.dto';

const EMAIL_VALIDO = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

function formularioVazio() {
  return {
    name: '',
    phone: '',
    email: '',
    zipCode: '',
    street: '',
    number: '',
    neighborhood: '',
    city: '',
    state: '',
  };
}

// aba "Adicionar escola" do motorista: escolas que ele atende (usadas no contrato e na rota)
@Component({
  selector: 'app-driver-schools',
  standalone: true,
  imports: [FormsModule, CepMaskDirective, CepBuscaDirective, PhoneMaskDirective],
  templateUrl: './driver-schools.html',
  styleUrl: './driver-schools.css',
})
export class DriverSchools implements OnInit {
  private schoolService = inject(SchoolService);
  private popUpService = inject(PopUpService);

  protected escolas = signal<schoolResponse[]>([]);
  protected carregando = signal(true);
  protected erroCarregar = signal('');
  protected formularioAberto = signal(false);
  protected salvando = signal(false);
  protected erro = signal('');

  protected form = formularioVazio();

  ngOnInit() {
    this.carregar();
  }

  carregar() {
    this.carregando.set(true);
    this.erroCarregar.set('');
    this.schoolService.getMySchools().subscribe({
      next: (escolas) => {
        this.escolas.set(escolas ?? []);
        this.carregando.set(false);
      },
      error: (error) => {
        console.error('Erro ao carregar escolas:', error);
        this.erroCarregar.set('Não foi possível carregar suas escolas.');
        this.carregando.set(false);
      },
    });
  }

  abrirFormulario() {
    this.form = formularioVazio();
    this.erro.set('');
    this.formularioAberto.set(true);
  }

  // CEP achado no ViaCEP: preenche o que veio (CEP de cidade pequena pode vir sem rua/bairro)
  preencherEndereco(endereco: EnderecoCep) {
    if (endereco.rua) this.form.street = endereco.rua;
    if (endereco.bairro) this.form.neighborhood = endereco.bairro;
    if (endereco.cidade) this.form.city = endereco.cidade;
    if (endereco.uf) this.form.state = endereco.uf;
  }

  cancelar() {
    this.formularioAberto.set(false);
    this.erro.set('');
  }

  salvar() {
    if (this.salvando()) return;

    const erro = this.validar();
    if (erro) {
      this.erro.set(erro);
      return;
    }

    this.salvando.set(true);
    this.erro.set('');

    const f = this.form;
    this.schoolService
      .addSchool({
        name: f.name.trim(),
        phone: f.phone,
        email: f.email.trim(),
        addressRequestDTO: {
          street: f.street.trim(),
          zipCode: f.zipCode,
          city: f.city.trim(),
          neighborhood: f.neighborhood.trim(),
          number: f.number ? Number(f.number) : null,
          state: f.state.trim().toUpperCase(),
        },
      })
      .subscribe({
        next: () => {
          this.salvando.set(false);
          this.formularioAberto.set(false);
          this.popUpService.show('Escola adicionada.', 'success', 'Tudo certo');
          this.carregar();
        },
        error: (error) => {
          console.error('Erro ao adicionar escola:', error);
          this.salvando.set(false);
          this.erro.set(this.mensagemErro(error));
        },
      });
  }

  private validar(): string {
    const f = this.form;
    if (!f.name.trim()) return 'Informe o nome da escola.';
    if (f.email.trim() && !EMAIL_VALIDO.test(f.email.trim())) return 'Informe um e-mail válido.';
    if (limparMascara(f.zipCode).length !== 8) return 'Informe um CEP válido.';
    if (!f.street.trim()) return 'Informe a rua.';
    if (f.number && !/^\d{1,6}$/.test(f.number)) return 'O número deve ter só dígitos.';
    if (!f.city.trim()) return 'Informe a cidade.';
    if (f.state.trim() && !/^[a-zA-Z]{2}$/.test(f.state.trim())) return 'Informe a UF com 2 letras.';
    return '';
  }

  private mensagemErro(error: unknown): string {
    const status = error instanceof HttpErrorResponse ? error.status : 0;
    if (status === 409) return 'Você já atende esta escola.';
    if (status === 0) return 'Sem conexão com o servidor. Tente novamente.';
    if (status === 400) return mensagemDoBackend(error) ?? 'Confira o endereço e tente novamente.';
    // o backend busca as coordenadas pelo endereco: endereco nao encontrado tambem cai aqui
    return 'Não foi possível adicionar a escola. Confira o endereço e tente novamente.';
  }

  endereco(escola: schoolResponse): string {
    const a = escola.address;
    if (!a) return '';
    const rua = [a.street, a.number].filter((v) => v !== null && v !== '').join(', ');
    return [rua, a.neighborhood, a.city && a.state ? `${a.city}/${a.state}` : a.city].filter(Boolean).join(' · ');
  }
}
