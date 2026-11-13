// tipos de /api/trip e do WebSocket /trip/{tripId}. Regras em docs/FLUXO_TRIP.md
import { StopAction, StopLocationType, routeAddress } from '../route/route.dto';

export type TripStatus = 'SCHEDULED' | 'STARTED' | 'IN_PROGRESS' | 'PAUSED' | 'FINISHED' | 'CANCELED' | 'FAILED';
export type BoardingStatus = 'PENDING' | 'BOARDED' | 'DELIVERED' | 'ABSENT';
export type OccurrenceType = 'VEHICLE' | 'STUDENT' | 'TRAFFIC' | 'DISCIPLINARY' | 'FINANCIAL' | 'MEDICAL' | 'OTHER';
// DRIVER = motorista; os outros sao do responsavel (etapa 8b)
export type TripMode = 'DRIVER' | 'FOLLOWING' | 'ON_BOARD' | 'DONE' | 'STATUS';

// viagem em andamento (iniciada ou com alguem ja embarcado)
export function viagemAtiva(status: TripStatus | null | undefined): boolean {
  return status === 'STARTED' || status === 'IN_PROGRESS' || status === 'PAUSED';
}

// TripStudentDTO
export interface tripStudent {
  studentId: string;
  studentName: string;
  action: StopAction | null;
  locationType: StopLocationType | null;
  status: BoardingStatus;
  boardedAt: string | null;
  deliveredAt: string | null;
  // apelido do endereco nesse ponto ("Casa", "Natação"); null na escola
  addressLabel: string | null;
  photoKey: string | null;
}

// TripStopDTO (resposta HTTP)
export interface tripStop {
  routeStopId: string;
  orderIndex: number;
  address: routeAddress;
  students: tripStudent[];
  schoolIds: string[];
  done: boolean;
  schoolNames: string[];
}

// TripResponseDTO: start, today, GET /{tripId}, absent e finish
export interface tripResponse {
  id: string;
  routeId: string;
  routeName: string;
  date: string;
  status: TripStatus;
  startTime: string | null;
  endTime: string | null;
  vehicleId: string | null;
  vehiclePlate: string | null;
  stops: tripStop[];
  path: { distance: number; duration: number; coordinates: number[][] } | null;
}

// TripLiveStopDTO (ponto no WebSocket)
export interface tripLiveStop {
  routeStopId: string;
  orderIndex: number;
  latitude: number | null;
  longitude: number | null;
  students: tripStudent[];
  schoolIds: string[];
  done: boolean;
  pathIndex: number | null;
  etaSeconds: number | null;
  street: string | null;
  number: number | null;
  neighborhood: string | null;
  schoolNames: string[];
}

// TripLiveViewDTO: o "trip-update" do WebSocket. path vem [longitude, latitude]
export interface tripUpdate {
  type: 'trip-update';
  tripId: string;
  routeName: string;
  status: TripStatus;
  following: boolean;
  mode: TripMode;
  latitude: number | null;
  longitude: number | null;
  positionAt: number | null;
  // null com a viagem ativa = todos os alunos foram atendidos
  targetStopId: string | null;
  etaSeconds: number | null;
  path: number[][] | null;
  students: tripStudent[];
  stops: tripLiveStop[] | null;
}

// rotulos do Figma "Registrar ocorrência"
export const TIPOS_DE_OCORRENCIA: { tipo: OccurrenceType; texto: string }[] = [
  { tipo: 'VEHICLE', texto: 'Veículo' },
  { tipo: 'STUDENT', texto: 'Aluno' },
  { tipo: 'TRAFFIC', texto: 'Trânsito' },
  { tipo: 'DISCIPLINARY', texto: 'Disciplinar' },
  { tipo: 'FINANCIAL', texto: 'Financeira' },
  { tipo: 'MEDICAL', texto: 'Médica' },
  { tipo: 'OTHER', texto: 'Outro' },
];

// selo do aluno (Figma "Selo")
export const SELO_DO_ALUNO: Record<BoardingStatus, { texto: string; classe: string }> = {
  PENDING: { texto: 'Aguardando', classe: 'aguardando' },
  BOARDED: { texto: 'Embarcou', classe: 'embarcou' },
  DELIVERED: { texto: 'Entregue', classe: 'entregue' },
  ABSENT: { texto: 'Ausente', classe: 'ausente' },
};

// "Buscar em casa", "Descer na escola", "Deixar na Natação"
export function tarefaDoAluno(aluno: tripStudent): string {
  const embarque = aluno.action === 'BOARDING';
  if (aluno.locationType === 'SCHOOL') return embarque ? 'Buscar na escola' : 'Descer na escola';
  const lugar = aluno.addressLabel && aluno.addressLabel.toLowerCase() !== 'casa' ? `em ${aluno.addressLabel}` : 'em casa';
  return embarque ? `Buscar ${lugar}` : `Deixar ${lugar}`;
}

// "07:32" de "2026-11-05T07:32:10"
export function horaDe(dataHora: string | null | undefined): string {
  const hora = dataHora?.split('T')[1] ?? '';
  return hora.slice(0, 5);
}

// "R. Libertadores, 2026" ou "Fatec Ipiranga" no ponto da escola
export function nomeDoPonto(ponto: tripLiveStop): string {
  if (ponto.schoolNames?.length) return ponto.schoolNames.join(', ');
  if (!ponto.street) return `Ponto ${ponto.orderIndex}`;
  return ponto.number != null ? `${ponto.street}, ${ponto.number}` : ponto.street;
}

// minutos inteiros pro texto "em 7 min" (menos de 1 min vira 1)
export function minutosAte(segundos: number | null | undefined): number | null {
  if (segundos == null) return null;
  return Math.max(1, Math.round(segundos / 60));
}

// GET /api/student/{id}/today-trips: as viagens de hoje do filho, com o motorista e a van dessa viagem
export interface studentTripToday {
  tripId: string;
  routeId: string;
  routeName: string;
  tripStatus: TripStatus;
  boardingStatus: BoardingStatus;
  driverId: string;
  driverName: string;
  driverPhotoKey: string | null;
  driverRating: number | null;
  vehiclePlate: string | null;
  vehicleModel: string | null;
  vehicleColor: string | null;
}

// falta avisada (POST/GET /api/student/{id}/absences)
export interface studentAbsence {
  id: string;
  studentId: string;
  routeId: string;
  routeName: string;
  date: string;
}

// GET /api/student/{id}/routes: so o nome das rotas do filho
export interface studentRoute {
  id: string;
  name: string;
}

// "2026-11-05" no fuso do aparelho (a falta usa a data local, nao a UTC)
export function dataLocal(data = new Date()): string {
  const mes = String(data.getMonth() + 1).padStart(2, '0');
  const dia = String(data.getDate()).padStart(2, '0');
  return `${data.getFullYear()}-${mes}-${dia}`;
}

// "05/11" de "2026-11-05"
export function diaEMes(data: string): string {
  const [, mes, dia] = data.split('-');
  return `${dia}/${mes}`;
}
