import { HttpErrorResponse, provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { SILENT_ERRORS } from '../../core/http/api';
import { UNEXPECTED_ERROR_MESSAGE } from '../../core/http/problem-details';
import { AccountsService, getAccountErrorMessage } from './accounts.service';

describe('AccountsService', () => {
  let service: AccountsService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(AccountsService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('liste les comptes avec la pagination', () => {
    service.listAccounts(2, 20).subscribe();

    const request = http.expectOne((r) => r.url === '/api/accounts');
    expect(request.request.method).toBe('GET');
    expect(request.request.params.get('page')).toBe('2');
    expect(request.request.params.get('size')).toBe('20');
    request.flush({ content: [], page: 2, size: 20, totalElements: 0, totalPages: 0 });
  });

  it('lit la vue de la banque', () => {
    service.getSummary().subscribe();

    const request = http.expectOne('/api/accounts/summary');
    expect(request.request.method).toBe('GET');
    request.flush({});
  });

  it('lit un compte et le clôture, en gérant lui-même les erreurs', () => {
    service.getAccount('a1').subscribe();
    const read = http.expectOne('/api/accounts/a1');
    expect(read.request.method).toBe('GET');
    expect(read.request.context.get(SILENT_ERRORS)).toBe(true);
    read.flush({});

    service.closeAccount('a1').subscribe();
    const close = http.expectOne('/api/accounts/a1/close');
    expect(close.request.method).toBe('POST');
    expect(close.request.context.get(SILENT_ERRORS)).toBe(true);
    close.flush({});
  });

  it("ouvre un compte avec la devise et le libellé, en gérant lui-même l'erreur", () => {
    service.openAccount({ currency: 'USD', label: 'Voyage' }).subscribe();

    const request = http.expectOne('/api/accounts');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ currency: 'USD', label: 'Voyage' });
    expect(request.request.context.get(SILENT_ERRORS)).toBe(true);
    request.flush({}, { status: 201, statusText: 'Created' });
  });
});

describe('getAccountErrorMessage', () => {
  function build(body: unknown): HttpErrorResponse {
    return new HttpErrorResponse({ status: 409, error: body });
  }

  it.each([
    [
      'CUSTOMER_NOT_READY',
      'Votre profil est en cours de création, réessayez dans quelques secondes.',
    ],
  ])('traduit %s', (code, message) => {
    expect(getAccountErrorMessage(build({ code, detail: 'autre' }), false)).toBe(message);
  });

  it.each([
    ['ACCOUNT_LIMIT_REACHED', false, 'Vous avez atteint la limite de 5 comptes ouverts.'],
    ['ACCOUNT_LIMIT_REACHED', true, 'Ce client a déjà 5 comptes ouverts.'],
    ['CUSTOMER_SUSPENDED', false, 'Votre profil est suspendu : contactez un conseiller.'],
    ['CUSTOMER_SUSPENDED', true, "Ce client est suspendu : aucune opération n'est possible."],
  ])('traduit %s selon le rôle (administrateur : %s)', (code, isAdmin, message) => {
    expect(getAccountErrorMessage(build({ code }), isAdmin)).toBe(message);
  });

  it('se replie sur le detail pour un code inconnu', () => {
    expect(
      getAccountErrorMessage(build({ code: 'AUTRE', detail: 'Détail du serveur.' }), false),
    ).toBe('Détail du serveur.');
  });

  it('renvoie un message générique sans détail exploitable', () => {
    expect(getAccountErrorMessage(build(null), false)).toBe(UNEXPECTED_ERROR_MESSAGE);
    expect(getAccountErrorMessage(new Error('x'), false)).toBe(UNEXPECTED_ERROR_MESSAGE);
  });
});
