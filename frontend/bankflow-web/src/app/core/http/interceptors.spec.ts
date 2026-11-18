import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { fakeJwt } from '../../testing/fake-jwt';
import { authInterceptor } from '../auth/auth.interceptor';
import { AuthService } from '../auth/auth.service';
import { NotificationUiService } from '../notifications/notification-ui.service';
import { correlationIdInterceptor } from './correlation-id.interceptor';
import { errorInterceptor } from './error.interceptor';

const OLD_TOKEN = fakeJwt('ROLE_CLIENT', 'ancien');
const NEW_TOKEN = fakeJwt('ROLE_CLIENT', 'nouveau');
const UNAUTHORIZED = { status: 401, statusText: 'Unauthorized' };

describe('intercepteurs HTTP', () => {
  let client: HttpClient;
  let http: HttpTestingController;
  let auth: AuthService;
  let router: Router;
  const notifications = { error: vi.fn(), info: vi.fn() };

  beforeEach(async () => {
    notifications.error.mockClear();
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
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
    expect(notifications.error).not.toHaveBeenCalled();
  });

  it('renvoie vers la connexion quand la session ne peut plus être renouvelée', async () => {
    vi.useFakeTimers();
    const navigate = vi.spyOn(router, 'navigate').mockResolvedValue(true);
    const response = firstValueFrom(client.get('/api/accounts')).catch((e: unknown) => e);

    http.expectOne('/api/accounts').flush(null, UNAUTHORIZED);
    http.expectOne('/api/auth/refresh').flush(null, UNAUTHORIZED);
    await vi.advanceTimersByTimeAsync(300);
    http.expectOne('/api/auth/refresh').flush(null, UNAUTHORIZED);
    await response;

    expect(auth.isAuthenticated()).toBe(false);
    expect(navigate).toHaveBeenCalledWith(['/connexion'], { queryParams: { returnUrl: '/' } });
    expect(notifications.error).toHaveBeenCalledTimes(1);
  });

  it('laisse le formulaire traiter un mot de passe incorrect', async () => {
    const navigate = vi.spyOn(router, 'navigate');
    const login = firstValueFrom(auth.login({ email: 'a@bankflow.test', password: 'faux' })).catch(
      (e: unknown) => e,
    );

    http.expectOne('/api/auth/login').flush(null, UNAUTHORIZED);
    await login;

    expect(navigate).not.toHaveBeenCalled();
    expect(notifications.error).not.toHaveBeenCalled();
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

    expect(notifications.error).toHaveBeenCalledWith(message);
  });

  it('laisse les erreurs métier aux écrans', async () => {
    const response = firstValueFrom(client.get('/api/accounts')).catch((e: unknown) => e);

    http.expectOne('/api/accounts').flush(null, { status: 409, statusText: 'Conflict' });
    await response;

    expect(notifications.error).not.toHaveBeenCalled();
  });
});
