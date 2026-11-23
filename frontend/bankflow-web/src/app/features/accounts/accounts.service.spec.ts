import { HttpErrorResponse, provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { SILENT_ERRORS } from '../../core/http/api';
import {
  AccountsService,
  getAccountErrorMessage,
  UNEXPECTED_ACCOUNT_ERROR,
} from './accounts.service';

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
    ['ACCOUNT_LIMIT_REACHED', 'Vous avez atteint la limite de 5 comptes ouverts.'],
    ['CUSTOMER_SUSPENDED', 'Votre profil est suspendu : contactez un conseiller.'],
  ])('traduit %s', (code, message) => {
    expect(getAccountErrorMessage(build({ code, detail: 'autre' }))).toBe(message);
  });

  it('se replie sur le detail pour un code inconnu', () => {
    expect(getAccountErrorMessage(build({ code: 'AUTRE', detail: 'Détail du serveur.' }))).toBe(
      'Détail du serveur.',
    );
  });

  it('renvoie un message générique sans détail exploitable', () => {
    expect(getAccountErrorMessage(build(null))).toBe(UNEXPECTED_ACCOUNT_ERROR);
    expect(getAccountErrorMessage(new Error('x'))).toBe(UNEXPECTED_ACCOUNT_ERROR);
  });
});
