import { BoardingStatus, OccurrenceType, TripStatus } from '../trip/trip.dto';

// GET /api/driver/groups
export interface driverGroup {
  id: string;
  name: string;
  students: { studentId: string; studentName: string; photoKey: string | null }[];
}

// POST /api/driver/groups/{id}/notice
export interface groupNoticeResponse {
  notifiedResponsibles: number;
}

// GET /api/trip/history: uma linha por aluno por viagem
export interface tripHistoryItem {
  tripId: string;
  date: string; // AAAA-MM-DD
  tripStatus: TripStatus;
  startTime: string | null; // HH:mm:ss
  endTime: string | null; // null com FINISHED = fechada pelo sistema
  routeId: string;
  routeName: string;
  driverId: string;
  driverName: string;
  studentId: string;
  studentName: string;
  boardingStatus: BoardingStatus;
  boardingTime: string | null; // AAAA-MM-DDTHH:mm:ss
  unboardingTime: string | null;
  occurrences: number;
}

export interface tripHistoryFilter {
  studentId?: string;
  routeId?: string;
  from?: string;
  to?: string;
  page?: number;
  size?: number;
}

// GET /api/trip/{tripId}/occurrences
export interface tripOccurrence {
  id: string;
  tripId: string;
  type: OccurrenceType;
  description: string | null;
  dateTime: string;
  driverName: string;
}
