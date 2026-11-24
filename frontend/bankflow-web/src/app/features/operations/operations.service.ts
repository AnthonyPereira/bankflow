import {
  HttpClient,
  HttpContext,
  HttpErrorResponse,
  HttpHeaders,
  HttpParams,
} from '@angular/common/http';
import { inject, Service } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { SILENT_ERRORS } from '../../core/http/api';
import {
  getRoleMessages,
  translateProblem,
  UNEXPECTED_ERROR_MESSAGE,
} from '../../core/http/problem-details';
import { PageResponse } from '../accounts/accounts.models';
import { OperationRequest, Transaction, TransactionFilters } from './operations.models';

const ACCOUNTS_URL = `${environment.apiUrl}/accounts`;
const TRANSACTIONS_URL = `${environment.apiUrl}/transactions`;

export const ACCOUNT_NOT_FOUND_MESSAGE = 'Compte introuvable.';

function getOperationMessages(isAdmin: boolean): Record<string, string> {
  return {
    INSUFFICIENT_FUNDS: 'Le solde est insuffisant.',
    ACCOUNT_CLOSED: 'Ce compte est clôturé.',
    IDEMPOTENCY_KEY_REUSED: 'Cette demande a déjà été traitée différemment, recommencez.',
    ALREADY_COMPENSATED: 'Cette opération a déjà été annulée.',
    COMPENSATION_NOT_ALLOWED: 'Cette opération ne peut pas être annulée.',
    CUSTOMER_NOT_FOUND: 'Client introuvable.',
    ACCOUNT_NOT_FOUND: ACCOUNT_NOT_FOUND_MESSAGE,
    TRANSACTION_NOT_FOUND: 'Opération introuvable.',
    ACCOUNT_BALANCE_NOT_ZERO: 'Le solde doit être à zéro pour clôturer le compte.',
    DESTINATION_ACCOUNT_NOT_FOUND: 'Aucun compte actif ne correspond à cet IBAN.',
    CURRENCY_MISMATCH: 'Les deux comptes doivent avoir la même devise.',
    ...getRoleMessages(isAdmin),
  };
}

/** Traduit l'erreur de l'API en français, selon le rôle de celui qui agit. */
export function getOperationErrorMessage(error: unknown, isAdmin: boolean): string {
  const fallback =
    error instanceof HttpErrorResponse && error.status === 404
      ? ACCOUNT_NOT_FOUND_MESSAGE
      : UNEXPECTED_ERROR_MESSAGE;
  return translateProblem(error, getOperationMessages(isAdmin), fallback);
}

@Service()
export class OperationsService {
  private readonly http = inject(HttpClient);

  listTransactions(
    filters: TransactionFilters,
    page: number,
    size: number,
    isSilent = false,
  ): Observable<PageResponse<Transaction>> {
    let params = new HttpParams().set('page', page).set('size', size);
    for (const [key, value] of Object.entries(filters)) {
      if (value) {
        params = params.set(key, value);
      }
    }
    return this.http.get<PageResponse<Transaction>>(TRANSACTIONS_URL, {
      params,
      context: new HttpContext().set(SILENT_ERRORS, isSilent),
    });
  }

  /** Les erreurs métier sont affichées par l'écran, pas par un message global. */
  deposit(
    accountId: string,
    request: OperationRequest,
    idempotencyKey: string,
  ): Observable<Transaction> {
    return this.postOperation(`${ACCOUNTS_URL}/${accountId}/deposits`, request, idempotencyKey);
  }

  withdraw(
    accountId: string,
    request: OperationRequest,
    idempotencyKey: string,
  ): Observable<Transaction> {
    return this.postOperation(`${ACCOUNTS_URL}/${accountId}/withdrawals`, request, idempotencyKey);
  }

  /** Réservé au conseiller : le motif est conservé avec l'écriture compensatrice. */
  compensate(transactionId: string, reason: string): Observable<Transaction> {
    return this.http.post<Transaction>(
      `${TRANSACTIONS_URL}/${transactionId}/compensations`,
      { reason },
      { context: new HttpContext().set(SILENT_ERRORS, true) },
    );
  }

  private postOperation(
    url: string,
    request: OperationRequest,
    idempotencyKey: string,
  ): Observable<Transaction> {
    return this.http.post<Transaction>(url, request, {
      headers: new HttpHeaders({ 'Idempotency-Key': idempotencyKey }),
      context: new HttpContext().set(SILENT_ERRORS, true),
    });
  }
}
