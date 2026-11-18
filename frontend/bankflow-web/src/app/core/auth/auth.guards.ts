import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from './auth.service';

// Ces guards améliorent la navigation. L'autorisation réelle reste vérifiée par le backend.

export const authGuard: CanActivateFn = (_route, state) => {
  if (inject(AuthService).isAuthenticated()) {
    return true;
  }
  return inject(Router).createUrlTree(['/connexion'], { queryParams: { returnUrl: state.url } });
};

export const adminGuard: CanActivateFn = (_route, state) => {
  const auth = inject(AuthService);
  const router = inject(Router);
  if (!auth.isAuthenticated()) {
    return router.createUrlTree(['/connexion'], { queryParams: { returnUrl: state.url } });
  }
  return auth.isAdmin() ? true : router.createUrlTree(['/tableau-de-bord']);
};

/** Évite d'afficher la connexion ou l'inscription à un utilisateur déjà connecté. */
export const guestGuard: CanActivateFn = () => {
  if (!inject(AuthService).isAuthenticated()) {
    return true;
  }
  return inject(Router).createUrlTree(['/tableau-de-bord']);
};
