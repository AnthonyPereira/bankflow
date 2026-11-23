import { HttpClient, HttpContext, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { createFakeJwt } from '../../testing/fake-jwt';
import { authInterceptor } from '../auth/auth.interceptor';
import { AuthService } from '../auth/auth.service';
import { NotificationUiService } from '../notifications/notification-ui.service';
import { SILENT_ERRORS } from './api';
import { correlationIdInterceptor } from './correlation-id.interceptor';
import { errorInterceptor } from './error.interceptor';

const OLD_TOKEN = createFakeJwt('ROLE_CLIENT', 'ancien');
const NEW_TOKEN = createFakeJwt('ROLE_CLIENT', 'nouveau');
const UNAUTHORIZED = { status: 401, statusText: 'Unauthorized' };

@Component({ template: '' })
class StubPage {}

describe('intercepteurs HTTP', () => {
  let client: HttpClient;
  let http: HttpTestingController;
  let auth: AuthService;
  let router: Router;
  const notifications = { showError: vi.fn() };

  beforeEach(async () => {
    notifications.showError.mockClear();
    TestBed.configureTestingModule({
      providers: [
        provideRouter([
          { path: 'comptes', component: StubPage },
          { path: 'connexion', component: StubPage },
        ]),
        provideHttpClient(
          withInterceptors([correlationIdInterceptor, errorInterceptor, authInterceptor]),
        ),
        provideHttpClientTesting(),
        { provide: NotificationUiService, useValue: notifications },
      ],
    });
    client = TestBed.inject(HttpClient);
    http = TestBed.inject(HttpTestingController);
    auth = TestBed.inject(AuthService);
    router = TestBed.inject(Router);

    const login = firstValueFrom(auth.login({ email: 'a@bankflow.test', password: 'secret' }));
    http.expectOne('/api/auth/login').flush({ accessToken: OLD_TOKEN });
    await login;
  });

  afterEach(() => {
    vi.useRealTimers();
    http.verify();
  });

  it('ajoute le JWT et un identifiant de corrélation aux appels API', () => {
    client.get('/api/accounts').subscribe();

    const request = http.expectOne('/api/accounts').request;

    expect(request.headers.get('Authorization')).toBe(`Bearer ${OLD_TOKEN}`);
    expect(request.headers.get('X-Correlation-Id')).toMatch(/^[0-9a-f-]{36}$/);
    http.match('/api/accounts').forEach((pending) => pending.flush({}));
  });

  it("n'envoie jamais le JWT à un autre domaine", () => {
    client.get('https://exemple.test/donnees').subscribe();

    const pending = http.expectOne('https://exemple.test/donnees');

    expect(pending.request.headers.has('Authorization')).toBe(false);
    expect(pending.request.headers.has('X-Correlation-Id')).toBe(false);
    pending.flush({});
  });

  it('rafraîchit le jeton puis rejoue la requête après un 401', async () => {
    const response = firstValueFrom(client.get('/api/accounts'));

    http.expectOne('/api/accounts').flush(null, UNAUTHORIZED);
    http.expectOne('/api/auth/refresh').flush({ accessToken: NEW_TOKEN });
    const replay = http.expectOne('/api/accounts');
    replay.flush({ ok: true });

    expect(replay.request.headers.get('Authorization')).toBe(`Bearer ${NEW_TOKEN}`);
    await expect(response).resolves.toEqual({ ok: true });
    expect(notifications.showError).not.toHaveBeenCalled();
  });

  it('réutilise le jeton déjà renouvelé par une autre requête', async () => {
    const first = firstValueFrom(client.get('/api/accounts'));
    const second = firstValueFrom(client.get('/api/transfers'));

    // La première requête refusée déclenche le refresh et se termine avant la seconde.
    http.expectOne('/api/accounts').flush(null, UNAUTHORIZED);
    http.expectOne('/api/auth/refresh').flush({ accessToken: NEW_TOKEN });
    http.expectOne('/api/accounts').flush({ ok: 1 });
    await first;

    // La seconde, partie avec l'ancien jeton, est rejouée sans nouveau refresh.
    http.expectOne('/api/transfers').flush(null, UNAUTHORIZED);
    http.expectNone('/api/auth/refresh');
    const replay = http.expectOne('/api/transfers');
    replay.flush({ ok: 2 });

    expect(replay.request.headers.get('Authorization')).toBe(`Bearer ${NEW_TOKEN}`);
    await expect(second).resolves.toEqual({ ok: 2 });
  });

  it('garde la session ouverte quand le refresh échoue pour une autre raison', async () => {
    const navigate = vi.spyOn(router, 'navigate');
    const response = firstValueFrom(client.get('/api/accounts')).catch((e: unknown) => e);

    http.expectOne('/api/accounts').flush(null, UNAUTHORIZED);
    http.expectOne('/api/auth/refresh').flush(null, { status: 503, statusText: 'Unavailable' });

    expect(await response).toMatchObject({ status: 503 });
    expect(auth.isAuthenticated()).toBe(true);
    expect(navigate).not.toHaveBeenCalled();
    expect(notifications.showError).toHaveBeenCalledWith(
      'Une erreur est survenue. Réessayez plus tard.',
    );
  });

  /** Le refresh est refusé deux fois : sans Web Locks, le service retente une fois. */
  async function rejectRefresh(): Promise<void> {
    http.expectOne('/api/auth/refresh').flush(null, UNAUTHORIZED);
    await vi.advanceTimersByTimeAsync(300);
    http.expectOne('/api/auth/refresh').flush(null, UNAUTHORIZED);
  }

  it('renvoie vers la connexion en gardant la page en cours', async () => {
    await router.navigateByUrl('/comptes?onglet=epargne');
    vi.useFakeTimers();
    const navigate = vi.spyOn(router, 'navigate').mockResolvedValue(true);
    const response = firstValueFrom(client.get('/api/accounts')).catch((e: unknown) => e);

    http.expectOne('/api/accounts').flush(null, UNAUTHORIZED);
    await rejectRefresh();
    await response;

    expect(auth.isAuthenticated()).toBe(false);
    expect(navigate).toHaveBeenCalledWith(['/connexion'], {
      queryParams: { returnUrl: '/comptes?onglet=epargne' },
    });
    expect(notifications.showError).toHaveBeenCalledExactlyOnceWith(
      'Votre session a expiré. Veuillez vous reconnecter.',
    );
  });

  it('ne traite qu’une fois plusieurs requêtes refusées en même temps', async () => {
    await router.navigateByUrl('/comptes');
    vi.useFakeTimers();
    const navigate = vi.spyOn(router, 'navigate').mockResolvedValue(true);
    const first = firstValueFrom(client.get('/api/accounts')).catch((e: unknown) => e);
    const second = firstValueFrom(client.get('/api/transfers')).catch((e: unknown) => e);

    http.expectOne('/api/accounts').flush(null, UNAUTHORIZED);
    http.expectOne('/api/transfers').flush(null, UNAUTHORIZED);
    await rejectRefresh();
    await Promise.all([first, second]);

    expect(navigate).toHaveBeenCalledExactlyOnceWith(['/connexion'], {
      queryParams: { returnUrl: '/comptes' },
    });
    expect(notifications.showError).toHaveBeenCalledTimes(1);
  });

  it('ne fait rien pour un 401 reçu sur la page de connexion', async () => {
    await router.navigateByUrl('/connexion?returnUrl=%2Fcomptes');
    const navigate = vi.spyOn(router, 'navigate');
    vi.useFakeTimers();
    const response = firstValueFrom(client.get('/api/accounts')).catch((e: unknown) => e);

    http.expectOne('/api/accounts').flush(null, UNAUTHORIZED);
    await rejectRefresh();
    await response;

    expect(navigate).not.toHaveBeenCalled();
    expect(notifications.showError).not.toHaveBeenCalled();
  });

  it('ferme aussi la session sur une requête silencieuse', async () => {
    vi.useFakeTimers();
    const navigate = vi.spyOn(router, 'navigate').mockResolvedValue(true);
    const context = new HttpContext().set(SILENT_ERRORS, true);
    const response = firstValueFrom(client.get('/api/auth/me', { context })).catch(
      (e: unknown) => e,
    );

    http.expectOne('/api/auth/me').flush(null, UNAUTHORIZED);
    await rejectRefresh();
    await response;

    expect(auth.isAuthenticated()).toBe(false);
    expect(navigate).toHaveBeenCalledTimes(1);
  });

  it("n'affiche aucun message pour une requête silencieuse", async () => {
    const context = new HttpContext().set(SILENT_ERRORS, true);
    const response = firstValueFrom(client.get('/api/auth/me', { context })).catch(
      (e: unknown) => e,
    );

    http.expectOne('/api/auth/me').flush(null, { status: 503, statusText: 'Unavailable' });
    await response;

    expect(notifications.showError).not.toHaveBeenCalled();
  });

  it('signale un service injoignable', async () => {
    const response = firstValueFrom(client.get('/api/accounts')).catch((e: unknown) => e);

    http.expectOne('/api/accounts').error(new ProgressEvent('error'));

    expect(await response).toMatchObject({ status: 0 });
    expect(notifications.showError).toHaveBeenCalledWith(
      'Le service est injoignable. Vérifiez votre connexion.',
    );
  });

  it('laisse le formulaire traiter un mot de passe incorrect', async () => {
    const navigate = vi.spyOn(router, 'navigate');
    const login = firstValueFrom(auth.login({ email: 'a@bankflow.test', password: 'faux' })).catch(
      (e: unknown) => e,
    );

    http.expectOne('/api/auth/login').flush(null, UNAUTHORIZED);
    await login;

    expect(navigate).not.toHaveBeenCalled();
    expect(notifications.showError).not.toHaveBeenCalled();
    expect(auth.isAuthenticated()).toBe(true);
  });

  it.each([
    [403, "Vous n'avez pas accès à cette ressource."],
    [429, 'Trop de tentatives. Réessayez dans quelques instants.'],
    [503, 'Une erreur est survenue. Réessayez plus tard.'],
  ])('affiche un message pour une erreur %i', async (status, message) => {
    const response = firstValueFrom(client.get('/api/accounts')).catch((e: unknown) => e);

    http.expectOne('/api/accounts').flush(null, { status, statusText: 'Erreur' });
    await response;

    expect(notifications.showError).toHaveBeenCalledWith(message);
  });

  it('laisse les erreurs métier aux écrans', async () => {
    const response = firstValueFrom(client.get('/api/accounts')).catch((e: unknown) => e);

    http.expectOne('/api/accounts').flush(null, { status: 409, statusText: 'Conflict' });
    await response;

    expect(notifications.showError).not.toHaveBeenCalled();
  });
});
