import { ContractStatus } from '../contract/contract.dto';

// avaliacao do motorista (responsibleName = so o primeiro nome de quem avaliou)
export interface ratingResponse {
  score: number;
  comment: string | null;
  date: string; // AAAA-MM-DDTHH:mm:ss
  responsibleName: string;
}

export interface ratingRequest {
  score: number;
  comment?: string;
}

// GET /api/driver/{driverId}/profile (a van pro pai e so modelo, cor e placa: sem lotacao)
export interface driverProfile {
  driverId: string;
  name: string;
  photoKey: string | null;
  memberSince: string | null; // AAAA-MM-DD
  schools: { schoolId: string; name: string }[];
  vehicles: { model: string; color: string; plate: string }[];
  ratingAverage: number | null;
  ratingCount: number;
  myRating: ratingResponse | null;
  recentRatings: ratingResponse[];
}

export type FinancialStatus = 'UP_TO_DATE' | 'OVERDUE';

// GET /api/responsible/{id}/profile. Pro motorista, so as parcelas e os filhos com ele; no proprio, de todos
export interface responsibleProfile {
  id: string;
  name: string;
  photoKey: string | null;
  phone: string | null;
  financialStatus: FinancialStatus;
  overduePayments: number;
  students: {
    studentId: string;
    studentName: string;
    contractId: string;
    contractStatus: ContractStatus;
    relationType: 'LEGAL' | 'FINANCIAL' | 'WITHDRAWAL';
    admin: boolean;
    photoKey: string | null;
  }[];
}

// "10/01/2025" de "2025-01-10" (ou de uma data com hora)
export function dataBr(data: string | null | undefined): string {
  if (!data) return '';
  const [ano, mes, dia] = data.slice(0, 10).split('-');
  return `${dia}/${mes}/${ano}`;
}

// "4,6"
export function notaBr(nota: number | null | undefined): string {
  return nota == null ? '' : nota.toFixed(1).replace('.', ',');
}
