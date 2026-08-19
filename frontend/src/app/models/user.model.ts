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
