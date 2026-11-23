export type Role = 'ROLE_CLIENT' | 'ROLE_ADMIN';

/** Seule table des libellés de rôle : le type impose un libellé à tout nouveau rôle. */
export const ROLE_LABELS: Record<Role, string> = {
  ROLE_CLIENT: 'Client',
  ROLE_ADMIN: 'Administrateur',
};

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
