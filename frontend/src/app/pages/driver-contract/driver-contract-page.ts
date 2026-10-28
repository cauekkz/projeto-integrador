import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { ActivatedRoute, Router } from '@angular/router';
import { catchError, firstValueFrom, forkJoin, map, Observable, of, switchMap } from 'rxjs';
import { Button } from '../../shared/button/button';
import { PopUpService } from '../../shared/pop-up';
import { ChatSummary } from '../../models/chat-message';
import { ChatWebSocketService } from '../../services/chat.service';
import { ContractService } from '../../services/contract.service';
import { StorageService } from '../../services/storage.service';
import { SchoolService } from '../../services/school.service';
import { ContractDraftService, EtapaContrato, ResponsavelContrato } from './contract-draft.service';
import { ContractStudentData } from '../../components/driver-contract/contract-student-data/contract-student-data';
import { ContractDetails } from '../../components/driver-contract/contract-details/contract-details';
import { ContractReview } from '../../components/driver-contract/contract-review/contract-review';
import { ContractAttachment } from '../../components/driver-contract/contract-attachment/contract-attachment';

const ETAPAS: { id: EtapaContrato; label: string }[] = [
  { id: 'aluno', label: 'Dados do aluno' },
  { id: 'contrato', label: 'Contrato' },
  { id: 'midia', label: 'Mídia' },
];

// motorista monta a proposta de contrato pro responsavel do chat (docs/FLUXO_CONTRATO.md)
// aluno -> contrato -> midia (revisao + PDF opcional) -> POST /api/chats/{chatId}/contract
@Component({
  selector: 'app-driver-contract-page',
  standalone: true,
  imports: [Button, ContractStudentData, ContractDetails, ContractReview, ContractAttachment],
  templateUrl: './driver-contract-page.html',
  styleUrls: ['../../components/driver-contract/contract-form.css', './driver-contract-page.css'],
})
export class DriverContractPage implements OnInit {
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private chatService = inject(ChatWebSocketService);
  private contractService = inject(ContractService);
  private storageService = inject(StorageService);
  private schoolService = inject(SchoolService);
  private popUpService = inject(PopUpService);
  protected draft = inject(ContractDraftService);

  protected carregando = signal(true);
  protected erroCarregar = signal('');
  protected enviando = signal(false);
  protected erroEnvio = signal('');

  protected indiceEtapa = computed(() => ETAPAS.findIndex((e) => e.id === this.draft.etapa()));
  protected etapaLabel = computed(() => ETAPAS[this.indiceEtapa()].label);
  protected progresso = computed(() => ((this.indiceEtapa() + 1) / ETAPAS.length) * 100);
  protected ultimaEtapa = computed(() => this.indiceEtapa() === ETAPAS.length - 1);

  private chatId = '';

  ngOnInit() {
    this.chatId = this.route.snapshot.paramMap.get('chatId') ?? '';
    if (!this.chatId) {
      this.router.navigate(['/chat']);
      return;
    }

    this.draft.iniciar(this.chatId);
    this.carregarDados();
  }

  // responsavel (do chat), filhos dele e escolas do motorista; o que ja foi escolhido continua se ainda existir
  carregarDados() {
    this.carregando.set(true);
    this.erroCarregar.set('');

    forkJoin({
      responsavel: this.buscarResponsavel(),
      alunos: this.contractService.getChatStudents(this.chatId),
      escolas: this.schoolService.getMySchools(),
    }).subscribe({
      next: ({ responsavel, alunos, escolas }) => {
        if (!responsavel) {
          this.erroCarregar.set('Não encontramos esta conversa.');
          this.carregando.set(false);
          return;
        }

        this.draft.responsavel.set(responsavel);
        this.draft.alunos.set(alunos ?? []);
        const listaEscolas = escolas ?? [];
        this.draft.escolas.set(listaEscolas);
        if (!this.draft.aluno()) this.draft.studentId.set('');
        // com uma escola so, ja deixa selecionada
        if (!this.draft.escola()) this.draft.schoolId.set(listaEscolas.length === 1 ? listaEscolas[0].id : '');
        // se um item escolhido sumiu, volta pra etapa onde ele e escolhido
        if (this.draft.etapa() !== 'aluno' && Object.keys(this.draft.errosAluno()).length) {
          this.draft.etapa.set('aluno');
        }

        this.carregando.set(false);
      },
      error: (error) => {
        this.erroCarregar.set(
          error?.status === 403 || error?.status === 400
            ? 'Você não tem acesso a esta conversa.'
            : 'Não foi possível carregar os dados do contrato.',
        );
        this.carregando.set(false);
        console.error('Erro ao carregar dados do contrato:', error);
      },
    });
  }

  // o responsavelId da proposta e o otherUserId do chat (o front nunca inventa id)
  private buscarResponsavel(): Observable<ResponsavelContrato | null> {
    return this.buscarChat(0).pipe(
      switchMap((chat) => {
        if (!chat) return of(null);

        const responsavel: ResponsavelContrato = {
          id: chat.otherUserId,
          nome: chat.otherUserName,
          fotoUrl: null,
        };
        if (!chat.otherUserPhotoKey) return of(responsavel);

        return this.storageService.getDownloadUrl(chat.otherUserPhotoKey).pipe(
          map((fotoUrl) => ({ ...responsavel, fotoUrl })),
          catchError(() => of(responsavel)),
        );
      }),
    );
  }

