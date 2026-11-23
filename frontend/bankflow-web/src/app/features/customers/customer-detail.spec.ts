import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Title } from '@angular/platform-browser';
import { provideRouter } from '@angular/router';
import { render, screen, waitFor, within } from '@testing-library/angular';
import userEvent from '@testing-library/user-event';
import { Account } from '../accounts/accounts.models';
import { Customer } from './customers.models';
import { ACCOUNT_OPENED_MESSAGE, CustomerDetail, DEMO_CUSTOMER_NOTICE } from './customer-detail';

const CUSTOMER_URL = '/api/customers/c1';
const ACCOUNTS_URL = '/api/accounts';
const CUSTOMER: Customer = {
  id: 'c1',
  userId: 'u1',
  email: 'ada@bankflow.test',
  firstName: 'Ada',
  lastName: 'Lovelace',
  phone: '+33 1 23 45 67 89',
  address: '1 rue des Lilas',
  status: 'ACTIVE',
  demo: false,
  createdAt: '2026-11-23T10:00:00Z',
  updatedAt: '2026-11-23T10:00:00Z',
};
const ACCOUNT: Account = {
  id: 'a1',
  accountNumber: 'FR7699999000010123456789012',
  customerId: 'c1',
  label: 'Courant',
  currency: 'EUR',
  balance: '10.00',
  status: 'ACTIVE',
  createdAt: '2026-11-23T10:00:00Z',
  closedAt: null,
};

function accountsPage(content: Account[]) {
  return { content, page: 0, size: 100, totalElements: content.length, totalPages: 1 };
}

