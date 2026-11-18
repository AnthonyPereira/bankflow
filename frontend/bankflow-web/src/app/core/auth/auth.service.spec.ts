import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { firstValueFrom } from 'rxjs';
import { fakeJwt } from '../../testing/fake-jwt';
import { AuthService } from './auth.service';

const CREDENTIALS = { email: 'a@bankflow.test', password: 'un-mot-de-passe-solide' };
const UNAUTHORIZED = { status: 401, statusText: 'Unauthorized' };

describe('AuthService', () => {
  let auth: AuthService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    auth = TestBed.inject(AuthService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    vi.useRealTimers();
    http.verify();
  });

  it('ouvre une session client à la connexion', async () => {
    const login = firstValueFrom(auth.login(CREDENTIALS));

    http.expectOne('/api/auth/login').flush({ accessToken: fakeJwt() });
    await login;

    expect(auth.isAuthenticated()).toBe(true);
    expect(auth.isAdmin()).toBe(false);
    expect(auth.accessToken()).toBe(fakeJwt());
  });

  it('reconnaît un administrateur', async () => {
    const login = firstValueFrom(auth.login(CREDENTIALS));

    http.expectOne('/api/auth/login').flush({ accessToken: fakeJwt('ROLE_ADMIN') });
    await login;

    expect(auth.isAdmin()).toBe(true);
  });

  it("n'écrit jamais le jeton dans le stockage du navigateur", async () => {
    const login = firstValueFrom(auth.login(CREDENTIALS));

    http.expectOne('/api/auth/login').flush({ accessToken: fakeJwt() });
    await login;

    expect(localStorage.length).toBe(0);
    expect(sessionStorage.length).toBe(0);
  });

  it('partage un seul refresh entre les requêtes simultanées', async () => {
    const first = firstValueFrom(auth.refresh());
    const second = firstValueFrom(auth.refresh());

    http.expectOne('/api/auth/refresh').flush({ accessToken: fakeJwt() });

    expect(await first).toBe(fakeJwt());
    expect(await second).toBe(fakeJwt());
  });

  it('retente une fois un refresh refusé à cause d’un autre onglet', async () => {
    vi.useFakeTimers();
    const refresh = firstValueFrom(auth.refresh());

    http.expectOne('/api/auth/refresh').flush(null, UNAUTHORIZED);
    await vi.advanceTimersByTimeAsync(300);
    http.expectOne('/api/auth/refresh').flush({ accessToken: fakeJwt() });

    expect(await refresh).toBe(fakeJwt());
    expect(auth.isAuthenticated()).toBe(true);
  });

  it('ferme la session si le refresh reste refusé', async () => {
    vi.useFakeTimers();
    const refresh = firstValueFrom(auth.refresh()).catch((error: unknown) => error);

    http.expectOne('/api/auth/refresh').flush(null, UNAUTHORIZED);
    await vi.advanceTimersByTimeAsync(300);
    http.expectOne('/api/auth/refresh').flush(null, UNAUTHORIZED);
    await refresh;

    expect(auth.isAuthenticated()).toBe(false);
  });

  it('démarre sans session quand aucune session ne peut être restaurée', async () => {
    const restore = auth.restoreSession();

    http.expectOne('/api/auth/refresh').flush(null, { status: 503, statusText: 'Unavailable' });

    await expect(restore).resolves.toBeUndefined();
    expect(auth.isAuthenticated()).toBe(false);
  });

  it('ferme la session locale même si la déconnexion échoue', async () => {
    const login = firstValueFrom(auth.login(CREDENTIALS));
    http.expectOne('/api/auth/login').flush({ accessToken: fakeJwt() });
    await login;

    const logout = firstValueFrom(auth.logout());
    http.expectOne('/api/auth/logout').flush(null, { status: 500, statusText: 'Error' });
    await logout;

    expect(auth.isAuthenticated()).toBe(false);
  });
});
