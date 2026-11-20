import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { createFakeJwt } from '../../testing/fake-jwt';
import { SILENT_ERRORS } from '../http/api';
import { authGuard, guestGuard } from './auth.guards';
import { AuthService } from './auth.service';

const CREDENTIALS = { email: 'a@bankflow.test', password: 'un-mot-de-passe-solide' };
const UNAUTHORIZED = { status: 401, statusText: 'Unauthorized' };
const REFRESH_URL = '/api/auth/refresh';

@Component({ template: '' })
class StubPage {}

/** Les vraies règles de navigation : seuls les composants sont simulés. */
const ROUTES = [
  { path: 'connexion', canActivate: [guestGuard], component: StubPage },
  { path: 'inscription', canActivate: [guestGuard], component: StubPage },
  { path: 'tableau-de-bord', canActivate: [authGuard], component: StubPage },
];

/** `BroadcastChannel` simulé : un message part vers les autres canaux du même nom, jamais vers soi. */
class FakeBroadcastChannel {
  static readonly instances: FakeBroadcastChannel[] = [];
  onmessage: ((event: MessageEvent<unknown>) => void) | null = null;
  isClosed = false;

  constructor(readonly name: string) {
    FakeBroadcastChannel.instances.push(this);
  }

  postMessage(data: unknown): void {
    FakeBroadcastChannel.instances
      .filter((other) => other !== this && other.name === this.name && !other.isClosed)
      .forEach((other) => other.onmessage?.({ data } as MessageEvent<unknown>));
  }

