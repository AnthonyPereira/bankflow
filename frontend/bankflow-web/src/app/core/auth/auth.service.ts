import { HttpClient, HttpContext, HttpErrorResponse } from '@angular/common/http';
import { computed, inject, Service, signal } from '@angular/core';
import { jwtDecode } from 'jwt-decode';
import {
  catchError,
  finalize,
  firstValueFrom,
  map,
  Observable,
  of,
  retry,
  shareReplay,
  tap,
  throwError,
  timeout,
  timer,
} from 'rxjs';
import { environment } from '../../../environments/environment';
import { Credentials, Role, TokenResponse, User } from '../../shared/models/auth.models';
import { SILENT_ERRORS } from '../http/api';

interface Session {
  accessToken: string;
  role: Role;
}

const AUTH_URL = `${environment.apiUrl}/auth`;
const CONCURRENT_REFRESH_RETRY_MS = 300;
const RESTORE_TIMEOUT_MS = 5000;

/**
 * Porte la session. Le JWT reste en mémoire, et le refresh token dans un cookie HttpOnly
 * inaccessible au JavaScript (ADR-003).
 */
@Service()
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly session = signal<Session | null>(null);
  private refreshInFlight: Observable<string> | null = null;

  readonly accessToken = computed(() => this.session()?.accessToken ?? null);
  readonly isAuthenticated = computed(() => this.session() !== null);
  readonly isAdmin = computed(() => this.session()?.role === 'ROLE_ADMIN');

  register(credentials: Credentials): Observable<User> {
    return this.http.post<User>(`${AUTH_URL}/register`, credentials);
  }

  login(credentials: Credentials): Observable<void> {
    return this.http.post<TokenResponse>(`${AUTH_URL}/login`, credentials).pipe(
      map((response) => {
        this.startSession(response.accessToken);
      }),
    );
  }

  currentUser(): Observable<User> {
    return this.http.get<User>(`${AUTH_URL}/me`);
  }

  /**
   * Toutes les requêtes en attente partagent le même refresh. Un 401 est retenté une fois : un
   * autre onglet vient peut-être de faire tourner le jeton et le navigateur a alors le nouveau
   * cookie.
   */
  refresh(): Observable<string> {
    this.refreshInFlight ??= this.http
      .post<TokenResponse>(`${AUTH_URL}/refresh`, null, { context: silent() })
      .pipe(
        retry({
          count: 1,
          delay: (error: unknown) =>
            isUnauthorized(error) ? timer(CONCURRENT_REFRESH_RETRY_MS) : throwError(() => error),
        }),
        map((response) => this.startSession(response.accessToken)),
        tap({
          error: (error: unknown) => {
            if (isUnauthorized(error)) {
              this.clearSession();
            }
          },
        }),
        finalize(() => {
          this.refreshInFlight = null;
        }),
        shareReplay({ bufferSize: 1, refCount: false }),
      );
    return this.refreshInFlight;
  }

  /** Retrouve la session au chargement de la page. L'application démarre même en cas d'échec. */
  restoreSession(): Promise<void> {
    return firstValueFrom(
      this.refresh().pipe(
        timeout(RESTORE_TIMEOUT_MS),
        map(() => undefined),
        catchError(() => of(undefined)),
      ),
    );
  }

  /** La session locale se termine toujours, même si le serveur ne répond pas. */
  logout(): Observable<void> {
    return this.http.post<void>(`${AUTH_URL}/logout`, null, { context: silent() }).pipe(
      catchError(() => of(undefined)),
      finalize(() => {
        this.clearSession();
      }),
    );
  }

  clearSession(): void {
    this.session.set(null);
  }

  private startSession(accessToken: string): string {
    // Le rôle sert uniquement à l'affichage : le backend vérifie la signature et les droits.
    const { role } = jwtDecode<{ role: Role }>(accessToken);
    this.session.set({ accessToken, role });
    return accessToken;
  }
}

function silent(): HttpContext {
  return new HttpContext().set(SILENT_ERRORS, true);
}

function isUnauthorized(error: unknown): boolean {
  return error instanceof HttpErrorResponse && error.status === 401;
}
