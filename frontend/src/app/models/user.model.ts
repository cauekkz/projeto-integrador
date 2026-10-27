// GET /api/users/me: dados do usuario logado; linkCode so vem pro motorista
export interface UserMe {
  id: string;
  name: string;
  email: string;
  phone: string;
  cpf: string;
  roles: string[];
  photoKey: string | null;
  linkCode: string | null;
  // so do front: link temporario da foto (gerado a partir do photoKey)
  photoUrl?: string;
}

export interface User {
  id: string;
  name: string;
  cpf: string;
  email: string;
  phone: string;
  password?: string;
  passwordHash?: string;
  status: string;
  createdAt: string;
  enabled: boolean;
  accountNonExpired: boolean;
  accountNonLocked: boolean;
  credentialsNonExpired: boolean;
  username: string;
  role: string;

  authorities: Authority[];
  roles: Role[];
  photoUrl?: string;
}

export interface Authority {
  authority: string;
}

export interface Role {
  id: string;
  nome: string;
}