describe('CustomerDetail', () => {
  async function setup(customer: Customer = CUSTOMER, accounts: Account[] = [ACCOUNT]) {
    await render(CustomerDetail, {
      inputs: { id: 'c1' },
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    const http = TestBed.inject(HttpTestingController);
    http.expectOne(CUSTOMER_URL).flush(customer);
    http.expectOne((r) => r.url === ACCOUNTS_URL).flush(accountsPage(accounts));
    await screen.findByRole('heading', { level: 1 });
    return { http, user: userEvent.setup() };
  }

  it('affiche l’identité, les coordonnées, le statut et le titre de la page', async () => {
    await setup();

    expect(await screen.findByRole('heading', { level: 1, name: 'Ada Lovelace' })).toBeTruthy();
    expect(screen.getAllByText('ada@bankflow.test').length).toBeGreaterThan(0);
    expect(screen.getByText('+33 1 23 45 67 89')).toBeTruthy();
    expect(screen.getByText('1 rue des Lilas')).toBeTruthy();
    expect(within(screen.getByRole('region', { name: 'Statut' })).getByText('Actif')).toBeTruthy();
    expect(TestBed.inject(Title).getTitle()).toBe('Ada Lovelace · BankFlow');
  });

  it('titre « Fiche client » tant que le client n’a pas de nom', async () => {
    await setup({ ...CUSTOMER, firstName: null, lastName: null });

    expect(
      await screen.findByRole('heading', { level: 1, name: 'Nom non renseigné' }),
    ).toBeTruthy();
    expect(TestBed.inject(Title).getTitle()).toBe('Fiche client · BankFlow');
  });

  it('liste les comptes du client, chacun lié à son détail', async () => {
    await setup();

    const link = await screen.findByRole('link', { name: 'Courant' });
    expect(link.getAttribute('href')).toBe('/comptes/a1');
  });

  it('suspend un client après confirmation', async () => {
    const { http, user } = await setup();

    await user.click(screen.getByRole('button', { name: 'Suspendre' }));
    const confirm = await screen.findByRole('button', { name: 'Confirmer la suspension' });
    expect(document.activeElement).toBe(confirm);
    http.expectNone(`${CUSTOMER_URL}/status`);
    await user.click(confirm);

    const request = http.expectOne(`${CUSTOMER_URL}/status`);
    expect(request.request.method).toBe('PUT');
    expect(request.request.body).toEqual({ status: 'SUSPENDED' });
    request.flush({ ...CUSTOMER, status: 'SUSPENDED' });

    expect(await screen.findByText('Suspendu', { selector: '.badge' })).toBeTruthy();
    expect(screen.getByRole('button', { name: 'Réactiver' })).toBeTruthy();
    expect(screen.queryByRole('button', { name: 'Confirmer la suspension' })).toBeNull();
  });

  it('annule la suspension sans appeler l’API', async () => {
    const { http, user } = await setup();

    await user.click(screen.getByRole('button', { name: 'Suspendre' }));
    await user.click(await screen.findByRole('button', { name: 'Annuler' }));

    expect(screen.getByRole('button', { name: 'Suspendre' })).toBeTruthy();
    http.expectNone(`${CUSTOMER_URL}/status`);
  });

  it('réactive un client suspendu sans confirmation', async () => {
    const { http, user } = await setup({ ...CUSTOMER, status: 'SUSPENDED' });

    await user.click(screen.getByRole('button', { name: 'Réactiver' }));

    const request = http.expectOne(`${CUSTOMER_URL}/status`);
    expect(request.request.body).toEqual({ status: 'ACTIVE' });
    request.flush(CUSTOMER);
    expect(await screen.findByRole('button', { name: 'Suspendre' })).toBeTruthy();
  });

  it('ne permet pas de suspendre un compte de démonstration', async () => {
    await setup({ ...CUSTOMER, demo: true });

    expect(screen.getByText('Démo')).toBeTruthy();
    expect(screen.getByText(DEMO_CUSTOMER_NOTICE)).toBeTruthy();
    expect(screen.queryByRole('button', { name: 'Suspendre' })).toBeNull();
  });

  it('affiche le refus de l’API si le compte de démonstration est tout de même visé', async () => {
    const { http, user } = await setup();

    await user.click(screen.getByRole('button', { name: 'Suspendre' }));
    await user.click(await screen.findByRole('button', { name: 'Confirmer la suspension' }));
    http
      .expectOne(`${CUSTOMER_URL}/status`)
      .flush(
        { code: 'DEMO_CUSTOMER_CANNOT_BE_SUSPENDED', detail: 'autre' },
        { status: 409, statusText: 'Conflict' },
      );

    expect((await screen.findByRole('alert')).textContent).toContain(
      'Un compte de démonstration ne peut pas être suspendu.',
    );
    expect(screen.getByRole('button', { name: 'Suspendre' })).toBeTruthy();
  });

  it('ouvre un compte pour le client avec son customerId puis rafraîchit la liste', async () => {
    const { http, user } = await setup();

    await user.selectOptions(screen.getByLabelText('Devise'), 'USD');
    await user.type(screen.getByLabelText('Libellé (facultatif)'), '  Voyage ');
    await user.click(screen.getByRole('button', { name: 'Ouvrir le compte' }));

    const request = http.expectOne((r) => r.method === 'POST' && r.url === ACCOUNTS_URL);
    expect(request.request.body).toEqual({ currency: 'USD', label: 'Voyage', customerId: 'c1' });
    request.flush(
      { ...ACCOUNT, id: 'a2', currency: 'USD' },
      { status: 201, statusText: 'Created' },
    );

    expect(await screen.findByText(ACCOUNT_OPENED_MESSAGE)).toBeTruthy();
    await waitFor(() => {
      http
        .expectOne((r) => r.method === 'GET' && r.url === ACCOUNTS_URL)
        .flush(accountsPage([ACCOUNT, { ...ACCOUNT, id: 'a2', label: 'Voyage' }]));
    });
    expect(await screen.findByRole('link', { name: 'Voyage' })).toBeTruthy();
  });

  it.each([
    ['ACCOUNT_LIMIT_REACHED', 'Ce client a déjà 5 comptes ouverts.', 409],
    ['CUSTOMER_SUSPENDED', "Ce client est suspendu : aucune opération n'est possible.", 403],
    ['CUSTOMER_NOT_FOUND', 'Client introuvable.', 404],
  ])('traduit l’erreur %s à l’ouverture d’un compte', async (code, message, status) => {
    const { http, user } = await setup();

    await user.click(screen.getByRole('button', { name: 'Ouvrir le compte' }));
    http
      .expectOne((r) => r.method === 'POST' && r.url === ACCOUNTS_URL)
      .flush({ code, detail: 'autre' }, { status, statusText: 'Erreur' });

    expect((await screen.findByRole('alert')).textContent).toContain(message);
  });

  it('refuse un libellé de plus de 50 caractères', async () => {
    const { http, user } = await setup();

    await user.type(screen.getByLabelText('Libellé (facultatif)'), 'x'.repeat(51));
    await user.click(screen.getByRole('button', { name: 'Ouvrir le compte' }));

    expect(await screen.findByText('Le libellé ne peut pas dépasser 50 caractères.')).toBeTruthy();
    http.expectNone((r) => r.method === 'POST');
  });

  it('signale un client introuvable', async () => {
    await render(CustomerDetail, {
      inputs: { id: 'c1' },
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    const http = TestBed.inject(HttpTestingController);
    http
      .expectOne(CUSTOMER_URL)
      .flush({ code: 'CUSTOMER_NOT_FOUND' }, { status: 404, statusText: 'Not Found' });
    http.expectOne((r) => r.url === ACCOUNTS_URL).flush(accountsPage([]));

    expect((await screen.findByRole('alert')).textContent).toContain('Client introuvable.');
    expect(screen.queryByRole('button', { name: 'Réessayer' })).toBeNull();
  });

  it('propose de réessayer après une autre erreur', async () => {
    await render(CustomerDetail, {
      inputs: { id: 'c1' },
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    const http = TestBed.inject(HttpTestingController);
    http.expectOne(CUSTOMER_URL).flush({}, { status: 500, statusText: 'Server Error' });
    http.expectOne((r) => r.url === ACCOUNTS_URL).flush(accountsPage([]));

    await userEvent.click(await screen.findByRole('button', { name: 'Réessayer' }));
    http.expectOne(CUSTOMER_URL).flush(CUSTOMER);
    expect(await screen.findByRole('heading', { level: 1, name: 'Ada Lovelace' })).toBeTruthy();
  });
});
