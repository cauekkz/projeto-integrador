export interface tokenResponse {
  token: string;
  expiresIn: number;
}

export interface loginRequest {
  cpf: string;
  passwordHash: string;
}


export interface createUserRequest {
  name: string;
  email: string;
  password: string;
  confirmPassword: string;
  cpf: string;
  phone: string;
}

export interface verifyEmailRequest {
  email: string;
  code: string;
}
