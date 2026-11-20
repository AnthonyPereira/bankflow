import { HttpClient, HttpContext, HttpErrorResponse } from '@angular/common/http';
import { computed, DestroyRef, inject, Service, signal } from '@angular/core';
import { Router } from '@angular/router';
import { jwtDecode } from 'jwt-decode';
import {
  catchError,
  EMPTY,
  endWith,
  finalize,
  ignoreElements,
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
import { Credentials, DemoAccount, Role, TokenResponse, User } from './auth.models';
import { SILENT_ERRORS } from '../http/api';
import { resolveReturnUrl } from './return-url';
import { getLockManager, runWithLock } from './web-lock';

interface Session {
  accessToken: string;
  role: Role;
}

const AUTH_URL = `${environment.apiUrl}/auth`;
const REFRESH_LOCK = 'bankflow-refresh';
const CONCURRENT_REFRESH_RETRY_MS = 300;
const RESTORE_TIMEOUT_MS = 5000;
const GUEST_PAGES = ['/connexion', '/inscription'];
const AUTH_CHANNEL = 'bankflow-auth';
const LOGOUT_MESSAGE = 'logout';

/**
 * Porte la session. Le JWT reste en mémoire, et le refresh token dans un cookie HttpOnly
 * inaccessible au JavaScript (ADR-003).
 */
@Service()
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);
  private readonly session = signal<Session | null>(null);
  private readonly channel = openAuthChannel();
  private refreshInFlight: Observable<string> | null = null;

  readonly getAccessToken = computed(() => this.session()?.accessToken ?? null);
  readonly isAuthenticated = computed(() => this.session() !== null);
  readonly isAdmin = computed(() => this.session()?.role === 'ROLE_ADMIN');

  constructor() {
    const channel = this.channel;
    if (channel) {
      // Une déconnexion dans un autre onglet ferme aussi la session de celui-ci.
      channel.onmessage = (event: MessageEvent<unknown>) => {
        if (event.data === LOGOUT_MESSAGE) {
          this.endRemoteSession();
        }
      };
      inject(DestroyRef).onDestroy(() => channel.close());
    }
  }

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
  fetchDemoAccounts(): Observable<DemoAccount[]> {
    return this.http
      .get<DemoAccount[]>(`${AUTH_URL}/demo-accounts`, { context: createSilentContext() })
      .pipe(catchError(() => of([])));
  }

  /** L'écran affiche lui-même l'erreur : pas de message global en plus. */
  fetchCurrentUser(): Observable<User> {
    return this.http.get<User>(`${AUTH_URL}/me`, { context: createSilentContext() });
  }

  /**
   * Toutes les requêtes en attente d'un onglet partagent le même refresh, et les onglets se
   * rafraîchissent chacun à leur tour (voir {@link requestRefresh}).
   *
   * Un refus ne ferme pas la session ici : l'intercepteur d'erreurs s'en charge, une seule fois
   * même si plusieurs requêtes échouent en même temps.
   */
  refresh(): Observable<string> {
    this.refreshInFlight ??= this.requestRefresh().pipe(
      map((response) => {
        const wasAnonymous = !this.isAuthenticated();
        const accessToken = this.startSession(response.accessToken);
        if (wasAnonymous) {
          this.leaveGuestPage();
        }
        return accessToken;
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

  /**
   * Complète dès qu'aucun refresh n'est en cours, quelle qu'en soit l'issue. L'attente est bornée :
   * un autre onglet qui garde le verrou ne bloque pas la navigation.
   */
  waitForPendingRefresh(): Observable<void> {
    if (!this.refreshInFlight) {
      return of(undefined);
    }
    return this.refreshInFlight.pipe(
      timeout(RESTORE_TIMEOUT_MS),
      ignoreElements(),
      catchError(() => EMPTY),
      endWith(undefined),
    );
  }

  /**
   * La session locale se termine toujours, même si le serveur ne répond pas, et les autres
   * onglets sont prévenus. Émet `false` si le serveur n'a pas confirmé : le cookie de refresh
   * peut alors rester valide.
   */
  logout(): Observable<boolean> {
    return this.http
      .post<void>(`${AUTH_URL}/logout`, null, { context: createSilentContext() })
      .pipe(
        map(() => true),
        catchError(() => of(false)),
        tap(() => {
          this.clearSession();
          this.channel?.postMessage(LOGOUT_MESSAGE);
        }),
      );
  }

  clearSession(): void {
    this.session.set(null);
  }

  private endRemoteSession(): void {
    this.clearSession();
    if (!this.isOnGuestPage()) {
      void this.router.navigateByUrl('/connexion');
    }
  }

  /**
   * Un refresh lent (verrou tenu par un autre onglet) peut aboutir après la redirection vers la
   * connexion : l'utilisateur rejoint alors la page qu'il demandait.
   */
  private leaveGuestPage(): void {
    if (this.isOnGuestPage()) {
      const returnUrl: unknown = this.router.parseUrl(this.router.url).queryParams['returnUrl'];
      void this.router.navigateByUrl(resolveReturnUrl(returnUrl));
    }
  }

  private isOnGuestPage(): boolean {
    const path = this.router.url.split(/[?#]/)[0];
    return GUEST_PAGES.includes(path);
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
      context: createSilentContext(),
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

/** `BroadcastChannel` manque dans certains navigateurs anciens : chaque onglet reste alors isolé. */
function openAuthChannel(): BroadcastChannel | null {
  if (typeof BroadcastChannel === 'undefined') {
    return null;
  }
  try {
    return new BroadcastChannel(AUTH_CHANNEL);
  } catch {
    return null;
  }
}

function createSilentContext(): HttpContext {
  return new HttpContext().set(SILENT_ERRORS, true);
}

function isUnauthorized(error: unknown): boolean {
  return error instanceof HttpErrorResponse && error.status === 401;
}
