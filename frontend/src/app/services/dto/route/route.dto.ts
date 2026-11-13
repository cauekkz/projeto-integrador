// tipos de /api/route (montar a rota). Regras em docs/FLUXO_ROTA.md

// enum WeekDay do backend
export type WeekDay = 'MONDAY' | 'TUESDAY' | 'WEDNESDAY' | 'THURSDAY' | 'FRIDAY' | 'SATURDAY' | 'SUNDAY';
export type StopAction = 'BOARDING' | 'ALIGHTING';
export type StopLocationType = 'HOME' | 'SCHOOL' | 'OTHER';

// ordem da semana, com o rotulo curto dos chips do Figma
export const DIAS_DA_SEMANA: { dia: WeekDay; curto: string; nome: string }[] = [
  { dia: 'MONDAY', curto: 'Seg', nome: 'segunda' },
  { dia: 'TUESDAY', curto: 'Ter', nome: 'terça' },
  { dia: 'WEDNESDAY', curto: 'Qua', nome: 'quarta' },
  { dia: 'THURSDAY', curto: 'Qui', nome: 'quinta' },
  { dia: 'FRIDAY', curto: 'Sex', nome: 'sexta' },
  { dia: 'SATURDAY', curto: 'Sáb', nome: 'sábado' },
  { dia: 'SUNDAY', curto: 'Dom', nome: 'domingo' },
];

// AddressResponseDTO
export interface routeAddress {
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

// RouteVehicleResponse: van padrao da rota (null = nenhuma escolhida)
export interface routeVehicle {
  id: string;
  plate: string;
  model: string | null;
  color: string | null;
  capacity: number | null;
}

// RouteResponse
export interface routeResponse {
  id: string;
  name: string;
  driverName: string;
  vehicle: routeVehicle | null;
}

// StudentStopResponseDTO: um aluno num ponto (weekdays vazio = todos os dias)
export interface studentStop {
  id: string;
  studentId: string;
  studentName: string;
  stopId: string;
  locationType: StopLocationType;
  action: StopAction;
  weekdays: WeekDay[];
}

// SchoolStopResponseDTO (so o id da escola: o nome vem de GET /api/school/my-schools)
export interface schoolStop {
  id: string;
  schoolId: string;
  stopId: string;
}

// RouteStopResponse: um ponto da rota, em ordem (orderIndex a partir de 1)
export interface routeStop {
  id: string;
  orderIndex: number;
  address: routeAddress;
  students: studentStop[];
  schools: schoolStop[];
}

// RouteWithStopsResponse (item da lista paginada)
export interface routeWithStops extends routeResponse {
  stops: routeStop[];
}

// RoutePendingResponse: dias em que falta embarque ou desembarque do aluno
export interface routePending {
  studentId: string;
  studentName: string;
  missingBoarding: WeekDay[];
  missingAlighting: WeekDay[];
}

// RouteEditResponse: toda alteracao da rota devolve a rota inteira e as pendencias
export interface routeEdit {
  stops: routeStop[];
  pending: routePending[];
}

// RouteStopOptionsDTO: alunos com contrato ACTIVE e os enderecos deles (id = studentAddressId)
export interface stopOptionAddress {
  id: string;
  label: string;
  address: routeAddress;
}

export interface stopOptionStudent {
  studentId: string;
  studentName: string;
  addresses: stopOptionAddress[];
}

export interface stopOptions {
  students: stopOptionStudent[];
}

// AddStudentToRouteRequest: newStop OU routeStopId (um dos dois)
export interface addStudentRequest {
  action: StopAction;
  locationType: StopLocationType;
  studentAddressId?: string;
  routeStopId?: string;
  newStop?: boolean;
  weekdays?: WeekDay[];
  orderIndex?: number;
}

// "Av. Paulista, 1000"
export function ruaENumero(endereco: routeAddress | null | undefined): string {
  if (!endereco) return '';
  return endereco.number != null ? `${endereco.street}, ${endereco.number}` : endereco.street;
}

// dias em ordem; sem dias (ou os 7) = todos os dias
export function ordenarDias(dias: WeekDay[] | null | undefined): WeekDay[] {
  const marcados = new Set(dias ?? []);
  return DIAS_DA_SEMANA.map((d) => d.dia).filter((d) => marcados.has(d));
}

// "Todos os dias", "Seg a Sex", "Seg a Qui", "Seg, Qua e Sex"
export function resumoDias(dias: WeekDay[] | null | undefined): string {
  const ordenados = ordenarDias(dias);
  if (!ordenados.length || ordenados.length === 7) return 'Todos os dias';
  const indices = ordenados.map((d) => DIAS_DA_SEMANA.findIndex((x) => x.dia === d));
  const curto = (i: number) => DIAS_DA_SEMANA[i].curto;
  const seguidos = indices.every((v, i) => i === 0 || v === indices[i - 1] + 1);
  if (seguidos && indices.length >= 3) return `${curto(indices[0])} a ${curto(indices[indices.length - 1])}`;
  const nomes = indices.map(curto);
  return nomes.length === 1 ? nomes[0] : `${nomes.slice(0, -1).join(', ')} e ${nomes[nomes.length - 1]}`;
}

// "segunda, quarta e sexta" (como as mensagens do backend)
export function nomesDosDias(dias: WeekDay[] | null | undefined): string {
  const nomes = ordenarDias(dias).map((d) => DIAS_DA_SEMANA.find((x) => x.dia === d)!.nome);
  if (nomes.length <= 1) return nomes.join('');
  return `${nomes.slice(0, -1).join(', ')} e ${nomes[nomes.length - 1]}`;
}

// "Lucas: falta embarque na sexta" / "Ana: falta desembarque na segunda e terça"
export function textoPendencia(p: routePending): string {
  const partes: string[] = [];
  if (p.missingBoarding?.length) partes.push(`falta embarque na ${nomesDosDias(p.missingBoarding)}`);
  if (p.missingAlighting?.length) partes.push(`falta desembarque na ${nomesDosDias(p.missingAlighting)}`);
  return `${p.studentName.split(' ')[0]}: ${partes.join('; ')}`;
}
