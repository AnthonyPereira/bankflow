import { HttpErrorResponse, provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { SILENT_ERRORS } from '../../core/http/api';
import { canCompensate, Transaction } from './operations.models';
import { NETWORK_ERROR_MESSAGE, UNEXPECTED_ERROR_MESSAGE } from '../../core/http/problem-details';
import { getOperationErrorMessage, OperationsService } from './operations.service';

const TRANSACTION: Transaction = {
  id: 't1',
  accountId: 'a1',
  type: 'DEPOSIT',
  amount: '150.00',
  currency: 'EUR',
  balanceAfter: '150.00',
  transferId: null,
  compensatesTransactionId: null,
  compensatedByTransactionId: null,
  description: null,
  direction: 'CREDIT',
  performedBy: 'CLIENT',
  createdAt: '2026-11-23T10:00:00Z',
};

describe('OperationsService', () => {
  let service: OperationsService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(OperationsService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('liste les opérations avec la pagination et les filtres renseignés seulement', () => {
    service
      .listTransactions({ accountId: 'a1', type: 'DEPOSIT', from: '2026-11-01', to: '' }, 2, 20)
      .subscribe();

    const request = http.expectOne((r) => r.url === '/api/transactions');
    expect(request.request.method).toBe('GET');
    expect(request.request.params.get('page')).toBe('2');
    expect(request.request.params.get('size')).toBe('20');
    expect(request.request.params.get('accountId')).toBe('a1');
    expect(request.request.params.get('type')).toBe('DEPOSIT');
    expect(request.request.params.get('from')).toBe('2026-11-01');
    expect(request.request.params.has('to')).toBe(false);
    request.flush({ content: [], page: 2, size: 20, totalElements: 0, totalPages: 0 });
  });

  it.each([
    ['deposit', 'deposits'],
    ['withdraw', 'withdrawals'],
  ] as const)(
    'envoie %s avec la clé d’idempotence, en gérant lui-même l’erreur',
    (method, path) => {
      service[method]('a1', { amount: '150.00', description: 'Salaire' }, 'cle-1').subscribe();

      const request = http.expectOne(`/api/accounts/a1/${path}`);
      expect(request.request.method).toBe('POST');
      expect(request.request.headers.get('Idempotency-Key')).toBe('cle-1');
      expect(request.request.body).toEqual({ amount: '150.00', description: 'Salaire' });
      expect(request.request.context.get(SILENT_ERRORS)).toBe(true);
      request.flush(TRANSACTION, { status: 201, statusText: 'Created' });
    },
  );

  it('envoie la même clé quand l’appelant la redonne après une erreur réseau', () => {
    const body = { amount: '10.00', description: null };
    service.deposit('a1', body, 'cle-1').subscribe({ error: () => undefined });
    http.expectOne('/api/accounts/a1/deposits').error(new ProgressEvent('error'));
    service.deposit('a1', body, 'cle-1').subscribe();

    const retry = http.expectOne('/api/accounts/a1/deposits');
    expect(retry.request.headers.get('Idempotency-Key')).toBe('cle-1');
    retry.flush(TRANSACTION);
  });

  it('annule une opération avec son motif', () => {
    service.compensate('t1', 'Erreur de saisie du client').subscribe();

    const request = http.expectOne('/api/transactions/t1/compensations');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ reason: 'Erreur de saisie du client' });
    expect(request.request.context.get(SILENT_ERRORS)).toBe(true);
    request.flush(TRANSACTION, { status: 201, statusText: 'Created' });
  });
});

describe('getOperationErrorMessage', () => {
  function build(body: unknown, status = 409): HttpErrorResponse {
    return new HttpErrorResponse({ status, error: body });
  }

  it.each([
    ['INSUFFICIENT_FUNDS', 'Le solde est insuffisant.'],
    ['ACCOUNT_CLOSED', 'Ce compte est clôturé.'],
    ['IDEMPOTENCY_KEY_REUSED', 'Cette demande a déjà été traitée différemment, recommencez.'],
    ['ALREADY_COMPENSATED', 'Cette opération a déjà été annulée.'],
    ['COMPENSATION_NOT_ALLOWED', 'Cette opération ne peut pas être annulée.'],
    ['CUSTOMER_NOT_FOUND', 'Client introuvable.'],
    ['ACCOUNT_BALANCE_NOT_ZERO', 'Le solde doit être à zéro pour clôturer le compte.'],
    ['DESTINATION_ACCOUNT_NOT_FOUND', 'Aucun compte actif ne correspond à cet IBAN.'],
    ['CURRENCY_MISMATCH', 'Les deux comptes doivent avoir la même devise.'],
    ['ACCOUNT_NOT_FOUND', 'Compte introuvable.'],
  ])('traduit %s', (code, message) => {
    expect(getOperationErrorMessage(build({ code, detail: 'autre' }), false)).toBe(message);
  });

  it.each([
    ['CUSTOMER_SUSPENDED', false, 'Votre profil est suspendu : contactez un conseiller.'],
    ['CUSTOMER_SUSPENDED', true, "Ce client est suspendu : aucune opération n'est possible."],
    ['ACCOUNT_LIMIT_REACHED', false, 'Vous avez atteint la limite de 5 comptes ouverts.'],
    ['ACCOUNT_LIMIT_REACHED', true, 'Ce client a déjà 5 comptes ouverts.'],
  ])('traduit %s selon le rôle (administrateur : %s)', (code, isAdmin, message) => {
    expect(getOperationErrorMessage(build({ code }), isAdmin)).toBe(message);
  });

  it('répond « Compte introuvable. » à un 404 sans code', () => {
    expect(getOperationErrorMessage(build(null, 404), false)).toBe('Compte introuvable.');
  });

  it('signale un serveur injoignable', () => {
    expect(getOperationErrorMessage(build(null, 0), false)).toBe(NETWORK_ERROR_MESSAGE);
  });

  it('se replie sur le detail puis sur un message générique', () => {
    expect(getOperationErrorMessage(build({ code: 'AUTRE', detail: 'Détail.' }), false)).toBe(
      'Détail.',
    );
    expect(getOperationErrorMessage(build(null, 500), false)).toBe(UNEXPECTED_ERROR_MESSAGE);
    expect(getOperationErrorMessage(new Error('x'), false)).toBe(UNEXPECTED_ERROR_MESSAGE);
  });
});

describe('modèles des opérations', () => {
  it('ne compense qu’un dépôt ou un retrait pas encore annulé', () => {
    expect(canCompensate(TRANSACTION)).toBe(true);
    expect(canCompensate({ ...TRANSACTION, type: 'WITHDRAWAL' })).toBe(true);
    expect(canCompensate({ ...TRANSACTION, compensatedByTransactionId: 'c1' })).toBe(false);
    expect(canCompensate({ ...TRANSACTION, type: 'TRANSFER_OUT' })).toBe(false);
    expect(canCompensate({ ...TRANSACTION, type: 'COMPENSATION' })).toBe(false);
  });
});
