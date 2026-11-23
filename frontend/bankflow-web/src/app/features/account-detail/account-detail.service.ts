import { HttpClient, HttpContext } from '@angular/common/http';
import { inject, Service } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { SILENT_ERRORS } from '../../core/http/api';
import { Account } from '../accounts/accounts.models';

const ACCOUNTS_URL = `${environment.apiUrl}/accounts`;

@Service()
export class AccountDetailService {
  private readonly http = inject(HttpClient);

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
}
