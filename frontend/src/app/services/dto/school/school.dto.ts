// GET /api/school/my-schools (SchoolResponseDTO, so o que o front usa)
export interface schoolResponse {
  id: string;
  name: string;
  phone: string | null;
  email: string | null;
  address: {
    street: string;
    zipCode: string;
    city: string;
    neighborhood: string | null;
    number: number | null;
    state: string | null;
  } | null;
}

// POST /api/school/add-school (SchoolRequestDTO); o endereco vai no campo "addressRequestDTO"
// lat/long o backend calcula pelo endereco
export interface schoolRequest {
  name: string;
  phone: string;
  email: string;
  addressRequestDTO: {
    street: string;
    zipCode: string;
    city: string;
    neighborhood: string;
    number: number | null;
    state: string;
  };
}
