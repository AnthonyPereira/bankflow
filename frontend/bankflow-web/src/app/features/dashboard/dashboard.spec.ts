import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { importProvidersFrom, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { MatIconTestingModule } from '@angular/material/icon/testing';
import { provideRouter } from '@angular/router';
import { render, screen, within } from '@testing-library/angular';
import { of, throwError } from 'rxjs';
import { User } from '../../core/auth/auth.models';
import { AuthService } from '../../core/auth/auth.service';
import { SILENT_ERRORS } from '../../core/http/api';
import { Account, AccountsSummary, PageResponse } from '../accounts/accounts.models';
import { Transaction } from '../operations/operations.models';
import { Dashboard } from './dashboard';

const USER: User = {
  id: '1',
  email: 'a@bankflow.test',
  role: 'ROLE_CLIENT',
  createdAt: '2026-11-18T10:00:00Z',
};

const ACCOUNT: Account = {
  id: 'a1',
  accountNumber: 'FR7699999000010123456789012',
  customerId: 'c1',
  label: 'Courant',
  currency: 'EUR',
  balance: '1000.10',
  status: 'ACTIVE',
  createdAt: '2026-11-23T10:00:00Z',
  closedAt: null,
};

const TRANSACTION: Transaction = {
  id: 't1',
  accountId: 'a1',
  type: 'TRANSFER_OUT',
  amount: '20.00',
  currency: 'EUR',
  balanceAfter: '980.00',
  transferId: 'tr1',
  compensatesTransactionId: null,
  compensatedByTransactionId: null,
  description: null,
  direction: 'DEBIT',
  counterpartyIban: 'FR7699999000010999999999912',
  performedBy: 'CLIENT',
  createdAt: '2026-11-23T10:00:00Z',
};

const SUMMARY: AccountsSummary = {
  customers: { total: 12, active: 11, suspended: 1 },
  accounts: { open: 20, closed: 3 },
  totalsByCurrency: [{ currency: 'EUR', balance: '12345.67', openAccounts: 18 }],
};

function page<T>(content: T[]): PageResponse<T> {
  return { content, page: 0, size: 100, totalElements: content.length, totalPages: 1 };
}

describe('Dashboard', () => {
  const isAdmin = signal(false);

  async function setup(fetchCurrentUser: AuthService['fetchCurrentUser'] = () => of(USER)) {
    const view = await render(Dashboard, {
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        importProvidersFrom(MatIconTestingModule),
        { provide: AuthService, useValue: { fetchCurrentUser, isAdmin } },
      ],
    });
    return { http: TestBed.inject(HttpTestingController), view };
  }

  function expectAccounts(http: HttpTestingController) {
    return http.expectOne((r) => r.url === '/api/accounts');
  }

  function expectTransactions(http: HttpTestingController, size: number) {
    const request = http.expectOne((r) => r.url === '/api/transactions');
    expect(request.request.params.get('size')).toBe(String(size));
    return request;
  }

  describe('client', () => {
    beforeEach(() => isAdmin.set(false));

    it('affiche les informations du compte connecté', async () => {
      const { http } = await setup();
      expectAccounts(http).flush(page([]));
      expectTransactions(http, 5).flush(page([]));

      expect(await screen.findByText('a@bankflow.test')).toBeTruthy();
      expect(screen.getByText('Client')).toBeTruthy();
    });

    it('laisse chaque bloc afficher sa propre erreur (requêtes silencieuses)', async () => {
      const { http } = await setup();
      const accounts = expectAccounts(http);
      const transactions = expectTransactions(http, 5);

      expect(accounts.request.context.get(SILENT_ERRORS)).toBe(true);
      expect(transactions.request.context.get(SILENT_ERRORS)).toBe(true);
      accounts.flush(page([]));
      transactions.flush(page([]));
    });

    it("n'appelle jamais la vue de la banque", async () => {
      const { http } = await setup();
      expectAccounts(http).flush(page([ACCOUNT]));
      expectTransactions(http, 5).flush(page([]));
      await screen.findByRole('link', { name: 'Courant' });

      http.expectNone('/api/accounts/summary');
      http.verify();
    });

    it('donne un nom accessible unique à chaque lien de compte de l’historique', async () => {
      const { http } = await setup();
      expectAccounts(http).flush(page([ACCOUNT]));
      expectTransactions(http, 5).flush(
        page([TRANSACTION, { ...TRANSACTION, id: 't2', createdAt: '2026-11-22T08:00:00Z' }]),
      );

      const links = await screen.findAllByRole('link', {
        name: /^Voir le compte Courant, opération/,
      });
      expect(new Set(links.map((link) => link.getAttribute('aria-label'))).size).toBe(2);
    });

    it('signale une liste partielle au-delà de 100 comptes', async () => {
      const { http } = await setup();
      expectAccounts(http).flush({ ...page([ACCOUNT]), totalElements: 101 });
      expectTransactions(http, 5).flush(page([]));

      expect(
        await screen.findByText('Seuls les 100 comptes les plus récents sont affichés.'),
      ).toBeTruthy();
    });

    it('affiche les comptes actifs avec leur total par devise', async () => {
      const { http } = await setup();
      expectAccounts(http).flush(
        page([
          ACCOUNT,
          { ...ACCOUNT, id: 'a2', label: 'Épargne', balance: '0.95' },
          { ...ACCOUNT, id: 'a3', label: 'Ancien', balance: '500.00', status: 'CLOSED' },
          { ...ACCOUNT, id: 'a4', label: 'Dollars', currency: 'USD', balance: '10.00' },
        ]),
      );
      expectTransactions(http, 5).flush(page([]));

      const link = await screen.findByRole('link', { name: 'Courant' });
      expect(link.getAttribute('href')).toBe('/comptes/a1');
      expect(screen.queryByText('Ancien')).toBeNull();
      const totals = within(
        screen.getByRole('heading', { name: 'Total par devise' }).nextElementSibling as HTMLElement,
      );
      expect(totals.getByText(/1\s001,05\s€/)).toBeTruthy();
      expect(totals.getByText(/10,00\s\$US/)).toBeTruthy();
    });

    it('affiche les 5 dernières opérations avec leur libellé de virement', async () => {
      const { http } = await setup();
      expectAccounts(http).flush(page([ACCOUNT]));
      expectTransactions(http, 5).flush(page([TRANSACTION]));

      expect(
        await screen.findByText('Virement émis vers FR76 9999 9000 0109 9999 9999 912'),
      ).toBeTruthy();
      expect(screen.getByText(/−20,00\s€/)).toBeTruthy();
      expect(
        screen.getByRole('link', { name: 'Voir toutes mes opérations' }).getAttribute('href'),
      ).toBe('/operations');
      expect(screen.getByRole('link', { name: 'Faire un virement' }).getAttribute('href')).toBe(
        '/virement',
      );
    });

    it("isole l'erreur des comptes : les opérations restent affichées", async () => {
      const { http } = await setup();
      expectAccounts(http).flush(null, { status: 500, statusText: 'Error' });
      expectTransactions(http, 5).flush(page([TRANSACTION]));

      expect((await screen.findByRole('alert')).textContent).toContain(
        "Vos comptes n'ont pas pu être chargés.",
      );
      expect(screen.getByText(/Virement émis/)).toBeTruthy();
      expect(screen.queryByText("Les opérations n'ont pas pu être chargées.")).toBeNull();
    });

    it("isole l'erreur des opérations : les comptes restent affichés", async () => {
      const { http } = await setup();
      expectAccounts(http).flush(page([ACCOUNT]));
      expectTransactions(http, 5).flush(null, { status: 500, statusText: 'Error' });

      expect((await screen.findByRole('alert')).textContent).toContain(
        "Les opérations n'ont pas pu être chargées.",
      );
      expect(screen.getByRole('link', { name: 'Courant' })).toBeTruthy();
    });

    it("signale une erreur de chargement de l'utilisateur, sans bloquer le reste", async () => {
      const { http } = await setup(() => throwError(() => new Error('indisponible')));
      expectAccounts(http).flush(page([ACCOUNT]));
      expectTransactions(http, 5).flush(page([]));

      expect((await screen.findByRole('alert')).textContent).toContain(
        "Vos informations n'ont pas pu être chargées.",
      );
      expect(screen.getByRole('link', { name: 'Courant' })).toBeTruthy();
    });

    it('annonce seulement les notifications à venir', async () => {
      const { http } = await setup();
      expectAccounts(http).flush(page([]));
      expectTransactions(http, 5).flush(page([]));

      expect(await screen.findAllByText('Bientôt disponible')).toHaveLength(1);
      expect(screen.getByRole('heading', { name: 'Notifications' })).toBeTruthy();
    });
  });

  describe('administrateur', () => {
    beforeEach(() => isAdmin.set(true));

    function flushSummary(http: HttpTestingController, body: AccountsSummary = SUMMARY) {
      http.expectOne('/api/accounts/summary').flush(body);
    }

    it('affiche le profil et la vue de la banque', async () => {
      const { http } = await setup(() => of({ ...USER, role: 'ROLE_ADMIN' }));
      flushSummary(http);
      expectTransactions(http, 10).flush(page([]));

      expect(await screen.findByText('Administrateur')).toBeTruthy();
      expect(screen.getByRole('heading', { name: 'Vue de la banque' })).toBeTruthy();
      expect(screen.getByText('Clients actifs').nextElementSibling?.textContent).toBe('11');
      expect(screen.getByText('Clients suspendus').nextElementSibling?.textContent).toBe('1');
      expect(screen.getByText('Comptes ouverts').nextElementSibling?.textContent).toBe('20');
      expect(screen.getByText('Comptes clôturés').nextElementSibling?.textContent).toBe('3');
      expect(screen.getByText(/12\s345,67\s€/)).toBeTruthy();
      expect(screen.getByRole('link', { name: 'Clients' }).getAttribute('href')).toBe(
        '/administration/clients',
      );
      expect(screen.getByRole('link', { name: 'Tous les comptes' }).getAttribute('href')).toBe(
        '/comptes',
      );
      expect(screen.queryByRole('link', { name: 'Faire un virement' })).toBeNull();
    });

    it('affiche les 10 dernières opérations de la banque avec un lien vers le compte', async () => {
      const { http } = await setup();
      flushSummary(http);
      expectTransactions(http, 10).flush(
        page([{ ...TRANSACTION, type: 'TRANSFER_IN', direction: 'CREDIT' }]),
      );

      expect(
        await screen.findByRole('heading', { name: 'Dernières opérations de la banque' }),
      ).toBeTruthy();
      expect(screen.getByText('Virement reçu de FR76 9999 9000 0109 9999 9999 912')).toBeTruthy();
      expect(
        screen
          .getByRole('link', { name: /^Voir le compte, opération du .* : Virement reçu de/ })
          .getAttribute('href'),
      ).toBe('/comptes/a1');
      expect(screen.getByText(/\+20,00\s€/)).toBeTruthy();
    });

    it('isole une erreur de la vue de la banque', async () => {
      const { http } = await setup();
      http.expectOne('/api/accounts/summary').flush(null, { status: 500, statusText: 'Error' });
      expectTransactions(http, 10).flush(page([TRANSACTION]));

      expect((await screen.findByRole('alert')).textContent).toContain(
        "La vue de la banque n'a pas pu être chargée.",
      );
      expect(screen.getByText(/Virement émis/)).toBeTruthy();
    });

    it('isole une erreur des opérations de la banque', async () => {
      const { http } = await setup();
      flushSummary(http);
      expectTransactions(http, 10).flush(null, { status: 500, statusText: 'Error' });

      expect((await screen.findByRole('alert')).textContent).toContain(
        "Les opérations n'ont pas pu être chargées.",
      );
      expect(screen.getByText('Comptes ouverts')).toBeTruthy();
    });
  });
});
