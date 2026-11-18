import { registerLocaleData } from '@angular/common';
import localeFr from '@angular/common/locales/fr';
import { provideHttpClient, withFetch, withInterceptors } from '@angular/common/http';
import {
  ApplicationConfig,
  inject,
  LOCALE_ID,
  provideAppInitializer,
  provideBrowserGlobalErrorListeners,
} from '@angular/core';
import { provideRouter, withComponentInputBinding } from '@angular/router';
import { routes } from './app.routes';
import { authInterceptor } from './core/auth/auth.interceptor';
import { AuthService } from './core/auth/auth.service';
import { correlationIdInterceptor } from './core/http/correlation-id.interceptor';
import { errorInterceptor } from './core/http/error.interceptor';
import { registerIcons } from './core/icons/register-icons';

registerLocaleData(localeFr);

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(routes, withComponentInputBinding()),
    // L'intercepteur d'erreurs entoure celui du JWT : il ne voit que le résultat après un refresh
    provideHttpClient(
      withFetch(),
      withInterceptors([correlationIdInterceptor, errorInterceptor, authInterceptor]),
    ),
    provideAppInitializer(() => inject(AuthService).restoreSession()),
    { provide: LOCALE_ID, useValue: 'fr-FR' },
    provideAppInitializer(registerIcons),
  ],
};
