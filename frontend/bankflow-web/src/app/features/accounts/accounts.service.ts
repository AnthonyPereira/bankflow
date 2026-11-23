import { HttpClient, HttpContext, HttpErrorResponse, HttpParams } from '@angular/common/http';
import { inject, Service } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { SILENT_ERRORS } from '../../core/http/api';
import { Account, AccountsSummary, OpenAccountRequest, PageResponse } from './accounts.models';

const ACCOUNTS_URL = `${environment.apiUrl}/accounts`;

export const UNEXPECTED_ACCOUNT_ERROR = 'Une erreur est survenue. Réessayez plus tard.';

const ERROR_MESSAGES: Record<string, string> = {
  CUSTOMER_NOT_READY: 'Votre profil est en cours de création, réessayez dans quelques secondes.',
  ACCOUNT_LIMIT_REACHED: 'Vous avez atteint la limite de 5 comptes ouverts.',
  CUSTOMER_SUSPENDED: 'Votre profil est suspendu : contactez un conseiller.',
};

/** Traduit le `code` du ProblemDetail en français, avec repli sur son `detail`. */
export function getAccountErrorMessage(error: unknown): string {
  if (!(error instanceof HttpErrorResponse)) {
    return UNEXPECTED_ACCOUNT_ERROR;
  }
  const body: unknown = error.error;
  if (typeof body === 'object' && body !== null) {
    const { code, detail } = body as { code?: unknown; detail?: unknown };
    if (typeof code === 'string' && code in ERROR_MESSAGES) {
      return ERROR_MESSAGES[code];
    }
    if (typeof detail === 'string' && detail) {
      return detail;
    }
  }
  return UNEXPECTED_ACCOUNT_ERROR;
}

@Service()
export class AccountsService {
  private readonly http = inject(HttpClient);

  listAccounts(page: number, size: number): Observable<PageResponse<Account>> {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http.get<PageResponse<Account>>(ACCOUNTS_URL, { params });
  }

  /** Vue d'ensemble de la banque, réservée à l'administrateur. */
  getSummary(): Observable<AccountsSummary> {
    return this.http.get<AccountsSummary>(`${ACCOUNTS_URL}/summary`);
  }

  /** Les erreurs métier (403, 409) sont affichées par l'écran, pas par un message global. */
  openAccount(request: OpenAccountRequest): Observable<Account> {
    return this.http.post<Account>(ACCOUNTS_URL, request, {
      context: new HttpContext().set(SILENT_ERRORS, true),
    });
  }
}
