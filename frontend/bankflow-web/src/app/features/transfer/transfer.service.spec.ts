import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { SILENT_ERRORS } from '../../core/http/api';
import { TransferService } from './transfer.service';

describe('TransferService', () => {
  let service: TransferService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(TransferService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it("envoie le virement avec la clé d'idempotence en en-tête et l'IBAN normalisé", () => {
    service
      .createTransfer(
        {
          sourceAccountId: 'a1',
          destinationIban: 'fr76 9999 9000 0101 2345 6789 012',
          amount: '200.00',
          description: 'Loyer',
        },
        'cle-1',
      )
      .subscribe();

    const request = http.expectOne('/api/transfers');
    expect(request.request.method).toBe('POST');
    expect(request.request.headers.get('Idempotency-Key')).toBe('cle-1');
    expect(request.request.body).toEqual({
      sourceAccountId: 'a1',
      destinationIban: 'FR7699999000010123456789012',
      amount: '200.00',
      description: 'Loyer',
    });
    expect(request.request.context.get(SILENT_ERRORS)).toBe(true);
    request.flush({});
  });
});
