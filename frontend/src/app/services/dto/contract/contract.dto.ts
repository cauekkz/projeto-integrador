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
    // fileKey do PDF no MinIO; sem anexo vai null (a coluna aceita, e o "document" e obrigatorio no body)
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

// enum ContractStatus do backend
export type ContractStatus = 'PENDING' | 'ACTIVE' | 'INACTIVE' | 'CANCELED' | 'SUSPENDED' | 'OVERDUE';

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
  driverId: string;
  driverName: string;
  responsibleId: string;
  responsibleName: string;
  // fileKey do PDF no MinIO (null sem anexo); link via GET /api/storage/download-url
  documentFileKey: string | null;
  documentType: string | null;
}

// GET /api/contract/my-contracts (ContractSummaryResponseDTO); ?status= filtra
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
