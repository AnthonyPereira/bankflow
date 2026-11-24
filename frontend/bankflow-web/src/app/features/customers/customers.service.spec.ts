import { HttpErrorResponse, provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { SILENT_ERRORS } from '../../core/http/api';
import { UNEXPECTED_ERROR_MESSAGE } from '../../core/http/problem-details';
import { CustomersService, getCustomerErrorMessage } from './customers.service';

describe('CustomersService', () => {
  let service: CustomersService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(CustomersService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it("liste les clients sans filtre : ni statut ni recherche n'est envoyé", () => {
    service.listCustomers({ page: 0, size: 20, status: 'ALL', term: '' }).subscribe();

    const request = http.expectOne((r) => r.url === '/api/customers');
    expect(request.request.method).toBe('GET');
    expect(request.request.params.get('page')).toBe('0');
    expect(request.request.params.get('size')).toBe('20');
    expect(request.request.params.has('status')).toBe(false);
    expect(request.request.params.has('q')).toBe(false);
    expect(request.request.context.get(SILENT_ERRORS)).toBe(true);
    request.flush({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 });
  });

  it('envoie la pagination, le statut et la recherche', () => {
    service.listCustomers({ page: 2, size: 20, status: 'SUSPENDED', term: 'ada' }).subscribe();

    const request = http.expectOne((r) => r.url === '/api/customers');
    expect(request.request.params.get('page')).toBe('2');
    expect(request.request.params.get('status')).toBe('SUSPENDED');
    expect(request.request.params.get('q')).toBe('ada');
    request.flush({ content: [], page: 2, size: 20, totalElements: 0, totalPages: 0 });
  });

  it('charge un client', () => {
    service.getCustomer('c1').subscribe();

    const request = http.expectOne('/api/customers/c1');
    expect(request.request.method).toBe('GET');
    expect(request.request.context.get(SILENT_ERRORS)).toBe(true);
    request.flush({});
  });

  it("change le statut d'un client", () => {
    service.changeStatus('c1', 'SUSPENDED').subscribe();

    const request = http.expectOne('/api/customers/c1/status');
    expect(request.request.method).toBe('PUT');
    expect(request.request.body).toEqual({ status: 'SUSPENDED' });
    expect(request.request.context.get(SILENT_ERRORS)).toBe(true);
    request.flush({});
  });

  it("liste les comptes d'un client", () => {
    service.listCustomerAccounts('c1').subscribe();

    const request = http.expectOne((r) => r.url === '/api/accounts');
    expect(request.request.method).toBe('GET');
    expect(request.request.params.get('customerId')).toBe('c1');
    expect(request.request.params.get('page')).toBe('0');
    expect(request.request.params.get('size')).toBe('100');
    request.flush({ content: [], page: 0, size: 100, totalElements: 0, totalPages: 0 });
  });

  it("ouvre un compte au nom du client avec son customerId, en gérant lui-même l'erreur", () => {
    service.openAccount('c1', { currency: 'USD', label: 'Voyage' }).subscribe();

    const request = http.expectOne('/api/accounts');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ currency: 'USD', label: 'Voyage', customerId: 'c1' });
    expect(request.request.context.get(SILENT_ERRORS)).toBe(true);
    request.flush({}, { status: 201, statusText: 'Created' });
  });
});

describe('getCustomerErrorMessage', () => {
  function build(body: unknown): HttpErrorResponse {
    return new HttpErrorResponse({ status: 409, error: body });
  }

  it.each([
    ['CUSTOMER_NOT_FOUND', 'Client introuvable.'],
    ['CUSTOMER_SUSPENDED', "Ce client est suspendu : aucune opération n'est possible."],
    ['ACCOUNT_LIMIT_REACHED', 'Ce client a déjà 5 comptes ouverts.'],
    ['DEMO_CUSTOMER_CANNOT_BE_SUSPENDED', 'Un compte de démonstration ne peut pas être suspendu.'],
  ])('traduit %s', (code, message) => {
    expect(getCustomerErrorMessage(build({ code, detail: 'autre' }))).toBe(message);
  });

  it('se replie sur le detail pour un code inconnu', () => {
    expect(getCustomerErrorMessage(build({ code: 'AUTRE', detail: 'Détail du serveur.' }))).toBe(
      'Détail du serveur.',
    );
  });

  it('renvoie un message générique sans détail exploitable', () => {
    expect(getCustomerErrorMessage(build(null))).toBe(UNEXPECTED_ERROR_MESSAGE);
    expect(getCustomerErrorMessage(new Error('x'))).toBe(UNEXPECTED_ERROR_MESSAGE);
  });
});
