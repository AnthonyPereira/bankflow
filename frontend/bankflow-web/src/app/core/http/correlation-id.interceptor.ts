import { HttpInterceptorFn } from '@angular/common/http';
import { isApiRequest } from './api';

/** Relie une action de l'utilisateur aux logs de tous les services traversés. */
export const correlationIdInterceptor: HttpInterceptorFn = (request, next) => {
  if (!isApiRequest(request.url)) {
    return next(request);
  }
  return next(request.clone({ setHeaders: { 'X-Correlation-Id': crypto.randomUUID() } }));
};
