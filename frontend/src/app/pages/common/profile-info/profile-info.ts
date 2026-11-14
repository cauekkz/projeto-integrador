import { ChangeDetectorRef, Component, OnInit, inject, signal } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { Location } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { AuthService } from '../../../services/auth.service';
import { Header } from '../../../shared/header/header';
import { PhoneMaskDirective } from '../../../shared/directives';
import { PopUpService } from '../../../shared/pop-up';
import { formatarCpf, formatarTelefone, limparMascara } from '../../../shared/utils';
import { Tema, ThemeService } from '../../../services/theme.service';
import { GlisseSegmentado, OpcaoSegmentada } from '../../../shared/glisse-segmentado/glisse-segmentado';
import { Skeleton } from '../../../shared/skeleton/skeleton';

import { BotaoCarregandoDirective } from '../../../shared/botao-carregando/botao-carregando.directive';
// mesma regra do backend: maiuscula, minuscula, numero e caractere especial, 8+ caracteres
const SENHA_FORTE = /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[@$!%*?&]).{8,}$/;

const TIPOS_FOTO = ['image/png', 'image/jpeg', 'image/webp'];
const TAMANHO_MAX_FOTO = 5 * 1024 * 1024;

@Component({
  selector: 'app-profile-info',
  standalone: true,
  imports: [BotaoCarregandoDirective, Skeleton, Header, FormsModule, PhoneMaskDirective, GlisseSegmentado],
  templateUrl: './profile-info.html',
  styleUrl: './profile-info.css',
})
export class ProfileInfo implements OnInit {
  tipoUsuario = '';
  modoEdicao = false;

  // dados do GET /api/users/me ja formatados pra exibir
  usuario = {
    name: '',
    cpf: '',
    email: '',
    phone: '',
    photoUrl: '',
  };

  enteredEmail = '';
  enteredPhone = '';
  enteredCode = '';
  // so pra tela: esqueleto ate os dados do usuario chegarem
  carregando = true;
  enteredPassword = '';
  enteredConfirmPassword = '';
  enteredPasswordCode = '';
  mensagem = '';
  salvando = false;
  // foto escolhida aparece na hora (preview local) enquanto sobe pro bucket
  fotoPreview: string | null = null;
  enviandoFoto = false;

