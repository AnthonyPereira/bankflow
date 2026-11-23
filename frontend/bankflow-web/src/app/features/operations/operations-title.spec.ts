import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, Route, RouterStateSnapshot } from '@angular/router';
import { routes } from '../../app.routes';
import { AuthService } from '../../core/auth/auth.service';

describe('Titre de la route des opérations', () => {
  function resolveTitle(admin: boolean): unknown {
    TestBed.configureTestingModule({
      providers: [{ provide: AuthService, useValue: { isAdmin: signal(admin) } }],
    });
    const shell = routes.find((route) => route.children) as Route;
    const operations = shell.children?.find((route) => route.path === 'operations') as Route;
    const title = operations.title as (
      route: ActivatedRouteSnapshot,
      state: RouterStateSnapshot,
    ) => string;
    return TestBed.runInInjectionContext(() =>
      title({} as ActivatedRouteSnapshot, {} as RouterStateSnapshot),
    );
  }

  it('affiche « Mes opérations » dans l’onglet pour un client', () => {
    expect(resolveTitle(false)).toBe('Mes opérations · BankFlow');
  });

  it('affiche « Toutes les opérations » dans l’onglet pour un administrateur', () => {
    expect(resolveTitle(true)).toBe('Toutes les opérations · BankFlow');
  });
});
