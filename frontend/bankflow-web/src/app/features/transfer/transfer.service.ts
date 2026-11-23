import { HttpClient, HttpContext, HttpHeaders } from '@angular/common/http';
import { inject, Service } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { SILENT_ERRORS } from '../../core/http/api';
import { normalizeIban } from '../accounts/accounts.models';
import { Transfer, TransferRequest } from './transfer.models';

const TRANSFERS_URL = `${environment.apiUrl}/transfers`;

@Service()
export class TransferService {
  private readonly http = inject(HttpClient);

  /**
   * L'IBAN part normalisé et la clé d'idempotence dans l'en-tête : rejouer la même clé ne crée
   * jamais un second virement. Les erreurs métier sont affichées par l'écran.
   */
  createTransfer(request: TransferRequest, idempotencyKey: string): Observable<Transfer> {
    return this.http.post<Transfer>(
      TRANSFERS_URL,
      { ...request, destinationIban: normalizeIban(request.destinationIban) },
      {
        headers: new HttpHeaders({ 'Idempotency-Key': idempotencyKey }),
        context: new HttpContext().set(SILENT_ERRORS, true),
      },
    );
  }
}
