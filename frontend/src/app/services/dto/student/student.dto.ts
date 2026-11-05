import { PageRequest } from '../page.dto';

import { addressRequest } from '../auth/auth.dto';

// resposta do POST /api/student/create-student
export interface studentResponsibleResponse {
  studentResponseDTO: {
    id: string;
    name: string;
    notes: string;
    birthDate: string;
    photoKey: string | null;
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

// item do GET /api/student/my-children (o aluno, com a chave da foto; sem foto = null)
export interface studentChild {
  id: string;
  name: string;
  notes: string | null;
  birthDate: string; // AAAA-MM-DD
  photoKey: string | null;
}

export interface getMyChildrenRequest extends PageRequest {
  relationType?: string;
  isAdmin?: boolean;
  studentName?: string;
}