  // GET /api/chats e paginado: procura o chat pagina por pagina
  private buscarChat(page: number): Observable<ChatSummary | null> {
    return this.chatService.getChats(page, 50).pipe(
      switchMap((pagina) => {
        const chat = (pagina.content ?? []).find((item) => item.id === this.chatId);
        if (chat) return of(chat);
        if (pagina.last || page + 1 >= pagina.totalPages) return of(null);
        return this.buscarChat(page + 1);
      }),
    );
  }

  avancar() {
    if (this.carregando() || this.erroCarregar() || this.enviando()) return;

    const etapa = this.draft.etapa();
    const erros = etapa === 'aluno' ? this.draft.errosAluno() : this.draft.errosContrato();

    if (etapa !== 'midia' && Object.keys(erros).length) {
      this.draft.mostrarErros.set(true);
      return;
    }

    this.draft.mostrarErros.set(false);
    this.erroEnvio.set('');

    if (etapa === 'aluno') this.draft.etapa.set('contrato');
    else if (etapa === 'contrato') this.draft.etapa.set('midia');
    else this.enviar();
  }

  voltar() {
    if (this.enviando()) return;

    this.draft.mostrarErros.set(false);
    this.erroEnvio.set('');

    const etapa = this.draft.etapa();
    if (etapa === 'midia') {
      this.draft.etapa.set('contrato');
      return;
    }
    if (etapa === 'contrato') {
      this.draft.etapa.set('aluno');
      return;
    }

    // voltar da primeira etapa cancela o fluxo
    this.draft.limpar();
    this.voltarParaChat();
  }

  private async enviar() {
    if (this.enviando()) return;

    // confere tudo de novo antes de mandar (o backend ainda nao valida o body)
    if (Object.keys(this.draft.errosAluno()).length) {
      this.draft.etapa.set('aluno');
      this.draft.mostrarErros.set(true);
      return;
    }
    if (Object.keys(this.draft.errosContrato()).length) {
      this.draft.etapa.set('contrato');
      this.draft.mostrarErros.set(true);
      return;
    }

    const responsavel = this.draft.responsavel()!;
    this.enviando.set(true);
    this.erroEnvio.set('');

    // 1) PDF primeiro: se ele falhar, o contrato nem e criado
    let fileKey: string | null = null;
    const pdf = this.draft.pdf();
    if (pdf) {
      try {
        fileKey = this.draft.fileKeyEnviado(pdf) ?? (await this.chatService.uploadAttachment(pdf));
        this.draft.registrarPdfEnviado(pdf, fileKey);
      } catch (error) {
        console.error('Erro ao enviar o PDF do contrato:', error);
        this.erroEnvio.set('Não foi possível enviar o PDF. Tente novamente ou remova o anexo.');
        this.enviando.set(false);
        return;
      }
    }

    // 2) proposta: o motorista sai do jwt, o responsavel do chat e os ids das listas da API
    try {
      await firstValueFrom(
        this.contractService.createContract(this.chatId, {
          responsibleId: responsavel.id,
          studentId: this.draft.studentId(),
          schoolId: this.draft.schoolId(),
          contract: {
            periodicity: this.draft.periodicity(),
            value: this.draft.valorCentavos() / 100,
            startDate: this.draft.dataInicioIso(),
            endDate: this.draft.dataFimIso(),
          },
          document: { url: fileKey, type: 'TRANSPORT_CONTRACT' },
        }),
      );
    } catch (error) {
      console.error('Erro ao criar o contrato:', error);
      this.erroEnvio.set(this.mensagemErroEnvio(error));
      this.enviando.set(false);
      return;
    }

    this.popUpService.show(`Proposta enviada para ${responsavel.nome}.`, 'success', 'Contrato enviado');
    this.draft.limpar();
    this.enviando.set(false);
    this.voltarParaChat(responsavel);
  }

  private mensagemErroEnvio(error: unknown): string {
    const status = error instanceof HttpErrorResponse ? error.status : 0;
    switch (status) {
      case 0:
        return 'Sem conexão com o servidor. Verifique sua internet e tente novamente.';
      case 400:
        return 'Este responsável não é o responsável principal do dependente, ou você não participa desta conversa.';
      case 401:
        return 'Sua sessão expirou. Entre novamente.';
      case 403:
        return 'Esta escola não está vinculada ao seu perfil de motorista.';
      case 409:
        return 'Já existe um contrato pendente ou ativo com este dependente.';
      default:
        return 'Não foi possível enviar o contrato. Tente novamente.';
    }
  }

  // a proposta aparece como mensagem no chat
  private voltarParaChat(responsavel?: ResponsavelContrato) {
    this.router.navigate(['/chat-details', this.chatId], {
      state: responsavel
        ? { nomeContato: responsavel.nome, fotoContato: responsavel.fotoUrl ?? undefined }
        : undefined,
    });
  }
}
