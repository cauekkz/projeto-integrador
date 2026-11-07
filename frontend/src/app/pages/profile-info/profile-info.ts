import { ChangeDetectorRef, Component, OnInit, inject, signal } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { Location } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { AuthService } from '../../services/auth.service';
import { Header } from '../../shared/header/header';
import { PhoneMaskDirective, PasswordToggleDirective } from '../../shared/directives';
import { PopUpService } from '../../shared/pop-up';
import { formatarCpf, formatarTelefone, limparMascara } from '../../shared/utils';
import { ProfileService } from '../../services/profile.service';
import { responsibleProfile } from '../../services/dto/profile/profile.dto';
import { ResponsibleSituation } from '../../components/responsible-situation/responsible-situation';

// mesma regra do backend: maiuscula, minuscula, numero e caractere especial, 8+ caracteres
const SENHA_FORTE = /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[@$!%*?&]).{8,}$/;

const TIPOS_FOTO = ['image/png', 'image/jpeg', 'image/webp'];
const TAMANHO_MAX_FOTO = 5 * 1024 * 1024;

@Component({
  selector: 'app-profile-info',
  standalone: true,
  imports: [Header, FormsModule, PhoneMaskDirective, PasswordToggleDirective, ResponsibleSituation],
  templateUrl: './profile-info.html',
  styleUrl: './profile-info.css',
})
export class ProfileInfo implements OnInit {
  tipoUsuario = '';
  // responsavel: situacao financeira geral e contratos dos filhos (Figma 1057:1016)
  situacao = signal<responsibleProfile | null>(null);
  private profileService = inject(ProfileService);
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
  enteredPassword = '';
  enteredConfirmPassword = '';
  enteredPasswordCode = '';
  mensagem = '';
  salvando = false;
  // foto escolhida aparece na hora (preview local) enquanto sobe pro bucket
  fotoPreview: string | null = null;
  enviandoFoto = false;

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
    this.authService.getMe().subscribe({
      next: (me) => {
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
        if (this.tipoUsuario === 'responsavel' && me.id) this.carregarSituacao(me.id);
      },
      error: (err) => console.error(err),
    });
  }

  private carregarSituacao(meuId: string) {
    this.profileService.getResponsibleProfile(meuId).subscribe({
      next: (perfil) => {
        this.situacao.set(perfil);
        if (perfil.financialStatus === 'OVERDUE') {
          const n = perfil.overduePayments;
          this.popUpService.show('Conta todos os seus contratos, com qualquer motorista.', 'error', n === 1 ? '1 parcela atrasada' : `${n} parcelas atrasadas`);
        }
      },
      // sem a situacao a tela continua so com os dados
      error: () => {},
    });
  }

  voltar() {
    this.location.back();
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
          this.popUpService.show('Senha alterada! Entre novamente com a nova senha.', 'success', 'Tudo certo');
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
