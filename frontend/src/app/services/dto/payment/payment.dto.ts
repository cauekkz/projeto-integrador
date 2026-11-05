import { PageRequest } from '../page.dto';
import { PixKeyType } from '../driver/pix.dto';

// enum PaymentStatus do backend (OVERDUE = pendente com vencimento antes de hoje)
export type PaymentStatus = 'PENDING' | 'OVERDUE' | 'PAID' | 'CANCELED';
// enum PaymentMethod do backend
export type PaymentMethod = 'PIX' | 'CASH' | 'BOLETO' | 'CREDIT_CARD';

export const METODOS: { value: PaymentMethod; label: string }[] = [
  { value: 'PIX', label: 'Pix' },
  { value: 'CASH', label: 'Dinheiro' },
  { value: 'BOLETO', label: 'Boleto' },
  { value: 'CREDIT_CARD', label: 'Cartão' },
];

// PaymentResponseDTO
export interface paymentResponse {
  id: string;
  contractId: string;
  studentId: string;
  studentName: string;
  driverId: string;
  driverName: string;
  responsibleId: string;
  responsibleName: string;
  value: number;
  dueDate: string; // AAAA-MM-DD
  status: PaymentStatus;
  paymentMethod: PaymentMethod | null;
  paidAt: string | null; // AAAA-MM-DDTHH:mm:ss
  // chave do motorista pro responsavel copiar (null se ele nao cadastrou)
  driverPixKeyType: PixKeyType | null;
  driverPixKey: string | null;
}

// filtros do GET /api/payments (todos opcionais) + pagina (padrao 20)
export interface paymentsRequest extends PageRequest {
  status?: PaymentStatus;
  studentId?: string;
  driverId?: string;
  responsibleId?: string;
  contractId?: string;
  from?: string; // AAAA-MM-DD
  to?: string; // AAAA-MM-DD
}
