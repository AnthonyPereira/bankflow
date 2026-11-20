import { HttpContextToken } from '@angular/common/http';
import { environment } from '../../../environments/environment';

/** Une requête marquée ainsi gère elle-même ses erreurs, sans message global. */
export const SILENT_ERRORS = new HttpContextToken<boolean>(() => false);

const PUBLIC_AUTH_URLS = ['login', 'register', 'refresh', 'logout', 'demo-accounts'].map(
  (path) => `${environment.apiUrl}/auth/${path}`,
);

export function isApiRequest(url: string): boolean {
  return url.startsWith(`${environment.apiUrl}/`);
}

/** Ces routes n'attendent pas de JWT, et leur 401 ne signifie pas qu'une session a expiré. */
export function isPublicAuthRequest(url: string): boolean {
  return PUBLIC_AUTH_URLS.includes(url);
}
