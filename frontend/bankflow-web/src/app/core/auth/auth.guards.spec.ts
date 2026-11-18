import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import {
  ActivatedRouteSnapshot,
  CanActivateFn,
  provideRouter,
  Router,
  RouterStateSnapshot,
} from '@angular/router';
import { adminGuard, authGuard, guestGuard } from './auth.guards';
import { AuthService } from './auth.service';

describe('guards', () => {
  const isAuthenticated = signal(false);
  const isAdmin = signal(false);

  beforeEach(() => {
    isAuthenticated.set(false);
    isAdmin.set(false);
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        { provide: AuthService, useValue: { isAuthenticated, isAdmin } },
      ],
    });
  });

  function run(guard: CanActivateFn) {
    const result = TestBed.runInInjectionContext(() =>
      guard({} as ActivatedRouteSnapshot, { url: '/cible' } as RouterStateSnapshot),
    );
    return result === true ? true : TestBed.inject(Router).serializeUrl(result as never);
  }

  it('authGuard renvoie un visiteur vers la connexion en gardant sa destination', () => {
    expect(run(authGuard)).toBe('/connexion?returnUrl=%2Fcible');
  });

  it('authGuard laisse passer un utilisateur connecté', () => {
    isAuthenticated.set(true);

    expect(run(authGuard)).toBe(true);
  });

  it('adminGuard renvoie un visiteur vers la connexion', () => {
    expect(run(adminGuard)).toBe('/connexion?returnUrl=%2Fcible');
  });

  it('adminGuard renvoie un client vers son tableau de bord', () => {
    isAuthenticated.set(true);

    expect(run(adminGuard)).toBe('/tableau-de-bord');
  });

  it('adminGuard laisse passer un administrateur', () => {
    isAuthenticated.set(true);
    isAdmin.set(true);

    expect(run(adminGuard)).toBe(true);
  });

  it('guestGuard renvoie un utilisateur connecté vers son tableau de bord', () => {
    isAuthenticated.set(true);

    expect(run(guestGuard)).toBe('/tableau-de-bord');
  });

  it('guestGuard laisse passer un visiteur', () => {
    expect(run(guestGuard)).toBe(true);
  });
});
