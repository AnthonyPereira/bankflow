import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { map } from 'rxjs';
import { AuthService } from './auth.service';

// Ces guards améliorent la navigation. L'autorisation réelle reste vérifiée par le backend.
// Chacun attend un refresh encore en cours : il décide sur la session réelle, et non sur une
// session pas encore restaurée.

export const authGuard: CanActivateFn = (_route, state) => {
  const auth = inject(AuthService);
  const router = inject(Router);
  return auth
    .waitForPendingRefresh()
    .pipe(
      map(() =>
        auth.isAuthenticated()
          ? true
          : router.createUrlTree(['/connexion'], { queryParams: { returnUrl: state.url } }),
      ),
    );
};

export const adminGuard: CanActivateFn = (_route, state) => {
  const auth = inject(AuthService);
  const router = inject(Router);
  return auth.waitForPendingRefresh().pipe(
    map(() => {
      if (!auth.isAuthenticated()) {
        return router.createUrlTree(['/connexion'], { queryParams: { returnUrl: state.url } });
      }
      return auth.isAdmin() ? true : router.createUrlTree(['/tableau-de-bord']);
    }),
  );
};

/** Les pages propres à un client (profil) n'existent pas pour un administrateur : il n'a pas de fiche client. */
export const clientGuard: CanActivateFn = (_route, state) => {
  const auth = inject(AuthService);
  const router = inject(Router);
  return auth.waitForPendingRefresh().pipe(
    map(() => {
      if (!auth.isAuthenticated()) {
        return router.createUrlTree(['/connexion'], { queryParams: { returnUrl: state.url } });
      }
      return auth.isAdmin() ? router.createUrlTree(['/tableau-de-bord']) : true;
    }),
  );
};

/** Évite d'afficher la connexion ou l'inscription à un utilisateur déjà connecté. */
export const guestGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  return auth
    .waitForPendingRefresh()
    .pipe(map(() => (auth.isAuthenticated() ? router.createUrlTree(['/tableau-de-bord']) : true)));
};
