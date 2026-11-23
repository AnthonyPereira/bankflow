import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { render, screen } from '@testing-library/angular';
import userEvent from '@testing-library/user-event';
import { AuthService } from '../../core/auth/auth.service';
import { Account, PageResponse } from '../accounts/accounts.models';
import { Transaction } from './operations.models';
import { Operations } from './operations';

const ACCOUNT: Account = {
  id: 'a1',
  accountNumber: 'FR7699999000010123456789012',
  customerId: 'c1',
  label: 'Vacances',
  currency: 'EUR',
  balance: '10.00',
  status: 'ACTIVE',
  createdAt: '2026-11-23T10:00:00Z',
  closedAt: null,
};

const TRANSACTION: Transaction = {
  id: 't1',
  accountId: 'a1',
  type: 'WITHDRAWAL',
  amount: '20.00',
  currency: 'EUR',
  balanceAfter: '980.00',
  transferId: null,
  compensatesTransactionId: null,
  compensatedByTransactionId: null,
  description: 'Courses',
  performedBy: 'CLIENT',
  createdAt: '2026-11-23T10:00:00Z',
};

function page<T>(content: T[], totalElements = content.length): PageResponse<T> {
  return { content, page: 0, size: 20, totalElements, totalPages: Math.ceil(totalElements / 20) };
}

describe('Operations', () => {
  const isAdmin = signal(false);

  async function setup(admin = false, transactions: Transaction[] = [TRANSACTION], total?: number) {
    isAdmin.set(admin);
    await render(Operations, {
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: AuthService, useValue: { isAdmin } },
      ],
    });
    const http = TestBed.inject(HttpTestingController);
    if (!admin) {
      http.expectOne((r) => r.url === '/api/accounts').flush(page([ACCOUNT]));
    }
    http.expectOne((r) => r.url === '/api/transactions').flush(page(transactions, total));
    return { http, user: userEvent.setup() };
  }

  it('titre la page « Mes opérations » et propose le filtre de compte à un client', async () => {
    await setup();

    expect(screen.getByRole('heading', { level: 1, name: 'Mes opérations' })).toBeTruthy();
    expect(await screen.findByRole('option', { name: 'Vacances' })).toBeTruthy();
    expect(screen.getByText('Courses')).toBeTruthy();
    expect(screen.getByRole('rowheader', { name: 'Retrait' })).toBeTruthy();
    expect(screen.getByRole('cell', { name: 'Vacances' })).toBeTruthy();
  });

  it('titre la page « Toutes les opérations » sans filtre de compte pour un administrateur', async () => {
    const { http } = await setup(true, [{ ...TRANSACTION, performedBy: 'ADMIN' }]);

    expect(screen.getByRole('heading', { level: 1, name: 'Toutes les opérations' })).toBeTruthy();
    expect(screen.queryByLabelText('Compte')).toBeNull();
    expect(await screen.findByText('Effectuée par un conseiller')).toBeTruthy();
    http.expectNone((r) => r.url === '/api/accounts');
  });

  it('affiche l’état vide et le nombre de résultats', async () => {
    await setup(false, []);

    expect(
      await screen.findByText('Aucune opération ne correspond à votre recherche.'),
    ).toBeTruthy();
    expect(screen.getByText(/0\s+opération trouvée/)).toBeTruthy();
  });

  it('applique les filtres et revient à la première page', async () => {
    const { http, user } = await setup(false, [TRANSACTION], 42);
    await screen.findByRole('option', { name: 'Vacances' });
    await user.click(screen.getByRole('button', { name: 'Page suivante' }));
    http
      .expectOne((r) => r.url === '/api/transactions')
      .flush({ ...page([TRANSACTION], 42), page: 1 });
    await screen.findByText('21 – 40 sur 42');

    await user.selectOptions(screen.getByLabelText('Compte'), 'a1');
    await user.selectOptions(screen.getByLabelText('Type'), 'WITHDRAWAL');
    await user.type(screen.getByLabelText('Du'), '2026-11-01');
    await user.type(screen.getByLabelText('Au'), '2026-11-30');
    await user.click(screen.getByRole('button', { name: 'Filtrer' }));

    const request = http.expectOne((r) => r.url === '/api/transactions');
    expect(request.request.params.get('accountId')).toBe('a1');
    expect(request.request.params.get('type')).toBe('WITHDRAWAL');
    expect(request.request.params.get('from')).toBe('2026-11-01');
    expect(request.request.params.get('to')).toBe('2026-11-30');
    expect(request.request.params.get('page')).toBe('0');
    request.flush(page([TRANSACTION]));
  });

  it('refuse une période dont le début suit la fin', async () => {
    const { http, user } = await setup();

    await user.type(screen.getByLabelText('Du'), '2026-11-30');
    await user.type(screen.getByLabelText('Au'), '2026-11-01');
    await user.click(screen.getByRole('button', { name: 'Filtrer' }));

    expect((await screen.findByRole('alert')).textContent).toContain(
      'La date de début doit précéder la date de fin.',
    );
    http.expectNone((r) => r.url === '/api/transactions');
  });

  it('réinitialise les filtres', async () => {
    const { http, user } = await setup();

    await user.selectOptions(screen.getByLabelText('Type'), 'DEPOSIT');
    await user.click(screen.getByRole('button', { name: 'Réinitialiser' }));

    const request = http.expectOne((r) => r.url === '/api/transactions');
    expect(request.request.params.has('type')).toBe(false);
    request.flush(page([TRANSACTION]));
  });

  it('pagine avec la pagination maison', async () => {
    const { http, user } = await setup(false, [TRANSACTION], 42);

    expect(await screen.findByText('1 – 20 sur 42')).toBeTruthy();
    await user.click(screen.getByRole('button', { name: 'Page suivante' }));

    const request = http.expectOne((r) => r.url === '/api/transactions');
    expect(request.request.params.get('page')).toBe('1');
    request.flush({ ...page([TRANSACTION], 42), page: 1 });
  });

  it('signale une erreur de chargement puis recharge', async () => {
    isAdmin.set(true);
    await render(Operations, {
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: AuthService, useValue: { isAdmin } },
      ],
    });
    const http = TestBed.inject(HttpTestingController);
    http
      .expectOne((r) => r.url === '/api/transactions')
      .flush(null, { status: 500, statusText: 'Server Error' });

    expect((await screen.findByRole('alert')).textContent).toContain('pas pu être chargées');
    screen.getByRole('button', { name: 'Réessayer' }).click();
    const reload = await vi.waitFor(() => http.expectOne((r) => r.url === '/api/transactions'));
    reload.flush(page([TRANSACTION]));
    expect(await screen.findByRole('table')).toBeTruthy();
  });
});
