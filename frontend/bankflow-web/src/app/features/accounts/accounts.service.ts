import { HttpClient, HttpContext, HttpParams } from '@angular/common/http';
import { inject, Service } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { SILENT_ERRORS } from '../../core/http/api';
import { getRoleMessages, translateProblem } from '../../core/http/problem-details';
import { Account, AccountsSummary, OpenAccountRequest, PageResponse } from './accounts.models';

const ACCOUNTS_URL = `${environment.apiUrl}/accounts`;

/** Un client n'a pas de profil tout de suite après son inscription : il est créé par un événement. */
function getAccountMessages(isAdmin: boolean): Record<string, string> {
  return {
    CUSTOMER_NOT_READY: 'Votre profil est en cours de création, réessayez dans quelques secondes.',
    ...getRoleMessages(isAdmin),
  };
}

/** Traduit l'erreur de l'API en français, selon le rôle de celui qui agit. */
export function getAccountErrorMessage(error: unknown, isAdmin: boolean): string {
  return translateProblem(error, getAccountMessages(isAdmin));
}

@Service()
export class AccountsService {
  private readonly http = inject(HttpClient);

  /** `isSilent` : l'écran affiche lui-même l'erreur de ce bloc. */
  listAccounts(page: number, size: number, isSilent = false): Observable<PageResponse<Account>> {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http.get<PageResponse<Account>>(ACCOUNTS_URL, {
      params,
      context: new HttpContext().set(SILENT_ERRORS, isSilent),
    });
  }

  /** Le 404 (compte inconnu ou d'un autre client) est affiché par l'écran. */
  getAccount(accountId: string): Observable<Account> {
    return this.http.get<Account>(`${ACCOUNTS_URL}/${accountId}`, {
      context: new HttpContext().set(SILENT_ERRORS, true),
    });
  }

  closeAccount(accountId: string): Observable<Account> {
    return this.http.post<Account>(`${ACCOUNTS_URL}/${accountId}/close`, null, {
      context: new HttpContext().set(SILENT_ERRORS, true),
    });
  }

  /** Vue d'ensemble de la banque, réservée à l'administrateur. */
  getSummary(): Observable<AccountsSummary> {
    return this.http.get<AccountsSummary>(`${ACCOUNTS_URL}/summary`, {
      context: new HttpContext().set(SILENT_ERRORS, true),
    });
  }

  /** Les erreurs métier (403, 409) sont affichées par l'écran, pas par un message global. */
  openAccount(request: OpenAccountRequest): Observable<Account> {
    return this.http.post<Account>(ACCOUNTS_URL, request, {
      context: new HttpContext().set(SILENT_ERRORS, true),
    });
  }
}
