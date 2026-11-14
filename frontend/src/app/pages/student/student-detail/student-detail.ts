import { Skeleton } from '../../../shared/skeleton/skeleton';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { ActivatedRoute, Router } from '@angular/router';
import { catchError, forkJoin, map, of, switchMap } from 'rxjs';
import { Header } from '../../../shared/header/header';
import { Footer } from '../../../shared/footer/footer';
import { PopUpService } from '../../../shared/pop-up';
import { CepBuscaDirective, CepMaskDirective, EnderecoCep } from '../../../shared/directives';
import { limparMascara } from '../../../shared/utils';
import { StudentPhoto } from '../../../components/student/student-photo/student-photo';
import { StudentService } from '../../../services/student.service';
import { AuthService } from '../../../services/auth.service';
import {
  enderecoEmLinha,
  studentPlace,
  studentResponsibleLink,
} from '../../../services/dto/student/student.dto';
import { dataLocal, diaEMes, studentAbsence, studentRoute } from '../../../services/dto/trip/trip.dto';
import { RELATION_LABEL } from '../../../services/dto/contract/contract.dto';
import { ConfirmacaoService } from '../../../shared/confirmacao/confirmacao.service';

import { BotaoCarregandoDirective } from '../../../shared/botao-carregando/botao-carregando.directive';
// endereco de outro filho que pode ser reaproveitado (addressId)
interface EnderecoDaFamilia {
  addressId: string;
  titulo: string; // "Casa de Marina"
  linha: string;
}

// folhas de confirmacao da tela (Figma 1054:918, 1054:986, 1054:1125, 1054:1193)
type Confirmacao =
  | { tipo: 'tirar-endereco'; lugar: studentPlace }
  | { tipo: 'remover'; pessoa: studentResponsibleLink }
  | { tipo: 'passar-admin'; pessoa: studentResponsibleLink }
  | { tipo: 'sair' };

const RELACOES = ['LEGAL', 'FINANCIAL', 'WITHDRAWAL'] as const;

// Figma "Responsável · /student/:id": foto, enderecos, responsaveis, rotas e faltas do filho
@Component({
  selector: 'app-student-detail',
  standalone: true,
  imports: [BotaoCarregandoDirective, Skeleton, FormsModule, Header, Footer, StudentPhoto, CepMaskDirective, CepBuscaDirective],
  templateUrl: './student-detail.html',
  styleUrls: ['../../../shared/folha.css', '../../../shared/rota.css', './student-detail.css'],
})
export class StudentDetail implements OnInit {
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private studentService = inject(StudentService);
  private authService = inject(AuthService);
  private popUpService = inject(PopUpService);
  private perguntas = inject(ConfirmacaoService);

  protected readonly relacoes = RELACOES;
  protected readonly rotuloRelacao = RELATION_LABEL;
  protected readonly enderecoEmLinha = enderecoEmLinha;
  protected readonly diaEMes = diaEMes;

  protected studentId = '';
  protected meuId = '';

  protected carregando = signal(true);
  protected erroCarregar = signal(false);
  protected nome = signal('');
  protected photoUrl = signal<string | null>(null);
  protected enderecos = signal<studentPlace[]>([]);
  protected responsaveis = signal<studentResponsibleLink[]>([]);
  protected rotas = signal<studentRoute[]>([]);
  protected faltas = signal<studentAbsence[]>([]);

  protected souAdmin = computed(() => this.responsaveis().some((r) => r.admin && r.responsibleId === this.meuId));
  protected primeiroNome = computed(() => this.nome().split(' ')[0] ?? '');

  protected enviando = signal(false);
  protected confirmacao = signal<Confirmacao | null>(null);

  // folha "Avisar falta" (Figma 1039:1491)
  protected avisandoFalta = signal(false);
  protected rotaDaFalta = signal<string | null>(null);
  protected dataDaFalta = '';
  protected desfazendo = signal<string | null>(null);

  // tela cheia "Novo endereço" (Figma 1054:790 / 1054:828)
  protected novoEndereco = signal(false);
  protected daFamilia = signal<EnderecoDaFamilia[]>([]);
  protected familiaEscolhida = signal<string | null>(null);
  protected digitando = signal(false);
  protected apelido = '';
  protected cep = '';
  protected rua = '';
  protected numero = '';
  protected bairro = '';
  protected cidade = '';
  protected estado = '';

