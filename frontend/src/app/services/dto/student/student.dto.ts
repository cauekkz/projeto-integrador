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

// endereco gravado no back (AddressResponseDTO)
export interface addressResponse {
  id: string;
  street: string;
  zipCode: string;
  city: string;
  neighborhood: string | null;
  number: number | null;
  state: string | null;
  latitude: number | null;
  longitude: number | null;
}

// GET /api/student/{id}/addresses: id = studentAddressId (o do DELETE), apelido e o endereco
export interface studentPlace {
  id: string;
  label: string | null;
  address: addressResponse;
}

// POST /api/student/{id}/addresses: endereco novo (address) OU um da familia (addressId)
export interface studentAddressRequest {
  label: string;
  address?: addressRequest;
  addressId?: string;
}

// GET /api/student/{id}/responsibles (responsibleId = id do usuario)
export interface studentResponsibleLink {
  responsibleId: string;
  name: string;
  relationType: 'LEGAL' | 'FINANCIAL' | 'WITHDRAWAL';
  admin: boolean;
}

// "R. Libertadores, 2026 · Ipiranga, São Paulo"
export function enderecoEmLinha(a: addressResponse): string {
  const rua = a.number != null ? `${a.street}, ${a.number}` : a.street;
  const lugar = [a.neighborhood, a.city].filter(Boolean).join(', ');
  return lugar ? `${rua} · ${lugar}` : rua;
}
