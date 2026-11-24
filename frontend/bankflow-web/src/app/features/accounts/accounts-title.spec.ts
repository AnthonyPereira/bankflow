import { TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { ActivatedRouteSnapshot, RouterStateSnapshot, Route } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import { routes } from '../../app.routes';

describe('Titre de la route des comptes', () => {
  function resolveTitle(admin: boolean): unknown {
    TestBed.configureTestingModule({
      providers: [{ provide: AuthService, useValue: { isAdmin: signal(admin) } }],
    });
    const shell = routes.find((route) => route.children) as Route;
    const accounts = shell.children?.find((route) => route.path === 'comptes') as Route;
    const title = accounts.title as (
      route: ActivatedRouteSnapshot,
      state: RouterStateSnapshot,
    ) => string;
    return TestBed.runInInjectionContext(() =>
      title({} as ActivatedRouteSnapshot, {} as RouterStateSnapshot),
    );
  }

  it('affiche « Mes comptes » dans l’onglet pour un client', () => {
    expect(resolveTitle(false)).toBe('Mes comptes · BankFlow');
  });

  it('affiche « Tous les comptes » dans l’onglet pour un administrateur', () => {
    expect(resolveTitle(true)).toBe('Tous les comptes · BankFlow');
  });
});