  // folha "Convidar" (codigo de 15 min)
  protected convidando = signal(false);
  protected relacaoConvite = signal<string | null>(null);
  protected codigoConvite = signal<string | null>(null);
  protected copiado = signal(false);

  ngOnInit() {
    this.studentId = this.route.snapshot.paramMap.get('studentId') ?? '';
    this.meuId = this.authService.getUserIdFromToken() ?? '';
    this.carregar();
  }

  carregar() {
    this.carregando.set(true);
    this.erroCarregar.set(false);
    forkJoin({
      // o nome e a foto vem do my-children (o back nao tem GET do aluno por id)
      filho: this.studentService.getMyChildren({ size: 100 }).pipe(
        map((pagina) => (pagina.content ?? []).find((f) => f.id === this.studentId) ?? null),
        switchMap((filho) => (filho ? this.studentService.comFotos([filho]).pipe(map((l) => l[0])) : of(null))),
      ),
      enderecos: this.studentService.getAddresses(this.studentId),
      responsaveis: this.studentService.getResponsibles(this.studentId),
      rotas: this.studentService.getStudentRoutes(this.studentId),
      faltas: this.studentService.getAbsences(this.studentId),
    }).subscribe({
      next: (r) => {
        if (!r.filho) {
          this.semAcesso();
          return;
        }
        this.nome.set(r.filho.name);
        this.photoUrl.set(r.filho.photoUrl);
        this.enderecos.set(r.enderecos);
        this.responsaveis.set(this.ordenar(r.responsaveis));
        this.rotas.set(r.rotas);
        this.faltas.set(this.ordenarFaltas(r.faltas));
        this.carregando.set(false);
      },
      error: (erro: HttpErrorResponse) => {
        if (erro?.status === 403) {
          this.semAcesso();
          return;
        }
        this.carregando.set(false);
        this.erroCarregar.set(true);
      },
    });
  }

  // nao e mais responsavel (removido por outro ou saiu em outro aparelho)
  private semAcesso() {
    this.popUpService.show('Você não é mais responsável por este aluno.', 'warning', 'Sem acesso');
    this.router.navigate(['/dependentes']);
  }

  // admin primeiro, depois eu, depois os outros
  private ordenar(lista: studentResponsibleLink[]) {
    const peso = (r: studentResponsibleLink) => (r.admin ? 0 : r.responsibleId === this.meuId ? 1 : 2);
    return [...lista].sort((a, b) => peso(a) - peso(b));
  }

  private ordenarFaltas(lista: studentAbsence[]) {
    return [...lista].sort((a, b) => a.date.localeCompare(b.date) || a.routeName.localeCompare(b.routeName));
  }

  voltar() {
    this.router.navigate(['/dependentes']);
  }

  fotoMudou(foto: { photoKey: string | null; photoUrl: string | null }) {
    this.photoUrl.set(foto.photoUrl);
  }

  souEu(pessoa: studentResponsibleLink) {
    return pessoa.responsibleId === this.meuId;
  }

  private avisarErro(erro: HttpErrorResponse) {
    const mensagem = erro?.error?.message || 'Tente de novo em instantes.';
    this.popUpService.show(mensagem, 'warning', 'Não foi possível concluir');
  }

  // ---------- confirmacoes (tirar endereco, remover, passar admin, sair) ----------

  confirmar(c: Confirmacao) {
    this.confirmacao.set(c);
  }

  fecharConfirmacao() {
    if (!this.enviando()) this.confirmacao.set(null);
  }

  executar() {
    const c = this.confirmacao();
    if (!c || this.enviando()) return;
    this.enviando.set(true);

    const chamada =
      c.tipo === 'tirar-endereco'
        ? this.studentService.removeAddress(this.studentId, c.lugar.id)
        : c.tipo === 'remover'
          ? this.studentService.removeResponsible(this.studentId, c.pessoa.responsibleId)
          : c.tipo === 'passar-admin'
            ? this.studentService.transferAdmin(this.studentId, c.pessoa.responsibleId)
            : this.studentService.removeResponsible(this.studentId, this.meuId);

    chamada.subscribe({
      next: () => {
        this.enviando.set(false);
        this.confirmacao.set(null);
        if (c.tipo === 'sair') {
          this.popUpService.show(`Você deixou de ser responsável de ${this.primeiroNome()}.`, 'success', 'Tudo certo');
          this.router.navigate(['/dependentes']);
          return;
        }
        if (c.tipo === 'tirar-endereco') {
          this.enderecos.update((l) => l.filter((e) => e.id !== c.lugar.id));
          this.popUpService.show('O motorista foi avisado.', 'success', 'Endereço tirado');
        } else if (c.tipo === 'remover') {
          this.responsaveis.update((l) => l.filter((r) => r.responsibleId !== c.pessoa.responsibleId));
          this.popUpService.show(`${c.pessoa.name} não é mais responsável de ${this.primeiroNome()}.`, 'success', 'Responsável removido');
        } else {
          this.responsaveis.update((l) =>
            this.ordenar(l.map((r) => ({ ...r, admin: r.responsibleId === c.pessoa.responsibleId }))),
          );
          this.popUpService.show(`${c.pessoa.name} agora é o admin.`, 'success', 'Admin passado');
        }
      },
      // a folha continua aberta por baixo do aviso (Figma 1054:1057 / 1054:1259 / 1054:4836)
      error: (erro: HttpErrorResponse) => {
        this.enviando.set(false);
        this.avisarErro(erro);
      },
    });
  }

