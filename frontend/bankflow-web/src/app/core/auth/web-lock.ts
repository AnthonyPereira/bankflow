import { finalize, Observable, Subscription } from 'rxjs';

/** `navigator.locks` n'existe que dans un contexte sécurisé (HTTPS ou localhost). */
export function getLockManager(): LockManager | undefined {
  return (globalThis.navigator as Partial<Navigator> | undefined)?.locks;
}

/**
 * Exécute `work` en tenant le verrou `name`, partagé par tous les onglets de l'origine. Le verrou
 * est libéré à la fin de `work`, en cas d'erreur ou de désabonnement.
 */
export function runWithLock<T>(
  locks: LockManager,
  name: string,
  work: Observable<T>,
): Observable<T> {
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
