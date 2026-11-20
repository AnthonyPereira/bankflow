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

/** Compte de démonstration au mot de passe public, affiché sur la page de connexion. */
export interface DemoAccount {
  email: string;
  password: string;
  role: Role;
}

export interface User {
  id: string;
  email: string;
  role: Role;
  createdAt: string;
}
