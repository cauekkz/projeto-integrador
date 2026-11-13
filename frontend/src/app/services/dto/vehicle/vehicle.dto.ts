// enum VehicleStatus do backend
export type VehicleStatus = 'ACTIVE' | 'INACTIVE' | 'UNDER_MAINTENANCE';

// VehicleResponseDTO: tudo que veio do CRLV (textos em maiusculas: formatar na tela)
export interface vehicleResponse {
  id: string;
  plate: string;
  renavam: string | null;
  chassis: string | null;
  model: string | null;
  year: number | null;
  manufactureYear: number | null;
  color: string | null;
  fuel: string | null;
  category: string | null;
  capacity: number | null;
  licensingYear: number | null;
  status: VehicleStatus;
  linkedSince: string | null; // AAAA-MM-DD
}
