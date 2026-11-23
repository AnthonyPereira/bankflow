import { defer, of } from 'rxjs';
import { runWithLock } from './web-lock';

/**
 * Verrou Web Locks simulé, fidèle à la spécification : les demandes passent l'une après l'autre,
 * et une demande abandonnée pendant l'attente est rejetée (`AbortError`) sans que son callback
 * ne soit appelé.
 */
function createFakeLockManager() {
  let queue = Promise.resolve();
  const request = vi.fn(
    (_name: string, options: LockOptions, callback: () => Promise<void>): Promise<void> => {
      const granted = queue.then(() => {
        if (options.signal?.aborted) {
          throw new DOMException('Lock request aborted', 'AbortError');
        }
        return callback();
      });
      queue = granted.catch(() => undefined);
      return granted;
    },
  );
  return { request } as unknown as LockManager & { request: typeof request };
}

/** Simule un autre onglet qui garde le verrou jusqu'à l'appel de la fonction retournée. */
function holdLock(locks: LockManager): () => void {
  let release!: () => void;
  void locks.request('bankflow-refresh', {}, () => new Promise<void>((done) => (release = done)));
  return () => release();
}

async function flushPromises(): Promise<void> {
  await new Promise((resolve) => setTimeout(resolve, 0));
}

describe('runWithLock', () => {
  it('exécute le travail une fois le verrou obtenu, puis le libère', async () => {
    const locks = createFakeLockManager();
    const values: number[] = [];

    runWithLock(locks, 'bankflow-refresh', of(1)).subscribe((value) => values.push(value));
    await flushPromises();

    expect(values).toEqual([1]);
    const next = vi.fn(() => Promise.resolve());
    await locks.request('bankflow-refresh', {}, next);
    expect(next).toHaveBeenCalled();
  });

  it('abandonne la demande de verrou en cas de désabonnement pendant l’attente', async () => {
    const locks = createFakeLockManager();
    const releaseOtherTab = holdLock(locks);
    const work = vi.fn(() => of('jeton'));
    const error = vi.fn();

    const subscription = runWithLock(locks, 'bankflow-refresh', defer(work)).subscribe({ error });
    await flushPromises();
    subscription.unsubscribe();

    const options = locks.request.mock.calls[1][1];
    expect(options.signal?.aborted).toBe(true);

    releaseOtherTab();
    await flushPromises();

    // Aucun appel refresh ne part et l'abandon n'est pas remonté comme une erreur.
    expect(work).not.toHaveBeenCalled();
    expect(error).not.toHaveBeenCalled();
  });

  it('libère aussitôt un verrou accordé après le désabonnement', async () => {
    // Navigateur qui ignorerait le signal : le callback arrive malgré l'abandon.
    const callbacks: (() => Promise<void>)[] = [];
    const locks = {
      request: (_name: string, _options: LockOptions, callback: () => Promise<void>) => {
        callbacks.push(callback);
        return Promise.resolve();
      },
    } as unknown as LockManager;
    const work = vi.fn(() => of('jeton'));

    runWithLock(locks, 'bankflow-refresh', defer(work)).subscribe().unsubscribe();

    await expect(callbacks[0]()).resolves.toBeUndefined();
    expect(work).not.toHaveBeenCalled();
  });
});
