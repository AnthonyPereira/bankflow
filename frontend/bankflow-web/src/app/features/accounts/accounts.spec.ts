import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { render, screen } from '@testing-library/angular';
import userEvent from '@testing-library/user-event';
import { AuthService } from '../../core/auth/auth.service';
import { Account, PageResponse } from './accounts.models';
import { Accounts, OPENED_MESSAGE } from './accounts';

const ACCOUNT: Account = {
  id: 'a1',
  accountNumber: 'FR7699999000010123456789012',
  customerId: '12345678-aaaa-bbbb-cccc-1234567890ab',
  label: null,
  currency: 'EUR',
  balance: '1234.50',
  status: 'ACTIVE',
  createdAt: '2026-11-23T10:00:00Z',
  closedAt: null,
};

function page(content: Account[], totalElements = content.length): PageResponse<Account> {
  return { content, page: 0, size: 20, totalElements, totalPages: Math.ceil(totalElements / 20) };
}

describe('Accounts', () => {
  const isAdmin = signal(false);

  async function setup(response: PageResponse<Account>, admin = false) {
    isAdmin.set(admin);
    await render(Accounts, {
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: AuthService, useValue: { isAdmin } },
      ],
    });
    const http = TestBed.inject(HttpTestingController);
    http.expectOne((r) => r.url === '/api/accounts').flush(response);
    return { http, user: userEvent.setup() };
  }

  it('affiche les comptes avec IBAN groupé, solde formaté et libellé par défaut', async () => {
    await setup(page([ACCOUNT, { ...ACCOUNT, id: 'a2', label: 'Vacances', status: 'CLOSED' }]));

    expect(await screen.findByText('Compte courant')).toBeTruthy();
    expect(screen.getByText('Vacances')).toBeTruthy();
    expect(screen.getAllByText('FR76 9999 9000 0101 2345 6789 012')).toHaveLength(2);
    expect(screen.getAllByText(/1\s234,50\s€/).length).toBe(2);
    expect(screen.getByText('Clôturé')).toBeTruthy();
    expect(screen.queryByRole('columnheader', { name: 'Client' })).toBeNull();
  });

  it("affiche l'état vide", async () => {
    await setup(page([]));

    expect(await screen.findByText("Vous n'avez pas encore de compte.")).toBeTruthy();
  });

  it('montre la colonne Client à un administrateur', async () => {
    await setup(page([ACCOUNT]), true);

    expect(await screen.findByRole('columnheader', { name: 'Client' })).toBeTruthy();
    expect(screen.getByText('12345678')).toBeTruthy();
  });

  it('affiche la pagination au-delà de 20 comptes seulement', async () => {
    await setup(page([ACCOUNT], 42));

    expect(await screen.findByRole('group', { name: 'Pagination des comptes' })).toBeTruthy();
  });

  it("n'affiche pas la pagination jusqu'à 20 comptes", async () => {
    await setup(page([ACCOUNT], 20));

    await screen.findByText('Compte courant');
    expect(screen.queryByRole('group', { name: 'Pagination des comptes' })).toBeNull();
  });

  it('ouvre un compte puis rafraîchit la liste', async () => {
    const { http, user } = await setup(page([]));
    await screen.findByText("Vous n'avez pas encore de compte.");

    await user.selectOptions(screen.getByLabelText('Devise'), 'USD');
    await user.type(screen.getByLabelText('Libellé (facultatif)'), '  Voyage ');
    await user.click(screen.getByRole('button', { name: 'Ouvrir le compte' }));

    const post = http.expectOne('/api/accounts');
    expect(post.request.method).toBe('POST');
    expect(post.request.body).toEqual({ currency: 'USD', label: 'Voyage' });
    post.flush(
      { ...ACCOUNT, currency: 'USD', label: 'Voyage' },
      { status: 201, statusText: 'Created' },
    );

    expect((await screen.findByRole('status')).textContent).toContain(OPENED_MESSAGE);
    http
      .expectOne((r) => r.url === '/api/accounts' && r.method === 'GET')
      .flush(page([{ ...ACCOUNT, currency: 'USD', label: 'Voyage' }]));
    expect(await screen.findByText('Voyage')).toBeTruthy();
  });

  it('envoie un libellé vide comme null', async () => {
    const { http, user } = await setup(page([]));

    await user.click(screen.getByRole('button', { name: 'Ouvrir le compte' }));

    expect(http.expectOne('/api/accounts').request.body).toEqual({ currency: 'EUR', label: null });
  });

  it.each([
    [
      409,
      'CUSTOMER_NOT_READY',
      'Votre profil est en cours de création, réessayez dans quelques secondes.',
    ],
    [409, 'ACCOUNT_LIMIT_REACHED', 'Vous avez atteint la limite de 5 comptes ouverts.'],
    [403, 'CUSTOMER_SUSPENDED', 'Votre profil est suspendu : contactez un conseiller.'],
  ])('traduit l’erreur %i %s', async (status, code, message) => {
    const { http, user } = await setup(page([]));

    await user.click(screen.getByRole('button', { name: 'Ouvrir le compte' }));
    http
      .expectOne('/api/accounts')
      .flush({ code, detail: 'autre' }, { status, statusText: 'Erreur' });

    expect((await screen.findByRole('alert')).textContent).toContain(message);
  });

  it('refuse un libellé de plus de 50 caractères', async () => {
    const { http, user } = await setup(page([]));
    const input = screen.getByLabelText('Libellé (facultatif)');
    input.removeAttribute('maxlength');

    await user.click(input);
    await user.paste('a'.repeat(51));
    await user.click(screen.getByRole('button', { name: 'Ouvrir le compte' }));

    expect(await screen.findByText(/ne peut pas dépasser 50/)).toBeTruthy();
    http.expectNone('/api/accounts');
  });
});
