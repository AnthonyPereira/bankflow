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
import { PageResponse } from '../accounts/accounts.models';
import { OperationRequest, Transaction, TransactionFilters } from './operations.models';

const ACCOUNTS_URL = `${environment.apiUrl}/accounts`;
const TRANSACTIONS_URL = `${environment.apiUrl}/transactions`;

export const UNEXPECTED_OPERATION_ERROR = 'Une erreur est survenue. Réessayez plus tard.';
export const NETWORK_ERROR_MESSAGE = 'Le serveur est injoignable. Réessayez dans un instant.';
export const ACCOUNT_NOT_FOUND_MESSAGE = 'Compte introuvable.';

const ERROR_MESSAGES: Record<string, string> = {
  INSUFFICIENT_FUNDS: 'Le solde est insuffisant.',
  ACCOUNT_CLOSED: 'Ce compte est clôturé.',
  CUSTOMER_SUSPENDED: "Ce client est suspendu : aucune opération n'est possible.",
  IDEMPOTENCY_KEY_REUSED: 'Cette demande a déjà été traitée différemment, recommencez.',
  ALREADY_COMPENSATED: 'Cette opération a déjà été annulée.',
  COMPENSATION_NOT_ALLOWED: 'Cette opération ne peut pas être annulée.',
  CUSTOMER_NOT_FOUND: 'Client introuvable.',
  ACCOUNT_NOT_FOUND: ACCOUNT_NOT_FOUND_MESSAGE,
  TRANSACTION_NOT_FOUND: 'Opération introuvable.',
  ACCOUNT_BALANCE_NOT_ZERO: 'Le solde doit être à zéro pour clôturer le compte.',
  ACCOUNT_LIMIT_REACHED: 'Ce client a déjà 5 comptes ouverts.',
};

/** Traduit le `code` du ProblemDetail en français, avec repli sur son `detail`. */
export function getOperationErrorMessage(error: unknown): string {
  if (!(error instanceof HttpErrorResponse)) {
    return UNEXPECTED_OPERATION_ERROR;
  }
  if (error.status === 0) {
    return NETWORK_ERROR_MESSAGE;
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
  return error.status === 404 ? ACCOUNT_NOT_FOUND_MESSAGE : UNEXPECTED_OPERATION_ERROR;
}

/** Code métier du ProblemDetail, `null` si l'erreur n'en porte pas. */
export function getErrorCode(error: unknown): string | null {
  const body: unknown = error instanceof HttpErrorResponse ? error.error : null;
  const code = typeof body === 'object' && body !== null ? (body as { code?: unknown }).code : null;
  return typeof code === 'string' ? code : null;
}

/** Une erreur réseau ou serveur laisse l'issue inconnue : la nouvelle tentative réutilise la clé. */
export function isRetryableError(error: unknown): boolean {
  return error instanceof HttpErrorResponse && (error.status === 0 || error.status >= 500);
}

/** Messages de validation par champ (`errors` du ProblemDetail), vide si le serveur n'en donne pas. */
export function getFieldErrors(error: unknown): Record<string, string> {
  const raw: unknown = error instanceof HttpErrorResponse ? error.error?.errors : null;
  const result: Record<string, string> = {};
  if (raw && typeof raw === 'object') {
    for (const [field, message] of Object.entries(raw)) {
      if (typeof message === 'string') {
        result[field] = message;
      }
    }
  }
  return result;
}

@Service()
export class OperationsService {
  private readonly http = inject(HttpClient);

  listTransactions(
    filters: TransactionFilters,
    page: number,
    size: number,
  ): Observable<PageResponse<Transaction>> {
    let params = new HttpParams().set('page', page).set('size', size);
    for (const [key, value] of Object.entries(filters)) {
      if (value) {
        params = params.set(key, value);
      }
    }
    return this.http.get<PageResponse<Transaction>>(TRANSACTIONS_URL, { params });
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
