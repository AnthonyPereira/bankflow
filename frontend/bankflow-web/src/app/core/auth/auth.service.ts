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
  Subscription,
  tap,
  throwError,
  timeout,
  timer,
} from 'rxjs';
import { environment } from '../../../environments/environment';
import { Credentials, DemoAccount, Role, TokenResponse, User } from './auth.models';
import { SILENT_ERRORS } from '../http/api';

interface Session {
  accessToken: string;
  role: Role;
}

const AUTH_URL = `${environment.apiUrl}/auth`;
const REFRESH_LOCK = 'bankflow-refresh';
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

  /** Comptes de démonstration. Une erreur donne une liste vide : la connexion reste possible. */
  demoAccounts(): Observable<DemoAccount[]> {
    return this.http
      .get<DemoAccount[]>(`${AUTH_URL}/demo-accounts`, { context: silent() })
      .pipe(catchError(() => of([])));
  }

  currentUser(): Observable<User> {
    return this.http.get<User>(`${AUTH_URL}/me`);
  }

  /**
   * Toutes les requêtes en attente d'un onglet partagent le même refresh, et les onglets se
   * rafraîchissent chacun à leur tour (voir {@link requestRefresh}).
   */
  refresh(): Observable<string> {
    this.refreshInFlight ??= this.requestRefresh().pipe(
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

  /**
   * Deux onglets qui présentent le même cookie en même temps font échouer l'un des deux avec un
   * 401 sans effacer le cookie (rotation concurrente, ADR-003). Le JavaScript ne peut pas
   * distinguer ce cas d'une absence de session : l'en-tête `Set-Cookie` lui est invisible et le
   * message est identique.
   *
   * Un verrou Web Locks, commun à tous les onglets, évite donc la course : chaque onglet attend
   * que le précédent ait reçu son nouveau cookie. Un 401 signifie alors qu'il n'y a pas de session,
   * et un visiteur anonyme ne fait qu'un appel. Sans Web Locks (contexte non sécurisé, vieux
   * navigateur), un 401 est retenté une fois après un court délai.
   */
  private requestRefresh(): Observable<TokenResponse> {
    const request = this.http.post<TokenResponse>(`${AUTH_URL}/refresh`, null, {
      context: silent(),
    });
    const locks = getLockManager();
    if (locks) {
      return runWithLock(locks, REFRESH_LOCK, request);
    }
    return request.pipe(
      retry({
        count: 1,
        delay: (error: unknown) =>
          isUnauthorized(error) ? timer(CONCURRENT_REFRESH_RETRY_MS) : throwError(() => error),
      }),
    );
  }
}

function silent(): HttpContext {
  return new HttpContext().set(SILENT_ERRORS, true);
}

/** `navigator.locks` n'existe que dans un contexte sécurisé (HTTPS ou localhost). */
function getLockManager(): LockManager | undefined {
  return (globalThis.navigator as Partial<Navigator> | undefined)?.locks;
}

/**
 * Exécute `work` en tenant le verrou `name`, partagé par tous les onglets de l'origine. Le verrou
 * est libéré à la fin de `work`, en cas d'erreur ou de désabonnement.
 */
function runWithLock<T>(locks: LockManager, name: string, work: Observable<T>): Observable<T> {
  return new Observable<T>((subscriber) => {
    const abort = new AbortController();
    let inner: Subscription | undefined;
    locks
      .request(
        name,
        { signal: abort.signal },
        () =>
          new Promise<void>((release) => {
            if (subscriber.closed) {
              release();
              return;
            }
            inner = work.pipe(finalize(release)).subscribe({
              next: (value) => subscriber.next(value),
              error: (error: unknown) => subscriber.error(error),
              complete: () => subscriber.complete(),
            });
          }),
      )
      .catch((error: unknown) => {
        // Un abandon pendant l'attente du verrou n'est pas une erreur.
        if (!abort.signal.aborted) {
          subscriber.error(error);
        }
      });
    return () => {
      abort.abort();
      inner?.unsubscribe();
    };
  });
}

function isUnauthorized(error: unknown): boolean {
  return error instanceof HttpErrorResponse && error.status === 401;
}