  close(): void {
    this.isClosed = true;
  }
}

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
    FakeBroadcastChannel.instances.length = 0;
    vi.stubGlobal('BroadcastChannel', FakeBroadcastChannel);
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter(ROUTES)],
    });
    auth = TestBed.inject(AuthService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    vi.useRealTimers();
    vi.unstubAllGlobals();
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

  it('demande les informations du compte sans message global en cas d’erreur', () => {
    auth.fetchCurrentUser().subscribe();

    const request = http.expectOne('/api/auth/me').request;

    expect(request.context.get(SILENT_ERRORS)).toBe(true);
    http.match('/api/auth/me').forEach((pending) => pending.flush({}));
  });

  it('renvoie une liste vide si les comptes de démonstration sont indisponibles', async () => {
    const accounts = firstValueFrom(auth.fetchDemoAccounts());

    http.expectOne('/api/auth/demo-accounts').flush(null, { status: 503, statusText: 'Error' });

    await expect(accounts).resolves.toEqual([]);
  });

  async function login(service: AuthService = auth): Promise<void> {
    const pending = firstValueFrom(service.login(CREDENTIALS));
    http.expectOne('/api/auth/login').flush({ accessToken: createFakeJwt() });
    await pending;
  }

  it('confirme la déconnexion acceptée par le serveur', async () => {
    await login();

    const logout = firstValueFrom(auth.logout());
    http.expectOne('/api/auth/logout').flush(null, { status: 204, statusText: 'No Content' });

    await expect(logout).resolves.toBe(true);
    expect(auth.isAuthenticated()).toBe(false);
  });

  it('ferme la session locale même si la déconnexion échoue, sans la confirmer', async () => {
    await login();

    const logout = firstValueFrom(auth.logout());
    http.expectOne('/api/auth/logout').flush(null, { status: 500, statusText: 'Error' });

    await expect(logout).resolves.toBe(false);
    expect(auth.isAuthenticated()).toBe(false);
  });

  describe('entre onglets', () => {
    it('ferme la session des autres onglets et les renvoie vers la connexion', async () => {
      const otherTab = TestBed.runInInjectionContext(() => new AuthService());
      await login();
      await login(otherTab);
      const navigate = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);

      const logout = firstValueFrom(auth.logout());
      http.expectOne('/api/auth/logout').flush(null, { status: 500, statusText: 'Error' });
      await logout;

      expect(otherTab.isAuthenticated()).toBe(false);
      expect(navigate).toHaveBeenCalledWith('/connexion');
    });

    it('ignore les messages inconnus', async () => {
      const otherTab = TestBed.runInInjectionContext(() => new AuthService());
      await login(otherTab);

      new FakeBroadcastChannel('bankflow-auth').postMessage('autre-chose');

      expect(otherTab.isAuthenticated()).toBe(true);
    });

    it('ne quitte pas la page d’inscription d’un visiteur', async () => {
      const router = TestBed.inject(Router);
      await router.navigateByUrl('/inscription');
      const navigate = vi.spyOn(router, 'navigateByUrl');

      new FakeBroadcastChannel('bankflow-auth').postMessage('logout');

      expect(navigate).not.toHaveBeenCalled();
    });

    it('ferme le canal avec l’application', () => {
      TestBed.resetTestingModule();

      expect(FakeBroadcastChannel.instances[0].isClosed).toBe(true);
    });

    it('fonctionne sans BroadcastChannel', async () => {
      vi.stubGlobal('BroadcastChannel', undefined);
      const isolatedTab = TestBed.runInInjectionContext(() => new AuthService());
      await login(isolatedTab);

      const logout = firstValueFrom(isolatedTab.logout());
      http.expectOne('/api/auth/logout').flush(null, { status: 204, statusText: 'No Content' });

      await expect(logout).resolves.toBe(true);
      expect(isolatedTab.isAuthenticated()).toBe(false);
    });
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

    it('échoue si le refresh reste refusé', async () => {
      vi.useFakeTimers();
      const refresh = firstValueFrom(auth.refresh()).catch((error: unknown) => error);

      http.expectOne('/api/auth/refresh').flush(null, UNAUTHORIZED);
      await vi.advanceTimersByTimeAsync(300);
      http.expectOne('/api/auth/refresh').flush(null, UNAUTHORIZED);

      expect(await refresh).toMatchObject({ status: 401 });
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

    /** Un autre onglet garde le verrou : la restauration abandonne au bout de 5 s. */
    async function restoreWhileOtherTabHoldsLock(): Promise<() => void> {
      let releaseOtherTab!: () => void;
      void locks.request(
        'bankflow-refresh',
        {},
        () => new Promise<void>((release) => (releaseOtherTab = release)),
      );
      const restore = auth.restoreSession();
      await vi.advanceTimersByTimeAsync(5000);
      await expect(restore).resolves.toBeUndefined();
      expect(auth.isAuthenticated()).toBe(false);
      http.expectNone(REFRESH_URL);
      return releaseOtherTab;
    }

    it('fait attendre la navigation initiale jusqu’à la fin du refresh en cours', async () => {
      const releaseOtherTab = await restoreWhileOtherTabHoldsLock();
      const router = TestBed.inject(Router);

      // Le guard ne tranche pas pendant que le refresh attend le verrou.
      const navigation = router.navigateByUrl('/tableau-de-bord');
      await vi.advanceTimersByTimeAsync(1000);
      releaseOtherTab();
      await vi.advanceTimersByTimeAsync(0);
      http.expectOne(REFRESH_URL).flush({ accessToken: createFakeJwt() });

      await expect(navigation).resolves.toBe(true);
      expect(router.url).toBe('/tableau-de-bord');
      expect(auth.getAccessToken()).toBe(createFakeJwt());
    });

    it('quitte la connexion pour la page demandée quand le refresh aboutit trop tard', async () => {
      const releaseOtherTab = await restoreWhileOtherTabHoldsLock();
      const router = TestBed.inject(Router);

      // Le guard finit par renoncer : le visiteur arrive sur la connexion.
      void router.navigateByUrl('/tableau-de-bord');
      await vi.advanceTimersByTimeAsync(10_000);
      expect(router.url).toBe('/connexion?returnUrl=%2Ftableau-de-bord');

      releaseOtherTab();
      await vi.advanceTimersByTimeAsync(0);
      http.expectOne(REFRESH_URL).flush({ accessToken: createFakeJwt() });
      await vi.advanceTimersByTimeAsync(0);

      expect(auth.isAuthenticated()).toBe(true);
      expect(router.url).toBe('/tableau-de-bord');
    });

    it('ignore une page de retour externe après un refresh tardif', async () => {
      const releaseOtherTab = await restoreWhileOtherTabHoldsLock();
      const router = TestBed.inject(Router);
      void router.navigateByUrl('/connexion?returnUrl=%2F%2Fsite-externe.example');
      await vi.advanceTimersByTimeAsync(5000);

      releaseOtherTab();
      await vi.advanceTimersByTimeAsync(0);
      http.expectOne(REFRESH_URL).flush({ accessToken: createFakeJwt() });
      await vi.advanceTimersByTimeAsync(0);

      expect(router.url).toBe('/tableau-de-bord');
    });
  });
});
