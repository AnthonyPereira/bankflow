import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';
import { AuthService } from '../auth/auth.service';
import { NotificationUiService } from '../notifications/notification-ui.service';
import { isPublicAuthRequest, SILENT_ERRORS } from './api';

const MESSAGES: Record<number, string> = {
  0: 'Le service est injoignable. Vérifiez votre connexion.',
  403: "Vous n'avez pas accès à cette ressource.",
  429: 'Trop de tentatives. Réessayez dans quelques instants.',
};
const SERVER_ERROR_MESSAGE = 'Une erreur est survenue. Réessayez plus tard.';

/** Traite les erreurs communes. Les erreurs métier (400, 404, 409) restent aux écrans. */
export const errorInterceptor: HttpInterceptorFn = (request, next) => {
  const auth = inject(AuthService);
  const router = inject(Router);
  const notifications = inject(NotificationUiService);

  return next(request).pipe(
    catchError((error: unknown) => {
      if (error instanceof HttpErrorResponse && !request.context.get(SILENT_ERRORS)) {
        if (error.status === 401) {
          if (!isPublicAuthRequest(request.url)) {
            auth.clearSession();
            notifications.showError('Votre session a expiré. Veuillez vous reconnecter.');
            void router.navigate(['/connexion'], { queryParams: { returnUrl: router.url } });
          }
        } else {
          const message = error.status >= 500 ? SERVER_ERROR_MESSAGE : MESSAGES[error.status];
          if (message) {
            notifications.showError(message);
          }
        }
      }
      return throwError(() => error);
    }),
  );
};
