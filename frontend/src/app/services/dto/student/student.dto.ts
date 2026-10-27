import { addressRequest } from '../auth/auth.dto';

// resposta do POST /api/student/create-student
export interface studentResponsibleResponse {
  studentResponseDTO: {
    id: string;
    name: string;
    notes: string;
    birthDate: string;
  };
  responsibleResponseDTO: {
    name: string;
    email: string;
  };
}

export interface createStudentRequest {
  name: string;
  notes: string;
  birthDate: string;
  relationType: string;
  address: addressRequest;
}

export interface generateStudentLink {
  id: string;
  relationType: string
}

export interface getMyChildrenRequest {
  relationType?: string;
  isAdmin?: boolean;
  studentName?: string;
  page?: number;
  size?: number;
}