  // "Aparencia": sistema / claro / escuro
  protected readonly tema = inject(ThemeService);
  protected readonly opcoesTema: { valor: Tema; rotulo: string; icone: string }[] = [
    { valor: 'sistema', rotulo: 'Sistema', icone: 'bi-circle-half' },
    { valor: 'claro', rotulo: 'Claro', icone: 'bi-sun' },
    { valor: 'escuro', rotulo: 'Escuro', icone: 'bi-moon-stars' },
  ];
  // mesmo controle de vidro da Glisse do resto do app; se o React nao carregar, cai nos botoes de reserva
  protected readonly opcoesSegmentado: OpcaoSegmentada[] = this.opcoesTema.map((o) => ({ value: o.valor, label: o.rotulo, icone: o.icone }));
  protected readonly glisseFalhou = signal(false);
  // capa mostra o perfil de quem esta logado
  protected ehMotorista = false;

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private location: Location,
    private authService: AuthService,
    private popUpService: PopUpService,
    private cdr: ChangeDetectorRef,
  ) {}

  ngOnInit() {
    this.tipoUsuario = this.route.snapshot.paramMap.get('tipo') || '';
    this.ehMotorista = this.authService.getRolesFromToken().includes('ROLE_DRIVER');
    this.authService.getMe().subscribe({
      next: (me) => {
        this.carregando = false;
        this.usuario = {
          name: me.name ?? '',
          cpf: formatarCpf(me.cpf),
          email: me.email ?? '',
          phone: formatarTelefone(me.phone),
          photoUrl: me.photoUrl ?? '',
        };
        this.enteredEmail = this.usuario.email;
        this.enteredPhone = this.usuario.phone;
        this.cdr.markForCheck();
      },
      error: (err) => {
        this.carregando = false;
        this.cdr.markForCheck();
        console.error(err);
      },
    });
  }

  voltar() {
    this.location.back();
  }

  escolherTema(valor: string) {
    const opcao = this.opcoesTema.find((o) => o.valor === valor);
    if (opcao) this.tema.escolher(opcao.valor);
  }

  // radiogroup: setas trocam a opcao e levam o foco junto (padrao WAI-ARIA)
  teclaTema(evento: KeyboardEvent) {
    const passo = ({ ArrowRight: 1, ArrowDown: 1, ArrowLeft: -1, ArrowUp: -1 } as Record<string, number>)[evento.key];
    const extremo = ({ Home: 0, End: this.opcoesTema.length - 1 } as Record<string, number>)[evento.key];
    if (passo === undefined && extremo === undefined) return;
    evento.preventDefault();
    const atual = this.opcoesTema.findIndex((o) => o.valor === this.tema.tema());
    const total = this.opcoesTema.length;
    const novo = extremo ?? (atual + passo! + total) % total;
    this.tema.escolher(this.opcoesTema[novo].valor);
    const grupo = evento.currentTarget as HTMLElement;
    queueMicrotask(() => grupo.querySelectorAll<HTMLElement>('[role="radio"]')[novo]?.focus());
  }

  async selecionarFoto(event: Event) {
    const input = event.target as HTMLInputElement;
    const arquivo = input.files?.[0];
    // limpa pra conseguir escolher o mesmo arquivo de novo depois de um erro
    input.value = '';
    if (!arquivo || this.enviandoFoto) return;

    if (!TIPOS_FOTO.includes(arquivo.type)) {
      this.popUpService.show('Escolha uma imagem PNG, JPG ou WEBP.', 'warning', 'Formato inválido');
      return;
    }
    if (arquivo.size > TAMANHO_MAX_FOTO) {
      this.popUpService.show('A foto pode ter no máximo 5 MB.', 'warning', 'Imagem muito grande');
      return;
    }

    const preview = URL.createObjectURL(arquivo);
    this.fotoPreview = preview;
    this.enviandoFoto = true;
    try {
      const photoUrl = await this.authService.updateProfilePhoto(arquivo);
      this.usuario = { ...this.usuario, photoUrl };
      this.popUpService.show('Sua foto de perfil foi atualizada.', 'success', 'Foto salva');
    } catch (error) {
      console.error('Erro ao trocar a foto de perfil:', error);
      this.popUpService.show('Não foi possível enviar a foto. Tente novamente.', 'error', 'Erro');
    } finally {
      this.fotoPreview = null;
      URL.revokeObjectURL(preview);
      this.enviandoFoto = false;
      this.cdr.detectChanges();
    }
  }

  editar() { this.modoEdicao = true; }

  cancelar() {
    this.modoEdicao = false;
    this.enteredEmail = this.usuario.email;
    this.enteredPhone = this.usuario.phone;
    this.enteredCode = '';
    this.mensagem = '';
  }

  solicitarCodigo() {
    this.mensagem = '';
    this.authService.sendCodeToUpdateUser().subscribe({
      next: () => {
        this.mensagem = 'Código de verificação enviado para o seu e-mail.';
        this.cdr.markForCheck();
      },
      error: (err) => {
        console.error(err);
        this.mensagem = 'Não foi possível enviar o código de verificação.';
        this.cdr.markForCheck();
      },
    });
  }

  salvar() {
    if (!this.enteredCode.trim()) {
      this.mensagem = 'Solicite e informe o código de verificação antes de salvar.';
      this.cdr.markForCheck();
      return;
    }

    this.salvando = true;
    this.mensagem = '';
    this.authService
      .updateUser({
        email: this.enteredEmail,
        phone: limparMascara(this.enteredPhone),
        code: this.enteredCode,
      })
      .subscribe({
        next: () => {
          this.usuario = {
            ...this.usuario,
            email: this.enteredEmail,
            phone: formatarTelefone(this.enteredPhone),
          };
          this.modoEdicao = false;
          this.enteredCode = '';
          this.salvando = false;
          this.mensagem = 'Dados atualizados com sucesso.';
          this.cdr.markForCheck();
        },
        error: (err) => {
          console.error(err);
          this.salvando = false;
          this.mensagem = 'Não foi possível atualizar os dados.';
          this.cdr.markForCheck();
        },
      });
  }

  // troca de senha usa o mesmo fluxo do "esqueci a senha" (codigo no e-mail da conta)
  solicitarCodigoSenha() {
    this.mensagem = '';
    this.authService.sendForgotPasswordCode(this.usuario.email).subscribe({
      next: () => {
        this.mensagem = 'Código para trocar a senha enviado para o seu e-mail.';
        this.cdr.markForCheck();
      },
      error: (err) => {
        console.error(err);
        this.mensagem = 'Não foi possível enviar o código de verificação.';
        this.cdr.markForCheck();
      },
    });
  }

  alterarSenha() {
    if (!this.enteredPasswordCode.trim() || !this.enteredPassword || !this.enteredConfirmPassword) {
      this.mensagem = 'Informe a nova senha, a confirmação e o código de verificação.';
      this.cdr.markForCheck();
      return;
    }
    if (!SENHA_FORTE.test(this.enteredPassword)) {
      this.mensagem = 'A senha precisa de 8 ou mais caracteres, com letra maiúscula, minúscula, número e símbolo.';
      this.cdr.markForCheck();
      return;
    }
    if (this.enteredPassword !== this.enteredConfirmPassword) {
      this.mensagem = 'As senhas não são iguais.';
      this.cdr.markForCheck();
      return;
    }

    this.salvando = true;
    this.mensagem = '';
    this.authService
      .resetPassword({
        email: this.usuario.email,
        code: this.enteredPasswordCode.trim(),
        password: this.enteredPassword,
        confirmPassword: this.enteredConfirmPassword,
      })
      .subscribe({
        next: () => {
          // o backend derruba as sessoes ao trocar a senha: entra de novo com a senha nova
          this.salvando = false;
          this.authService.logout();
          this.popUpService.show('Senha alterada. Entre novamente com a nova senha.', 'success', 'Tudo certo');
          this.router.navigate(['/']);
        },
        error: (err) => {
          console.error(err);
          this.salvando = false;
          this.mensagem = 'Não foi possível atualizar a senha.';
          this.cdr.markForCheck();
        },
      });
  }
}
