import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import {
  ActivatedRouteSnapshot,
  CanActivateFn,
  provideRouter,
  Router,
  RouterStateSnapshot,
} from '@angular/router';
import { firstValueFrom, Observable, of, Subject } from 'rxjs';
import { adminGuard, authGuard, clientGuard, guestGuard } from './auth.guards';
import { AuthService } from './auth.service';

describe('guards', () => {
  const isAuthenticated = signal(false);
  const isAdmin = signal(false);
  let pendingRefresh: Observable<void>;

  beforeEach(() => {
    isAuthenticated.set(false);
    isAdmin.set(false);
    pendingRefresh = of(undefined);
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        {
          provide: AuthService,
          useValue: { isAuthenticated, isAdmin, waitForPendingRefresh: () => pendingRefresh },
        },
      ],
    });
  });

  function run(guard: CanActivateFn) {
    const result = TestBed.runInInjectionContext(() =>
      guard({} as ActivatedRouteSnapshot, { url: '/cible' } as RouterStateSnapshot),
    );
    return firstValueFrom(result as Observable<unknown>).then((decision) =>
      decision === true ? true : TestBed.inject(Router).serializeUrl(decision as never),
    );
  }

  it('authGuard renvoie un visiteur vers la connexion en gardant sa destination', async () => {
    expect(await run(authGuard)).toBe('/connexion?returnUrl=%2Fcible');
  });

  it('authGuard laisse passer un utilisateur connecté', async () => {
    isAuthenticated.set(true);

    expect(await run(authGuard)).toBe(true);
  });

  it('adminGuard renvoie un visiteur vers la connexion', async () => {
    expect(await run(adminGuard)).toBe('/connexion?returnUrl=%2Fcible');
  });

  it('adminGuard renvoie un client vers son tableau de bord', async () => {
    isAuthenticated.set(true);

    expect(await run(adminGuard)).toBe('/tableau-de-bord');
  });

  it('adminGuard laisse passer un administrateur', async () => {
    isAuthenticated.set(true);
    isAdmin.set(true);

    expect(await run(adminGuard)).toBe(true);
  });

  it('clientGuard renvoie un visiteur vers la connexion', async () => {
    expect(await run(clientGuard)).toBe('/connexion?returnUrl=%2Fcible');
  });

  it('clientGuard renvoie un administrateur vers son tableau de bord', async () => {
    isAuthenticated.set(true);
    isAdmin.set(true);

    expect(await run(clientGuard)).toBe('/tableau-de-bord');
  });

  it('clientGuard laisse passer un client', async () => {
    isAuthenticated.set(true);

    expect(await run(clientGuard)).toBe(true);
  });

  it('guestGuard renvoie un utilisateur connecté vers son tableau de bord', async () => {
    isAuthenticated.set(true);

    expect(await run(guestGuard)).toBe('/tableau-de-bord');
  });

  it('guestGuard laisse passer un visiteur', async () => {
    expect(await run(guestGuard)).toBe(true);
  });

  it.each([
    ['authGuard', authGuard, true],
    ['adminGuard', adminGuard, '/tableau-de-bord'],
    ['clientGuard', clientGuard, true],
    ['guestGuard', guestGuard, '/tableau-de-bord'],
  ])('%s attend la fin du refresh en cours avant de décider', async (_name, guard, expected) => {
    const refresh = new Subject<void>();
    pendingRefresh = refresh;
    let decision: unknown = 'en attente';
    void run(guard).then((result) => (decision = result));
    await Promise.resolve();

    expect(decision).toBe('en attente');

    // Le refresh aboutit : la session est ouverte avant que le guard ne tranche.
    isAuthenticated.set(true);
    refresh.next();
    refresh.complete();
    await Promise.resolve();
    await Promise.resolve();

    expect(decision).toBe(expected);
  });
});
