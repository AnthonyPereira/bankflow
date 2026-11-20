import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { firstValueFrom } from 'rxjs';
import { createFakeJwt } from '../../testing/fake-jwt';
import { AuthService } from './auth.service';

const CREDENTIALS = { email: 'a@bankflow.test', password: 'un-mot-de-passe-solide' };
const UNAUTHORIZED = { status: 401, statusText: 'Unauthorized' };
const REFRESH_URL = '/api/auth/refresh';

/** Verrou Web Locks simulé : les demandes passent l'une après l'autre, comme entre onglets. */
function createFakeLockManager() {
  let queue = Promise.resolve();
  const request = vi.fn((_name: string, _options: LockOptions, callback: () => Promise<void>) => {
    const granted = queue.then(() => callback());
    queue = granted.catch(() => undefined);
    return granted;
  });
  return { request };
}

function setLockManager(locks: unknown): void {
  Object.defineProperty(navigator, 'locks', { value: locks, configurable: true });
}

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
    Reflect.deleteProperty(navigator, 'locks');
    http.verify();
  });

  it('ouvre une session client à la connexion', async () => {
    const login = firstValueFrom(auth.login(CREDENTIALS));

    http.expectOne('/api/auth/login').flush({ accessToken: createFakeJwt() });
    await login;

    expect(auth.isAuthenticated()).toBe(true);
    expect(auth.isAdmin()).toBe(false);
    expect(auth.getAccessToken()).toBe(createFakeJwt());
  });

  it('reconnaît un administrateur', async () => {
    const login = firstValueFrom(auth.login(CREDENTIALS));

    http.expectOne('/api/auth/login').flush({ accessToken: createFakeJwt('ROLE_ADMIN') });
    await login;

    expect(auth.isAdmin()).toBe(true);
  });

  it("n'écrit jamais le jeton dans le stockage du navigateur", async () => {
    const login = firstValueFrom(auth.login(CREDENTIALS));

    http.expectOne('/api/auth/login').flush({ accessToken: createFakeJwt() });
    await login;

    expect(localStorage.length).toBe(0);
    expect(sessionStorage.length).toBe(0);
  });

  it('partage un seul refresh entre les requêtes simultanées', async () => {
    const first = firstValueFrom(auth.refresh());
    const second = firstValueFrom(auth.refresh());

    http.expectOne('/api/auth/refresh').flush({ accessToken: createFakeJwt() });

    expect(await first).toBe(createFakeJwt());
    expect(await second).toBe(createFakeJwt());
  });

  it('démarre sans session quand aucune session ne peut être restaurée', async () => {
    const restore = auth.restoreSession();

    http.expectOne('/api/auth/refresh').flush(null, { status: 503, statusText: 'Unavailable' });

    await expect(restore).resolves.toBeUndefined();
    expect(auth.isAuthenticated()).toBe(false);
  });

  it('ferme la session locale même si la déconnexion échoue', async () => {
    const login = firstValueFrom(auth.login(CREDENTIALS));
    http.expectOne('/api/auth/login').flush({ accessToken: createFakeJwt() });
    await login;

    const logout = firstValueFrom(auth.logout());
    http.expectOne('/api/auth/logout').flush(null, { status: 500, statusText: 'Error' });
    await logout;

    expect(auth.isAuthenticated()).toBe(false);
  });

  describe('sans Web Locks', () => {
    beforeEach(() => {
      setLockManager(undefined);
    });

    it('retente une fois un refresh refusé à cause d’un autre onglet', async () => {
      vi.useFakeTimers();
      const refresh = firstValueFrom(auth.refresh());

      http.expectOne('/api/auth/refresh').flush(null, UNAUTHORIZED);
      await vi.advanceTimersByTimeAsync(300);
      http.expectOne('/api/auth/refresh').flush({ accessToken: createFakeJwt() });

      expect(await refresh).toBe(createFakeJwt());
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
  });

  describe('avec Web Locks', () => {
    let locks: ReturnType<typeof createFakeLockManager>;

    beforeEach(() => {
      vi.useFakeTimers();
      locks = createFakeLockManager();
      setLockManager(locks);
    });

    it("ne fait qu'un appel pour un visiteur sans session", async () => {
      const restore = auth.restoreSession();
      await vi.advanceTimersByTimeAsync(0);

      http.expectOne(REFRESH_URL).flush(null, UNAUTHORIZED);
      await expect(restore).resolves.toBeUndefined();
      await vi.advanceTimersByTimeAsync(1000);

      http.expectNone(REFRESH_URL);
      expect(locks.request).toHaveBeenCalledTimes(1);
      expect(locks.request.mock.calls[0][0]).toBe('bankflow-refresh');
      expect(auth.isAuthenticated()).toBe(false);
    });

    it('fait passer les onglets chacun à son tour, avec le cookie renouvelé', async () => {
      const otherTab = TestBed.runInInjectionContext(() => new AuthService());
      const first = firstValueFrom(auth.refresh());
      const second = firstValueFrom(otherTab.refresh());
      await vi.advanceTimersByTimeAsync(0);

      // Le second onglet attend que le premier ait reçu son nouveau cookie.
      http.expectOne(REFRESH_URL).flush({ accessToken: createFakeJwt('ROLE_CLIENT', 'onglet-1') });
      await vi.advanceTimersByTimeAsync(0);
      http.expectOne(REFRESH_URL).flush({ accessToken: createFakeJwt('ROLE_CLIENT', 'onglet-2') });

      expect(await first).toBe(createFakeJwt('ROLE_CLIENT', 'onglet-1'));
      expect(await second).toBe(createFakeJwt('ROLE_CLIENT', 'onglet-2'));
      expect(otherTab.isAuthenticated()).toBe(true);
    });

    it('libère le verrou quand le refresh échoue', async () => {
      const otherTab = TestBed.runInInjectionContext(() => new AuthService());
      const first = firstValueFrom(auth.refresh()).catch((error: unknown) => error);
      const second = firstValueFrom(otherTab.refresh());
      await vi.advanceTimersByTimeAsync(0);

      http.expectOne(REFRESH_URL).flush(null, { status: 503, statusText: 'Unavailable' });
      await first;
      await vi.advanceTimersByTimeAsync(0);
      http.expectOne(REFRESH_URL).flush({ accessToken: createFakeJwt() });

      expect(await second).toBe(createFakeJwt());
    });

    it('garde la session obtenue après le délai de restauration si un autre onglet tarde', async () => {
      let releaseOtherTab!: () => void;
      void locks.request(
        'bankflow-refresh',
        {},
        () => new Promise<void>((release) => (releaseOtherTab = release)),
      );

      // L'application démarre sans attendre plus de 5 s l'autre onglet.
      const restore = auth.restoreSession();
      await vi.advanceTimersByTimeAsync(5000);
      await expect(restore).resolves.toBeUndefined();
      expect(auth.isAuthenticated()).toBe(false);
      http.expectNone(REFRESH_URL);

      // Une navigation pendant l'attente rejoint le même refresh, toujours en attente du verrou.
      const navigation = firstValueFrom(auth.refresh());
      releaseOtherTab();
      await vi.advanceTimersByTimeAsync(0);
      http.expectOne(REFRESH_URL).flush({ accessToken: createFakeJwt() });

      expect(await navigation).toBe(createFakeJwt());
      expect(auth.isAuthenticated()).toBe(true);
      expect(auth.getAccessToken()).toBe(createFakeJwt());
    });
  });
});
