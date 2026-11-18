export type Role = 'ROLE_CLIENT' | 'ROLE_ADMIN';

export interface Credentials {
  email: string;
  password: string;
}

export interface TokenResponse {
  accessToken: string;
  tokenType: string;
  expiresIn: number;
}

export interface User {
  id: string;
  email: string;
  role: Role;
  createdAt: string;
}
