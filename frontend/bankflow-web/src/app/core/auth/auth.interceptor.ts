import { HttpErrorResponse, HttpInterceptorFn, HttpRequest } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, of, switchMap, throwError } from 'rxjs';
import { isApiRequest, isPublicAuthRequest } from '../http/api';
import { AuthService } from './auth.service';

/** Ajoute le JWT et rejoue une fois la requête après un refresh si le jeton a expiré. */
export const authInterceptor: HttpInterceptorFn = (request, next) => {
  if (!isApiRequest(request.url) || isPublicAuthRequest(request.url)) {
    return next(request);
  }
  const auth = inject(AuthService);
  const sentToken = auth.getAccessToken();

  return next(addBearerToken(request, sentToken)).pipe(
    catchError((error: unknown) => {
      const currentToken = auth.getAccessToken();
      if (!(error instanceof HttpErrorResponse) || error.status !== 401 || !currentToken) {
        return throwError(() => error);
      }
      // Une autre requête a peut-être déjà renouvelé le jeton pendant celle-ci
      const freshToken = currentToken === sentToken ? auth.refresh() : of(currentToken);
      return freshToken.pipe(switchMap((token) => next(addBearerToken(request, token))));
    }),
  );
};

function addBearerToken<T>(request: HttpRequest<T>, token: string | null): HttpRequest<T> {
  return token ? request.clone({ setHeaders: { Authorization: `Bearer ${token}` } }) : request;
}