  // ---------- faltas ----------

  abrirFalta(rotaId: string | null = null) {
    this.rotaDaFalta.set(rotaId ?? this.rotas()[0]?.id ?? null);
    this.dataDaFalta = dataLocal();
    this.avisandoFalta.set(true);
  }

  fecharFalta() {
    if (!this.enviando()) this.avisandoFalta.set(false);
  }

  hoje() {
    return dataLocal();
  }

  avisarFalta() {
    const rotaId = this.rotaDaFalta();
    if (this.enviando()) return;
    if (!rotaId || !this.dataDaFalta) {
      this.popUpService.show('Escolha a rota e a data.', 'warning', 'Campos Incompletos');
      return;
    }
    this.enviando.set(true);
    this.studentService.createAbsence(this.studentId, rotaId, this.dataDaFalta).subscribe({
      next: (falta) => {
        this.enviando.set(false);
        this.avisandoFalta.set(false);
        this.faltas.update((l) => this.ordenarFaltas([...l, falta]));
        const quando = falta.date === dataLocal() ? 'hoje' : `em ${diaEMes(falta.date)}`;
        this.popUpService.show(`O motorista foi avisado que ${this.primeiroNome()} não vai ${quando}.`, 'success', 'Falta avisada');
      },
      error: (erro: HttpErrorResponse) => {
        this.enviando.set(false);
        this.avisarErro(erro);
      },
    });
  }

  async eleVaiSim(falta: studentAbsence) {
    if (this.desfazendo()) return;
    const sim = await this.perguntas.perguntar({
      titulo: `Desfazer a falta de ${this.primeiroNome()}?`,
      texto: 'A falta avisada é cancelada.',
      acao: `${this.primeiroNome()} vai sim`,
      tom: 'azul',
    });
    if (!sim || this.desfazendo()) return;
    this.desfazendo.set(falta.id);
    this.studentService.cancelAbsence(this.studentId, falta.id).subscribe({
      next: () => {
        this.desfazendo.set(null);
        this.faltas.update((l) => l.filter((f) => f.id !== falta.id));
      },
      error: (erro: HttpErrorResponse) => {
        this.desfazendo.set(null);
        this.avisarErro(erro);
      },
    });
  }

  abrirHistorico() {
    this.router.navigate(['/trip-history'], { queryParams: { studentId: this.studentId } });
  }

  abrirConversas() {
    this.router.navigate(['/chat'], { queryParams: { from: 'responsible-home' } });
  }

  // ---------- novo endereco ----------

  abrirNovoEndereco() {
    this.apelido = '';
    this.limparFormulario();
    this.familiaEscolhida.set(null);
    this.daFamilia.set([]);
    this.digitando.set(false);
    this.novoEndereco.set(true);
    this.carregarDaFamilia();
  }

