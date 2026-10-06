export interface studentResponsibleResponse {
  id: string;
  relationType: string;
  student: {
    id: string;
    name: string;
    notes: string;
    birthDate: string;
  };
  admin: boolean;
}

export interface createStudentRequest {
  name: string;
  notes: string;
  birthDate: string;
  relationType: string;
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
