import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { fireEvent, render, screen } from '@testing-library/angular';
import userEvent from '@testing-library/user-event';
import { Customer } from './profile.models';
import {
  MAX_RETRIES,
  PROFILE_CREATING_MESSAGE,
  Profile,
  RETRY_DELAY_MS,
  SAVED_MESSAGE,
  SAVE_ERROR_MESSAGE,
} from './profile';

const URL = '/api/customers/me';
const CUSTOMER: Customer = {
  id: 'c1',
  userId: 'u1',
  email: 'a@bankflow.test',
  firstName: 'Ada',
  lastName: 'Lovelace',
  phone: null,
  address: null,
  status: 'ACTIVE',
  demo: false,
  createdAt: '2026-11-23T10:00:00Z',
  updatedAt: '2026-11-23T10:00:00Z',
};
const NOT_FOUND = { status: 404, statusText: 'Not Found' };

describe('Profile', () => {
  afterEach(() => {
    vi.useRealTimers();
  });

  async function setup() {
    const { container, fixture } = await render(Profile, {
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    const http = TestBed.inject(HttpTestingController);
    return { http, container, fixture, user: userEvent.setup() };
  }

  async function setupLoaded(customer: Customer = CUSTOMER) {
    const context = await setup();
    context.http.expectOne(URL).flush(customer);
    await screen.findByLabelText('Prénom');
    return context;
  }

  it("affiche l'email en lecture seule, le statut et les champs préremplis", async () => {
    await setupLoaded();

    expect(screen.getByText('a@bankflow.test')).toBeTruthy();
    expect(screen.getByText('Actif')).toBeTruthy();
    expect((screen.getByLabelText('Prénom') as HTMLInputElement).value).toBe('Ada');
    expect((screen.getByLabelText('Nom') as HTMLInputElement).value).toBe('Lovelace');
    expect(screen.queryByLabelText('Adresse email')).toBeNull();
  });

  it('annonce la création du profil sur un 404 puis réessaie', async () => {
    vi.useFakeTimers({ toFake: ['setTimeout'] });
    const { http } = await setup();

    http.expectOne(URL).flush(null, NOT_FOUND);
    expect(
      (await screen.findByText('Votre profil est en cours de création…'))
        .closest('div')
        ?.getAttribute('aria-live'),
    ).toBe('polite');

    vi.advanceTimersByTime(RETRY_DELAY_MS);
    http.expectOne(URL).flush(CUSTOMER);

    expect(await screen.findByLabelText('Prénom')).toBeTruthy();
    expect(screen.queryByText('Votre profil est en cours de création…')).toBeNull();
  });

  it('propose de réessayer après cinq échecs', async () => {
    vi.useFakeTimers({ toFake: ['setTimeout'] });
    const { http } = await setup();

    http.expectOne(URL).flush(null, NOT_FOUND);
    for (let attempt = 0; attempt < MAX_RETRIES; attempt++) {
      vi.advanceTimersByTime(RETRY_DELAY_MS);
      http.expectOne(URL).flush(null, NOT_FOUND);
    }

    const button = await screen.findByRole('button', { name: 'Réessayer' });
    vi.advanceTimersByTime(RETRY_DELAY_MS * 3);
    http.expectNone(URL);

    button.click();
    http.expectOne(URL).flush(CUSTOMER);
    expect(await screen.findByLabelText('Prénom')).toBeTruthy();
  });

  it('signale une autre erreur de chargement sans réessayer seul', async () => {
    const { http } = await setup();

    http.expectOne(URL).flush(null, { status: 500, statusText: 'Server Error' });

    expect((await screen.findByRole('alert')).textContent).toContain('pas pu être chargé');
    http.expectNone(URL);
  });

  it('refuse un téléphone invalide sans appeler le serveur', async () => {
    const { http, user } = await setupLoaded();

    await user.type(screen.getByLabelText('Téléphone'), 'abc');
    await user.click(screen.getByRole('button', { name: 'Enregistrer' }));

    expect(await screen.findByText(/Le numéro doit contenir/)).toBeTruthy();
    http.expectNone(URL);
  });

  it('teste le téléphone sur la valeur sans espaces de bord', async () => {
    const { http, user } = await setupLoaded();

    await user.type(screen.getByLabelText('Téléphone'), ' 0612345678 ');
    await user.click(screen.getByRole('button', { name: 'Enregistrer' }));

    expect(http.expectOne(URL).request.body.phone).toBe('0612345678');
  });

  it('ne programme rien si le composant est détruit pendant la requête', async () => {
    vi.useFakeTimers({ toFake: ['setTimeout'] });
    const { http, fixture } = await setup();
    const request = http.expectOne(URL);
    const timersBefore = vi.getTimerCount();

    fixture.destroy();

    expect(request.cancelled).toBe(true);
    expect(vi.getTimerCount()).toBeLessThanOrEqual(timersBefore);
    vi.advanceTimersByTime(RETRY_DELAY_MS * 3);
    http.expectNone(URL);
  });

  it("n'envoie aucune requête si le composant est détruit pendant l'attente", async () => {
    vi.useFakeTimers({ toFake: ['setTimeout'] });
    const { http, fixture } = await setup();
    http.expectOne(URL).flush(null, NOT_FOUND);
    await screen.findByText('Votre profil est en cours de création…');

    fixture.destroy();
    vi.advanceTimersByTime(RETRY_DELAY_MS * 3);

    http.expectNone(URL);
  });

  it('ignore une deuxième soumission pendant la première', async () => {
    const { http, container } = await setupLoaded();
    const form = container.querySelector('form') as HTMLFormElement;

    fireEvent.submit(form);
    fireEvent.submit(form);

    http.expectOne(URL);
  });

  it('place le focus sur le premier champ refusé par le serveur', async () => {
    const { http, user } = await setupLoaded();

    await user.click(screen.getByRole('button', { name: 'Enregistrer' }));
    http.expectOne(URL).flush(
      {
        code: 'VALIDATION_ERROR',
        errors: { address: 'Adresse refusée.', lastName: 'Nom refusé.' },
      },
      { status: 400, statusText: 'Bad Request' },
    );

    await screen.findByText('Nom refusé.');
    expect(document.activeElement).toBe(screen.getByLabelText('Nom'));
  });

  it("annonce que le profil est en cours de création sur un 404 à l'enregistrement", async () => {
    const { http, user } = await setupLoaded();

    await user.click(screen.getByRole('button', { name: 'Enregistrer' }));
    http
      .expectOne(URL)
      .flush({ code: 'CUSTOMER_NOT_FOUND' }, { status: 404, statusText: 'Not Found' });

    expect((await screen.findByRole('alert')).textContent).toContain(PROFILE_CREATING_MESSAGE);
  });

  it('refuse un prénom de plus de 100 caractères', async () => {
    const { http, user } = await setupLoaded();
    const input = screen.getByLabelText('Prénom');

    await user.clear(input);
    await user.click(input);
    await user.paste('a'.repeat(101));
    await user.click(screen.getByRole('button', { name: 'Enregistrer' }));

    expect(await screen.findByText(/ne peut pas dépasser 100/)).toBeTruthy();
    http.expectNone(URL);
  });

  it('envoie les valeurs nettoyées et confirme', async () => {
    const { http, user } = await setupLoaded();

    await user.clear(screen.getByLabelText('Prénom'));
    await user.type(screen.getByLabelText('Téléphone'), '+33 6 12 34 56 78');
    await user.click(screen.getByRole('button', { name: 'Enregistrer' }));

    const request = http.expectOne(URL);
    expect(request.request.method).toBe('PUT');
    expect(request.request.body).toEqual({
      firstName: null,
      lastName: 'Lovelace',
      phone: '+33 6 12 34 56 78',
      address: null,
    });
    expect(
      (screen.getByRole('button', { name: 'Enregistrement…' }) as HTMLButtonElement).disabled,
    ).toBe(true);
    request.flush({ ...CUSTOMER, firstName: null, phone: '+33 6 12 34 56 78' });

    expect(await screen.findByText(SAVED_MESSAGE)).toBeTruthy();
    expect(screen.getByText(SAVED_MESSAGE).closest('div')?.getAttribute('aria-live')).toBe(
      'polite',
    );
    expect(
      (screen.getByRole('button', { name: 'Enregistrer' }) as HTMLButtonElement).disabled,
    ).toBe(false);
  });

  it('affiche les erreurs du serveur sous les champs concernés', async () => {
    const { http, user } = await setupLoaded();

    await user.click(screen.getByRole('button', { name: 'Enregistrer' }));
    http
      .expectOne(URL)
      .flush(
        { code: 'VALIDATION_ERROR', errors: { phone: 'Numéro de téléphone refusé.' } },
        { status: 400, statusText: 'Bad Request' },
      );

    expect(await screen.findByText('Numéro de téléphone refusé.')).toBeTruthy();
  });

  it("affiche un message général quand l'enregistrement échoue sans détail de champ", async () => {
    const { http, user } = await setupLoaded();

    await user.click(screen.getByRole('button', { name: 'Enregistrer' }));
    http.expectOne(URL).flush(null, { status: 500, statusText: 'Server Error' });

    expect((await screen.findByRole('alert')).textContent).toContain(SAVE_ERROR_MESSAGE);
  });
});