  // enderecos dos outros filhos que este ainda nao tem (o back aceita addressId de um filho seu)
  private carregarDaFamilia() {
    const jaTem = new Set(this.enderecos().map((e) => e.address.id));
    this.studentService
      .getMyChildren({ size: 100 })
      .pipe(
        map((pagina) => (pagina.content ?? []).filter((f) => f.id !== this.studentId)),
        switchMap((irmaos) =>
          irmaos.length
            ? forkJoin(
                irmaos.map((irmao) =>
                  this.studentService.getAddresses(irmao.id).pipe(
                    map((lista) => lista.map((lugar) => ({ irmao: irmao.name, lugar }))),
                    catchError(() => of([])),
                  ),
                ),
              )
            : of([]),
        ),
        catchError(() => of([])),
      )
      .subscribe((porIrmao) => {
        const vistos = new Set<string>();
        const lista: EnderecoDaFamilia[] = [];
        for (const { irmao, lugar } of porIrmao.flat()) {
          const id = lugar.address.id;
          if (jaTem.has(id) || vistos.has(id)) continue;
          vistos.add(id);
          const primeiro = irmao.split(' ')[0];
          lista.push({
            addressId: id,
            titulo: lugar.label ? `${lugar.label} de ${primeiro}` : `Endereço de ${primeiro}`,
            linha: lugar.address.number != null ? `${lugar.address.street}, ${lugar.address.number}` : lugar.address.street,
          });
        }
        this.daFamilia.set(lista);
        this.familiaEscolhida.set(lista[0]?.addressId ?? null);
        if (!lista.length) this.digitando.set(true);
      });
  }

  fecharNovoEndereco() {
    if (!this.enviando()) this.novoEndereco.set(false);
  }

  digitarOutro() {
    this.familiaEscolhida.set(null);
    this.digitando.set(true);
  }

  escolherDaFamilia(id: string) {
    this.familiaEscolhida.set(id);
    this.digitando.set(false);
  }

  preencherEndereco(e: EnderecoCep) {
    if (e.rua) this.rua = e.rua;
    if (e.bairro) this.bairro = e.bairro;
    if (e.cidade) this.cidade = e.cidade;
    if (e.uf) this.estado = e.uf;
  }

  private limparFormulario() {
    this.cep = '';
    this.rua = '';
    this.numero = '';
    this.bairro = '';
    this.cidade = '';
    this.estado = '';
  }

  adicionarEndereco() {
    if (this.enviando()) return;
    const label = this.apelido.trim();
    const addressId = this.digitando() ? null : this.familiaEscolhida();

    if (!label) {
      this.popUpService.show('Dê um apelido ao endereço (ex.: Casa da avó).', 'warning', 'Campos Incompletos');
      return;
    }
    if (!addressId && (!this.cep.trim() || !this.rua.trim() || !this.cidade.trim())) {
      this.popUpService.show('Preencha CEP, rua e cidade.', 'warning', 'Campos Incompletos');
      return;
    }

    const numero = Number(this.numero);
    const body = addressId
      ? { label, addressId }
      : {
          label,
          address: {
            zipCode: limparMascara(this.cep),
            street: this.rua.trim(),
            number: this.numero.trim() && Number.isInteger(numero) ? numero : null,
            neighborhood: this.bairro.trim(),
            city: this.cidade.trim(),
            state: this.estado.trim(),
          },
        };

    this.enviando.set(true);
    this.studentService.addAddress(this.studentId, body).subscribe({
      next: (lugar) => {
        this.enviando.set(false);
        this.novoEndereco.set(false);
        this.enderecos.update((l) => [...l, lugar]);
        this.popUpService.show(`${lugar.label ?? 'Endereço'} foi adicionado.`, 'success', 'Endereço adicionado');
      },
      // Figma 1054:880: o aviso aparece e a tela continua aberta
      error: (erro: HttpErrorResponse) => {
        this.enviando.set(false);
        this.avisarErro(erro);
      },
    });
  }

  // ---------- convidar responsavel ----------

  abrirConvite() {
    this.relacaoConvite.set(null);
    this.codigoConvite.set(null);
    this.copiado.set(false);
    this.convidando.set(true);
  }

  fecharConvite() {
    if (!this.enviando()) this.convidando.set(false);
  }

  gerarCodigo() {
    const relationType = this.relacaoConvite();
    if (this.enviando()) return;
    if (!relationType) {
      this.popUpService.show('Escolha o tipo de responsável.', 'warning', 'Campos Incompletos');
      return;
    }
    this.enviando.set(true);
    this.studentService.generateStudentLink({ id: this.studentId, relationType }).subscribe({
      next: (codigo) => {
        this.enviando.set(false);
        this.codigoConvite.set(codigo);
      },
      // o popup global ja mostra o erro
      error: () => this.enviando.set(false),
    });
  }

  copiarCodigo() {
    const codigo = this.codigoConvite();
    if (!codigo) return;
    navigator.clipboard?.writeText(codigo).then(() => {
      this.copiado.set(true);
      setTimeout(() => this.copiado.set(false), 2000);
    });
  }
}
