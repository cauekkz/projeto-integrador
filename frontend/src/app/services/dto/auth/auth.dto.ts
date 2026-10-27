// login e refresh devolvem o mesmo par: o token dura 15 min, o refreshToken troca por um par novo
export interface tokenResponse {
  token: string;
  expiresIn: number;
  refreshToken: string;
  refreshExpiresIn: number;
}

export interface loginRequest {
  cpf: string;
  passwordHash: string;
}

export interface addressRequest {
  street: string;
  number: number | null;
  neighborhood: string;
  city: string;
  state: string;
  zipCode: string;
}

export interface createUserRequest {
  name: string;
  email: string;
  password: string;
  confirmPassword: string;
  cpf: string;
  phone: string;
  address: addressRequest;
}

export interface verifyEmailRequest {
  email: string;
  code: string;
}

export interface updateUserRequest {
  email: string;
  phone: string;
  code: string;
}

// esqueci a senha (rota publica): o usuario e achado pelo e-mail que recebeu o codigo
export interface forgotPasswordRequest {
  email: string;
  code: string;
  password: string;
  confirmPassword: string;
}
