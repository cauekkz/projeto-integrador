import { Injectable, computed, signal } from '@angular/core';
import { dataParaIso } from '../../shared/utils';
import {
  chatStudentResponse,
  ContractPeriodicity,
  PERIODICITY_OPTIONS,
} from '../../services/dto/contract/contract.dto';
import { schoolResponse } from '../../services/dto/school/school.dto';

export type EtapaContrato = 'aluno' | 'contrato' | 'midia';

export interface ResponsavelContrato {
  id: string;
  nome: string;
  fotoUrl: string | null;
}

// NUMERIC(10,2) da tabela contracts
export const VALOR_MAXIMO_CENTAVOS = 9_999_999_999;
// o PDF sobe direto pro MinIO (presigned), o backend nao limita: segura aqui
export const PDF_TAMANHO_MAXIMO = 10 * 1024 * 1024;

// rascunho da proposta: fica na memoria enquanto o motorista navega (vale por chat)
// e some quando a proposta e enviada ou o fluxo e cancelado
@Injectable({
  providedIn: 'root',
})
export class ContractDraftService {
  readonly chatId = signal('');
  readonly etapa = signal<EtapaContrato>('aluno');

  // dados que vem da API
  readonly responsavel = signal<ResponsavelContrato | null>(null);
  readonly alunos = signal<chatStudentResponse[]>([]);
  readonly escolas = signal<schoolResponse[]>([]);

  // o que o motorista preenche
  readonly studentId = signal('');
  readonly schoolId = signal('');
  readonly periodicity = signal<ContractPeriodicity>('MONTHLY');
  readonly valorCentavos = signal(0);
  readonly dataInicio = signal(''); // DD/MM/AAAA
  readonly dataFim = signal(''); // DD/MM/AAAA
  readonly pdf = signal<File | null>(null);

  // fileKey do PDF ja enviado: se o POST do contrato falhar, a nova tentativa nao sobe o arquivo de novo
  private pdfEnviado: { file: File; fileKey: string } | null = null;

  // so mostra os erros depois que o motorista tenta avancar
  readonly mostrarErros = signal(false);

  readonly aluno = computed(() => this.alunos().find((a) => a.id === this.studentId()) ?? null);
  readonly escola = computed(() => this.escolas().find((e) => e.id === this.schoolId()) ?? null);
  readonly periodicidadeLabel = computed(
    () => PERIODICITY_OPTIONS.find((p) => p.value === this.periodicity())?.label ?? '',
  );
  readonly valorFormatado = computed(() => formatarReais(this.valorCentavos()));

  readonly errosAluno = computed(() => {
    const erros: { aluno?: string; escola?: string } = {};
    if (!this.aluno()) erros.aluno = 'Selecione o dependente.';
    if (!this.escola()) erros.escola = 'Selecione a escola.';
    return erros;
  });

  readonly errosContrato = computed(() => {
    const erros: { periodicidade?: string; valor?: string; dataInicio?: string; dataFim?: string } = {};

    if (!PERIODICITY_OPTIONS.some((p) => p.value === this.periodicity())) {
      erros.periodicidade = 'Selecione a periodicidade.';
    }

    const centavos = this.valorCentavos();
    if (!Number.isInteger(centavos) || centavos <= 0) {
      erros.valor = 'Informe um valor maior que zero.';
    } else if (centavos > VALOR_MAXIMO_CENTAVOS) {
      erros.valor = 'Valor acima do permitido.';
    }

    const inicio = dataValida(this.dataInicio());
    const fim = dataValida(this.dataFim());
    if (!inicio) erros.dataInicio = 'Informe uma data válida (DD/MM/AAAA).';
    if (!fim) erros.dataFim = 'Informe uma data válida (DD/MM/AAAA).';
    // com o inicio depois do fim o backend aceita e o aceite gera 0 parcelas
    if (inicio && fim && fim < inicio) {
      erros.dataFim = 'A data de término não pode ser anterior à de início.';
    }

    return erros;
  });

  // entra no fluxo: se for outro chat, comeca do zero
  iniciar(chatId: string) {
    if (this.chatId() !== chatId) {
      this.limpar();
      this.chatId.set(chatId);
    }
  }

  limpar() {
    this.chatId.set('');
    this.etapa.set('aluno');
    this.responsavel.set(null);
    this.alunos.set([]);
    this.escolas.set([]);
    this.studentId.set('');
    this.schoolId.set('');
    this.periodicity.set('MONTHLY');
    this.valorCentavos.set(0);
    this.dataInicio.set('');
    this.dataFim.set('');
    this.pdf.set(null);
    this.pdfEnviado = null;
    this.mostrarErros.set(false);
  }

  fileKeyEnviado(file: File): string | null {
    return this.pdfEnviado?.file === file ? this.pdfEnviado.fileKey : null;
  }

  registrarPdfEnviado(file: File, fileKey: string) {
    this.pdfEnviado = { file, fileKey };
  }

  dataInicioIso() {
    return dataParaIso(this.dataInicio());
  }

  dataFimIso() {
    return dataParaIso(this.dataFim());
  }
}

// aluno nao tem foto na API: o avatar mostra as iniciais
export function iniciais(nome: string | null | undefined): string {
  const partes = (nome ?? '').trim().split(/\s+/).filter(Boolean);
  if (!partes.length) return '?';
  const primeira = partes[0][0];
  const ultima = partes.length > 1 ? partes[partes.length - 1][0] : '';
  return (primeira + ultima).toUpperCase();
}

export function formatarReais(centavos: number): string {
  return (centavos / 100).toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' });
}

// DD/MM/AAAA -> AAAA-MM-DD, so se a data existir de verdade (31/02 nao passa)
function dataValida(valor: string): string | null {
  if (!/^\d{2}\/\d{2}\/\d{4}$/.test(valor)) return null;

  const iso = dataParaIso(valor);
  const [ano, mes, dia] = iso.split('-').map(Number);
  const data = new Date(ano, mes - 1, dia);
  const existe = data.getFullYear() === ano && data.getMonth() === mes - 1 && data.getDate() === dia;

  return existe && ano >= 2000 ? iso : null;
}
