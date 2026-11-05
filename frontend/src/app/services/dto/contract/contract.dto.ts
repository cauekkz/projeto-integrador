// enum ContractPeriodicity do backend
export type ContractPeriodicity = 'WEEKLY' | 'MONTHLY' | 'QUARTERLY' | 'SEMI_ANNUALLY' | 'ANNUALLY';

export const PERIODICITY_OPTIONS: { value: ContractPeriodicity; label: string }[] = [
  { value: 'WEEKLY', label: 'Semanal' },
  { value: 'MONTHLY', label: 'Mensal' },
  { value: 'QUARTERLY', label: 'Trimestral' },
  { value: 'SEMI_ANNUALLY', label: 'Semestral' },
  { value: 'ANNUALLY', label: 'Anual' },
];

// GET /api/chats/{chatId}/students (ChildSummaryResponseDTO)
export interface chatStudentResponse {
  id: string;
  name: string;
  // foto do aluno (o motorista do chat pode baixar); null = iniciais
  photoKey: string | null;
}


// POST /api/chats/{chatId}/contract (ContractProposalRequestDTO)
export interface contractProposalRequest {
  responsibleId: string;
  studentId: string;
  schoolId: string;
  contract: {
    periodicity: ContractPeriodicity;
    value: number;
    startDate: string; // AAAA-MM-DD
    endDate: string; // AAAA-MM-DD
  };
  document: {
    // fileKey do PDF no MinIO; obrigatorio (o backend recusa proposta sem o PDF)
    url: string | null;
    type: 'TRANSPORT_CONTRACT';
  };
}

// resposta 201 do POST acima (contract tem o formato do GET /api/contract/{id})
export interface contractProposalResponse {
  userDriverContractId: string;
  contract: {
    contractId: string;
    status: string;
  };
}

// enum ContractStatus do backend (SCHEDULED = aceito, esperando a data de inicio)
export type ContractStatus = 'PENDING' | 'SCHEDULED' | 'ACTIVE' | 'INACTIVE' | 'CANCELED';

// enum CancellationStatus do backend
export type CancellationStatus = 'PENDING' | 'ACCEPTED' | 'COMPLETED' | 'WITHDRAWN' | 'REJECTED';

// CancellationResponseDTO: pedido de cancelamento (com aviso previo) ou cancelamento unilateral
export interface cancellationResponse {
  id: string;
  status: CancellationStatus;
  unilateral: boolean;
  reason: string | null;
  requestedById: string;
  requestedByName: string;
  requestedAt: string; // AAAA-MM-DDTHH:mm:ss
  effectiveDate: string | null; // AAAA-MM-DD: dia em que o contrato deixa de valer
  decidedById: string | null;
  decidedByName: string | null;
  decidedAt: string | null;
}

export type DocumentStatus = 'PENDING' | 'SIGNED' | 'REJECTED';

export interface contractSignature {
  userId: string;
  name: string;
  signedAt: string;
  valid: boolean;
}

export interface contractStudentResponsible {
  responsibleId: string;
  name: string;
  relationType: string;
  admin: boolean;
}

// AddressResponseDTO da escola
export interface contractSchoolAddress {
  street: string | null;
  number: number | null;
  neighborhood: string | null;
  city: string | null;
  state: string | null;
  zipCode: string | null;
}

// GET /api/contract/{contractId} (ContractDetailsResponseDTO); so as duas partes do contrato conseguem ver
export interface contractDetailsResponse {
  contractId: string;
  userDriverContractId: string;
  status: ContractStatus;
  periodicity: ContractPeriodicity;
  value: number;
  startDate: string; // AAAA-MM-DD
  endDate: string; // AAAA-MM-DD
  studentId: string;
  studentName: string;
  schoolId: string;
  schoolName: string;
  schoolAddress: contractSchoolAddress | null;
  driverId: string;
  driverName: string;
  responsibleId: string;
  responsibleName: string;
  // fileKey do PDF no MinIO (null sem anexo); link via GET /api/storage/download-url
  documentFileKey: string | null;
  documentType: string | null;
  documentStatus: DocumentStatus | null;
  signatures: contractSignature[];
  cancellations: cancellationResponse[];
  overduePayments: number;
  studentResponsibles: contractStudentResponsible[];
}

// GET /api/contract/my-contracts (pagina de ContractSummaryResponseDTO)
export interface contractSummaryResponse {
  contractId: string;
  userDriverContractId: string;
  status: ContractStatus;
  periodicity: ContractPeriodicity;
  value: number;
  startDate: string; // AAAA-MM-DD
  endDate: string; // AAAA-MM-DD
  studentId: string;
  studentName: string;
  schoolId: string;
  schoolName: string;
  driverId: string;
  driverName: string;
  responsibleId: string;
  responsibleName: string;
  // pedido de cancelamento em andamento (o contrato vale ate effectiveDate)
  pendingCancellation: cancellationResponse | null;
  // parcelas atrasadas desse contrato
  overduePayments: number;
}

// filtros do my-contracts (MyContractsFilterRequestDTO) + pagina (padrao 20)
export interface myContractsRequest {
  status?: ContractStatus;
  studentId?: string;
  driverId?: string;
  page?: number;
  size?: number;
}

export function periodicidadeLabel(valor: ContractPeriodicity | null | undefined): string {
  return PERIODICITY_OPTIONS.find((p) => p.value === valor)?.label ?? '';
}

export function valorEmReais(valor: number | null | undefined): string {
  return (valor ?? 0).toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' });
}

// AAAA-MM-DD -> DD/MM/AAAA
export function isoParaData(iso: string | null | undefined): string {
  if (!iso) return '';
  const [ano, mes, dia] = iso.split('-');
  return `${dia}/${mes}/${ano}`;
}

// rotulo do vinculo responsavel-aluno (RelationType)
export const RELATION_LABEL: Record<string, string> = {
  LEGAL: 'Legal',
  FINANCIAL: 'Financeiro',
  WITHDRAWAL: 'Retirada',
};

// AAAA-MM-DD ou AAAA-MM-DDTHH:mm:ss -> DD/MM
export function diaMes(iso: string | null | undefined): string {
  if (!iso) return '';
  const [, mes, dia] = iso.slice(0, 10).split('-');
  return `${dia}/${mes}`;
}

// AAAA-MM-DD + 1 dia -> DD/MM/AAAA (inicio da renovacao)
export function diaSeguinte(iso: string): string {
  const [ano, mes, dia] = iso.split('-').map(Number);
  const data = new Date(Date.UTC(ano, mes - 1, dia + 1));
  const dd = String(data.getUTCDate()).padStart(2, '0');
  const mm = String(data.getUTCMonth() + 1).padStart(2, '0');
  return `${dd}/${mm}/${data.getUTCFullYear()}`;
}
