import { HttpClient, HttpContext, HttpParams } from '@angular/common/http';
import { inject, Service } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { SILENT_ERRORS } from '../../core/http/api';
import { getRoleMessages, translateProblem } from '../../core/http/problem-details';
import { Account, OpenAccountRequest } from '../accounts/accounts.models';
import { Customer, CustomerSearch, CustomerStatus, PageResponse } from './customers.models';

const CUSTOMERS_URL = `${environment.apiUrl}/customers`;
const ACCOUNTS_URL = `${environment.apiUrl}/accounts`;
/** Un client a 5 comptes ouverts au plus : une page suffit. */
const CUSTOMER_ACCOUNTS_SIZE = 100;

/** Ces écrans sont ceux du conseiller : les messages parlent du client. */
const ERROR_MESSAGES: Record<string, string> = {
  CUSTOMER_NOT_FOUND: 'Client introuvable.',
  DEMO_CUSTOMER_CANNOT_BE_SUSPENDED: 'Un compte de démonstration ne peut pas être suspendu.',
  ...getRoleMessages(true),
};

/** Traduit le `code` du ProblemDetail en français, avec repli sur son `detail`. */
export function getCustomerErrorMessage(error: unknown): string {
  return translateProblem(error, ERROR_MESSAGES);
}

/** Les erreurs sont affichées par chaque écran, pas par un message global. */
function createSilentContext(): HttpContext {
  return new HttpContext().set(SILENT_ERRORS, true);
}

@Service()
export class CustomersService {
  private readonly http = inject(HttpClient);

  listCustomers(search: CustomerSearch): Observable<PageResponse<Customer>> {
    let params = new HttpParams().set('page', search.page).set('size', search.size);
    if (search.status !== 'ALL') {
      params = params.set('status', search.status);
    }
    if (search.term) {
      params = params.set('q', search.term);
    }
    return this.http.get<PageResponse<Customer>>(CUSTOMERS_URL, {
      params,
      context: createSilentContext(),
    });
  }

  getCustomer(id: string): Observable<Customer> {
    return this.http.get<Customer>(`${CUSTOMERS_URL}/${encodeURIComponent(id)}`, {
      context: createSilentContext(),
    });
  }

  changeStatus(id: string, status: CustomerStatus): Observable<Customer> {
    return this.http.put<Customer>(
      `${CUSTOMERS_URL}/${encodeURIComponent(id)}/status`,
      { status },
      { context: createSilentContext() },
    );
  }

  listCustomerAccounts(customerId: string): Observable<PageResponse<Account>> {
    const params = new HttpParams()
      .set('customerId', customerId)
      .set('page', 0)
      .set('size', CUSTOMER_ACCOUNTS_SIZE);
    return this.http.get<PageResponse<Account>>(ACCOUNTS_URL, {
      params,
      context: createSilentContext(),
    });
  }

  /** L'administrateur ouvre le compte au nom du client : `customerId` est obligatoire. */
  openAccount(customerId: string, request: OpenAccountRequest): Observable<Account> {
    return this.http.post<Account>(
      ACCOUNTS_URL,
      { ...request, customerId },
      { context: createSilentContext() },
    );
  }
}
