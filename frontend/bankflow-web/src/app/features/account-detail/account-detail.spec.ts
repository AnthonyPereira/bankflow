import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { render, screen } from '@testing-library/angular';
import userEvent from '@testing-library/user-event';
import { AuthService } from '../../core/auth/auth.service';
import { Account, PageResponse } from '../accounts/accounts.models';
import { Transaction } from '../operations/operations.models';
import {
  AccountDetail,
  CANCELLED_MESSAGE,
  CLOSED_MESSAGE,
  SUCCESS_MESSAGES,
} from './account-detail';

const ACCOUNT: Account = {
  id: 'a1',
  accountNumber: 'FR7699999000010123456789012',
  customerId: 'c1',
  label: 'Vacances',
  currency: 'EUR',
  balance: '1234.50',
  status: 'ACTIVE',
  createdAt: '2026-11-23T10:00:00Z',
  closedAt: null,
};

const TRANSACTION: Transaction = {
  id: 't1',
  accountId: 'a1',
  type: 'DEPOSIT',
  amount: '150.00',
  currency: 'EUR',
  balanceAfter: '1234.50',
  transferId: null,
  compensatesTransactionId: null,
  compensatedByTransactionId: null,
  description: 'Salaire',
  direction: 'CREDIT',
  performedBy: 'CLIENT',
  createdAt: '2026-11-23T10:00:00Z',
};

function page(content: Transaction[], totalElements = content.length): PageResponse<Transaction> {
  return { content, page: 0, size: 20, totalElements, totalPages: Math.ceil(totalElements / 20) };
}

describe('AccountDetail', () => {
  const isAdmin = signal(false);

  async function setup(
    options: {
      admin?: boolean;
      account?: Account;
      transactions?: Transaction[];
      total?: number;
    } = {},
  ) {
    isAdmin.set(options.admin ?? false);
    const { fixture } = await render(AccountDetail, {
      inputs: { id: 'a1' },
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: AuthService, useValue: { isAdmin } },
      ],
    });
    const http = TestBed.inject(HttpTestingController);
    http.expectOne('/api/accounts/a1').flush(options.account ?? ACCOUNT);
    http
      .expectOne((r) => r.url === '/api/transactions')
      .flush(page(options.transactions ?? [TRANSACTION], options.total));
    await screen.findByRole('heading', { level: 1, name: options.account?.label ?? 'Vacances' });
    return { http, fixture, user: userEvent.setup() };
  }

  function getDepositField(label: string): HTMLInputElement {
    const form = screen.getByRole('heading', { name: 'Déposer de l’argent' }).nextElementSibling;
    return (form as HTMLElement).querySelector(`[formControlName="${label}"]`) as HTMLInputElement;
  }

  async function submitDeposit(user: ReturnType<typeof userEvent.setup>, amount: string) {
    await user.clear(getDepositField('amount'));
    await user.type(getDepositField('amount'), amount);
    await user.click(screen.getByRole('button', { name: 'Déposer' }));
  }

  it('affiche l’en-tête du compte et l’historique', async () => {
    await setup();

    expect(screen.getByText('FR76 9999 9000 0101 2345 6789 012')).toBeTruthy();
    expect(screen.getByText('Actif')).toBeTruthy();
    expect(screen.getAllByText(/1\s234,50\s€/).length).toBeGreaterThan(0);
    expect(screen.getByText('Salaire')).toBeTruthy();
    expect(screen.getByRole('link', { name: '← Retour aux comptes' })).toBeTruthy();
  });

  it('affiche « Compte introuvable. » sur un 404', async () => {
    isAdmin.set(false);
    await render(AccountDetail, {
      inputs: { id: 'a1' },
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: AuthService, useValue: { isAdmin } },
      ],
    });
    TestBed.inject(HttpTestingController)
      .expectOne('/api/accounts/a1')
      .flush({ code: 'ACCOUNT_NOT_FOUND' }, { status: 404, statusText: 'Not Found' });

    expect((await screen.findByRole('alert')).textContent).toBe('Compte introuvable.');
  });

  it('propose Dépôt et Retrait, sans annulation, à un client', async () => {
    await setup();

    expect(screen.getByRole('button', { name: 'Déposer' })).toBeTruthy();
    expect(screen.getByRole('button', { name: 'Retirer' })).toBeTruthy();
    expect(screen.queryByRole('button', { name: /Annuler l'opération/ })).toBeNull();
  });

  it('affiche l’annulation à l’administrateur seulement sur une opération non compensée', async () => {
    await setup({
      admin: true,
      transactions: [
        TRANSACTION,
        { ...TRANSACTION, id: 't2', compensatedByTransactionId: 'c2' },
        { ...TRANSACTION, id: 't3', type: 'TRANSFER_OUT' },
      ],
    });

    expect(screen.getAllByRole('button', { name: /Annuler l'opération/ })).toHaveLength(1);
    expect(screen.getByText('Annulée')).toBeTruthy();
    expect(screen.getByRole('button', { name: 'Déposer' })).toBeTruthy();
  });

  it('nomme la contrepartie des virements dans l’historique', async () => {
    await setup({
      transactions: [
        {
          ...TRANSACTION,
          id: 't2',
          type: 'TRANSFER_IN',
          counterpartyIban: 'FR7699999000010999999999912',
        },
        {
          ...TRANSACTION,
          id: 't3',
          type: 'TRANSFER_OUT',
          direction: 'DEBIT',
          counterpartyIban: 'FR7699999000010888888888812',
        },
      ],
    });

    expect(screen.getByText('Virement reçu de FR76 9999 9000 0109 9999 9999 912')).toBeTruthy();
    expect(screen.getByText('Virement émis vers FR76 9999 9000 0108 8888 8888 812')).toBeTruthy();
  });

  it('propose le virement depuis un compte actif, selon le rôle', async () => {
    await setup();
    expect(screen.getByRole('link', { name: 'Faire un virement' }).getAttribute('href')).toBe(
      '/virement?from=a1',
    );
  });

  it('propose « Faire un virement depuis ce compte » à un administrateur', async () => {
    await setup({ admin: true });
    expect(
      screen.getByRole('link', { name: 'Faire un virement depuis ce compte' }).getAttribute('href'),
    ).toBe('/virement?from=a1');
  });

  it('ne propose pas de virement depuis un compte clôturé', async () => {
    await setup({ account: { ...ACCOUNT, status: 'CLOSED' } });
    expect(screen.queryByRole('link', { name: /Faire un virement/ })).toBeNull();
  });

  it('signale une opération effectuée par un conseiller', async () => {
    await setup({ transactions: [{ ...TRANSACTION, performedBy: 'ADMIN' }] });

    expect(screen.getByText('Effectuée par un conseiller')).toBeTruthy();
  });

  it('envoie un dépôt avec montant à virgule, clé d’idempotence, puis rafraîchit', async () => {
    const { http, user } = await setup();

    await user.type(getDepositField('description'), '  Prime ');
    await submitDeposit(user, '150,5');

    const post = http.expectOne('/api/accounts/a1/deposits');
    expect(post.request.body).toEqual({ amount: '150.50', description: 'Prime' });
    expect(post.request.headers.get('Idempotency-Key')).toBeTruthy();
    expect(screen.getByRole('button', { name: 'Envoi…' }).hasAttribute('disabled')).toBe(true);
    post.flush({ ...TRANSACTION, amount: '150.50' }, { status: 201, statusText: 'Created' });

    expect(await screen.findByText(SUCCESS_MESSAGES.deposit)).toBeTruthy();
    http.expectOne('/api/accounts/a1').flush({ ...ACCOUNT, balance: '1385.00' });
    http.expectOne((r) => r.url === '/api/transactions').flush(page([TRANSACTION]));
    expect(await screen.findAllByText(/1\s385,00\s€/)).toBeTruthy();
    expect(getDepositField('amount').value).toBe('');
  });

  it('envoie un retrait sur le bon chemin', async () => {
    const { http, user } = await setup();
    const form = screen.getByRole('heading', { name: 'Retirer de l’argent' })
      .nextElementSibling as HTMLElement;

    await user.type(form.querySelector('[formControlName="amount"]') as HTMLInputElement, '20');
    await user.click(screen.getByRole('button', { name: 'Retirer' }));

    const post = http.expectOne('/api/accounts/a1/withdrawals');
    expect(post.request.body).toEqual({ amount: '20.00', description: null });
    post.flush(TRANSACTION, { status: 201, statusText: 'Created' });
  });

  it('réutilise la même clé après une erreur réseau puis en change après un succès', async () => {
    const { http, user } = await setup();

    await submitDeposit(user, '50');
    const first = http.expectOne('/api/accounts/a1/deposits');
    const key = first.request.headers.get('Idempotency-Key');
    first.flush(null, { status: 503, statusText: 'Unavailable' });
    expect((await screen.findByRole('alert')).textContent).toContain('sans risque de doublon');

    await user.click(screen.getByRole('button', { name: 'Déposer' }));
    const retry = http.expectOne('/api/accounts/a1/deposits');
    expect(retry.request.headers.get('Idempotency-Key')).toBe(key);
    retry.flush(TRANSACTION, { status: 201, statusText: 'Created' });
    await screen.findByText(SUCCESS_MESSAGES.deposit);
    http.expectOne('/api/accounts/a1').flush(ACCOUNT);
    http.expectOne((r) => r.url === '/api/transactions').flush(page([TRANSACTION]));

    await submitDeposit(user, '50');
    const next = http.expectOne('/api/accounts/a1/deposits');
    expect(next.request.headers.get('Idempotency-Key')).not.toBe(key);
    next.flush(TRANSACTION, { status: 201, statusText: 'Created' });
  });

  it('change de clé quand le formulaire est modifié après une erreur', async () => {
    const { http, user } = await setup();

    await submitDeposit(user, '50');
    const first = http.expectOne('/api/accounts/a1/deposits');
    const key = first.request.headers.get('Idempotency-Key');
    first.flush(null, { status: 0, statusText: 'Unknown' });
    await screen.findByRole('alert');

    await user.type(getDepositField('amount'), '0');
    await user.click(screen.getByRole('button', { name: 'Déposer' }));

    const second = http.expectOne('/api/accounts/a1/deposits');
    expect(second.request.headers.get('Idempotency-Key')).not.toBe(key);
    second.flush(TRANSACTION, { status: 201, statusText: 'Created' });
  });

  it('refuse un montant invalide sans appeler le serveur', async () => {
    const { http, user } = await setup();

    await submitDeposit(user, '10000,01');

    expect(
      await screen.findByText('Le montant maximal par opération est de 10 000,00.'),
    ).toBeTruthy();
    http.expectNone('/api/accounts/a1/deposits');
  });

  it('affiche errors.amount du serveur sous le champ', async () => {
    const { http, user } = await setup();

    await submitDeposit(user, '10');
    http
      .expectOne('/api/accounts/a1/deposits')
      .flush(
        { code: 'VALIDATION_ERROR', errors: { amount: 'Le montant doit être positif.' } },
        { status: 400, statusText: 'Bad Request' },
      );

    expect(await screen.findByText('Le montant doit être positif.')).toBeTruthy();
    expect(document.activeElement).toBe(getDepositField('amount'));
  });

  it.each([
    ['INSUFFICIENT_FUNDS', 'Le solde est insuffisant.'],
    ['CUSTOMER_SUSPENDED', "Ce client est suspendu : aucune opération n'est possible."],
    ['ACCOUNT_CLOSED', 'Ce compte est clôturé.'],
  ])('traduit l’erreur %s', async (code, message) => {
    const { http, user } = await setup();

    await submitDeposit(user, '10');
    http
      .expectOne('/api/accounts/a1/deposits')
      .flush({ code }, { status: 409, statusText: 'Conflict' });

    expect((await screen.findByRole('alert')).textContent).toContain(message);
  });

  it('désactive la clôture tant que le solde n’est pas nul', async () => {
    await setup();

    const user = userEvent.setup();
    const button = screen.getByRole('button', { name: 'Clôturer le compte' });

    expect(button.getAttribute('aria-disabled')).toBe('true');
    expect(button.getAttribute('aria-describedby')).toBe('close-hint');
    expect(screen.getByText('Le solde doit être à zéro pour clôturer le compte.')).toBeTruthy();
    await user.click(button);
    expect(screen.queryByRole('button', { name: 'Confirmer la clôture' })).toBeNull();
  });

  it('fait suivre le montant d’un signe + ou − selon le sens', async () => {
    await setup({
      transactions: [
        TRANSACTION,
        { ...TRANSACTION, id: 't2', type: 'WITHDRAWAL', direction: 'DEBIT', amount: '20.00' },
      ],
    });

    const credit = screen.getByText(/^\+150,00\s€$/);
    const debit = screen.getByText(/^−20,00\s€$/);
    expect(credit.classList.contains('credit')).toBe(true);
    expect(debit.classList.contains('debit')).toBe(true);
  });

  it('repart de zéro quand le compte affiché change', async () => {
    const { http, user, fixture } = await setup();
    await submitDeposit(user, '10');
    http
      .expectOne('/api/accounts/a1/deposits')
      .flush({ code: 'INSUFFICIENT_FUNDS' }, { status: 409, statusText: 'Conflict' });
    await screen.findByRole('alert');

    fixture.componentRef.setInput('id', 'a2');
    fixture.detectChanges();

    const account = await vi.waitFor(() => http.expectOne('/api/accounts/a2'));
    expect(screen.queryByRole('alert')).toBeNull();
    account.flush({ ...ACCOUNT, id: 'a2' });
    http.expectOne((r) => r.url === '/api/transactions').flush(page([]));
    await screen.findByRole('heading', { level: 1, name: 'Vacances' });
    expect(getDepositField('amount').value).toBe('');
  });

  it('demande une nouvelle clé après un refus 4xx', async () => {
    const { http, user } = await setup();

    await submitDeposit(user, '10');
    const first = http.expectOne('/api/accounts/a1/deposits');
    const key = first.request.headers.get('Idempotency-Key');
    first.flush({ code: 'INSUFFICIENT_FUNDS' }, { status: 409, statusText: 'Conflict' });
    await screen.findByRole('alert');

    await user.click(screen.getByRole('button', { name: 'Déposer' }));
    const second = http.expectOne('/api/accounts/a1/deposits');
    expect(second.request.headers.get('Idempotency-Key')).not.toBe(key);
    second.flush(TRANSACTION, { status: 201, statusText: 'Created' });
  });

  it('demande une nouvelle clé après IDEMPOTENCY_KEY_REUSED', async () => {
    const { http, user } = await setup();

    await submitDeposit(user, '10');
    const first = http.expectOne('/api/accounts/a1/deposits');
    const key = first.request.headers.get('Idempotency-Key');
    first.flush({ code: 'IDEMPOTENCY_KEY_REUSED' }, { status: 422, statusText: 'Unprocessable' });
    expect((await screen.findByRole('alert')).textContent).toContain('recommencez');

    await user.click(screen.getByRole('button', { name: 'Déposer' }));
    const second = http.expectOne('/api/accounts/a1/deposits');
    expect(second.request.headers.get('Idempotency-Key')).not.toBe(key);
    second.flush(TRANSACTION, { status: 201, statusText: 'Created' });
  });

  it('garde la même clé après une erreur 5xx', async () => {
    const { http, user } = await setup();

    await submitDeposit(user, '10');
    const first = http.expectOne('/api/accounts/a1/deposits');
    const key = first.request.headers.get('Idempotency-Key');
    first.flush(null, { status: 500, statusText: 'Server Error' });
    await screen.findByRole('alert');

    await user.click(screen.getByRole('button', { name: 'Déposer' }));
    const second = http.expectOne('/api/accounts/a1/deposits');
    expect(second.request.headers.get('Idempotency-Key')).toBe(key);
    second.flush(TRANSACTION, { status: 201, statusText: 'Created' });
  });

  it('n’envoie qu’une requête en cas de double clic', async () => {
    const { http, user } = await setup();

    await user.type(getDepositField('amount'), '10');
    await user.dblClick(screen.getByRole('button', { name: 'Déposer' }));

    http.expectOne('/api/accounts/a1/deposits').flush(TRANSACTION);
  });

  it('recharge solde et historique quand un dépôt est refusé car le compte est clôturé', async () => {
    const { http, user } = await setup();

    await submitDeposit(user, '10');
    http
      .expectOne('/api/accounts/a1/deposits')
      .flush({ code: 'ACCOUNT_CLOSED' }, { status: 409, statusText: 'Conflict' });

    const account = await vi.waitFor(() => http.expectOne('/api/accounts/a1'));
    account.flush({ ...ACCOUNT, status: 'CLOSED' });
    http.expectOne((r) => r.url === '/api/transactions').flush(page([TRANSACTION]));
    expect(await screen.findByText('Clôturé')).toBeTruthy();
  });

  it('recharge l’écran quand une annulation est refusée car déjà annulée', async () => {
    const { http, user } = await setup({ admin: true });

    await user.click(screen.getByRole('button', { name: /Annuler l'opération/ }));
    await user.type(await screen.findByLabelText("Motif de l'annulation"), 'Erreur de saisie');
    await user.click(screen.getByRole('button', { name: "Confirmer l'annulation" }));
    http
      .expectOne('/api/transactions/t1/compensations')
      .flush({ code: 'ALREADY_COMPENSATED' }, { status: 409, statusText: 'Conflict' });

    const account = await vi.waitFor(() => http.expectOne('/api/accounts/a1'));
    account.flush(ACCOUNT);
    http
      .expectOne((r) => r.url === '/api/transactions')
      .flush(page([{ ...TRANSACTION, compensatedByTransactionId: 'c1' }]));
    expect(await screen.findByText('Annulée')).toBeTruthy();
  });

  it('clôture un compte à zéro après confirmation', async () => {
    const { http, user } = await setup({ account: { ...ACCOUNT, balance: '0.00' } });

    await user.click(screen.getByRole('button', { name: 'Clôturer le compte' }));
    http.expectNone('/api/accounts/a1/close');
    await user.click(await screen.findByRole('button', { name: 'Confirmer la clôture' }));

    http
      .expectOne('/api/accounts/a1/close')
      .flush({ ...ACCOUNT, balance: '0.00', status: 'CLOSED' });
    expect(await screen.findByText(CLOSED_MESSAGE)).toBeTruthy();
    http.expectOne('/api/accounts/a1').flush({ ...ACCOUNT, balance: '0.00', status: 'CLOSED' });
    expect(await screen.findByText('Clôturé')).toBeTruthy();
    expect(screen.queryByRole('button', { name: 'Déposer' })).toBeNull();
  });

  it('renonce à la clôture', async () => {
    const { http, user } = await setup({ account: { ...ACCOUNT, balance: '0.00' } });

    await user.click(screen.getByRole('button', { name: 'Clôturer le compte' }));
    await user.click(await screen.findByRole('button', { name: 'Annuler' }));

    expect(screen.getByRole('button', { name: 'Clôturer le compte' })).toBeTruthy();
    http.expectNone('/api/accounts/a1/close');
  });

  it('affiche l’erreur de clôture', async () => {
    const { http, user } = await setup({ account: { ...ACCOUNT, balance: '0.00' } });

    await user.click(screen.getByRole('button', { name: 'Clôturer le compte' }));
    await user.click(await screen.findByRole('button', { name: 'Confirmer la clôture' }));
    http
      .expectOne('/api/accounts/a1/close')
      .flush({ code: 'ACCOUNT_BALANCE_NOT_ZERO' }, { status: 409, statusText: 'Conflict' });

    expect((await screen.findByRole('alert')).textContent).toContain(
      'Le solde doit être à zéro pour clôturer le compte.',
    );
  });

  it('annule une opération avec un motif, côté administrateur', async () => {
    const { http, user } = await setup({ admin: true });

    await user.click(screen.getByRole('button', { name: /Annuler l'opération/ }));
    await user.type(await screen.findByLabelText("Motif de l'annulation"), 'court');
    await user.click(screen.getByRole('button', { name: "Confirmer l'annulation" }));
    expect(await screen.findByText('Le motif doit contenir de 10 à 200 caractères.')).toBeTruthy();
    http.expectNone('/api/transactions/t1/compensations');

    await user.type(screen.getByLabelText("Motif de l'annulation"), ' et plus long');
    await user.click(screen.getByRole('button', { name: "Confirmer l'annulation" }));

    const post = http.expectOne('/api/transactions/t1/compensations');
    expect(post.request.body).toEqual({ reason: 'court et plus long' });
    post.flush(
      { ...TRANSACTION, id: 'c1', type: 'COMPENSATION' },
      { status: 201, statusText: 'Created' },
    );
    expect(await screen.findByText(CANCELLED_MESSAGE)).toBeTruthy();
    http.expectOne('/api/accounts/a1').flush(ACCOUNT);
    http
      .expectOne((r) => r.url === '/api/transactions')
      .flush(page([{ ...TRANSACTION, compensatedByTransactionId: 'c1' }]));
    expect(await screen.findByText('Annulée')).toBeTruthy();
  });

  it('affiche l’erreur d’une annulation refusée', async () => {
    const { http, user } = await setup({ admin: true });

    await user.click(screen.getByRole('button', { name: /Annuler l'opération/ }));
    await user.type(await screen.findByLabelText("Motif de l'annulation"), 'Erreur de saisie');
    await user.click(screen.getByRole('button', { name: "Confirmer l'annulation" }));
    http
      .expectOne('/api/transactions/t1/compensations')
      .flush({ code: 'ALREADY_COMPENSATED' }, { status: 409, statusText: 'Conflict' });

    expect((await screen.findByRole('alert')).textContent).toContain(
      'Cette opération a déjà été annulée.',
    );
  });

  it('pagine l’historique', async () => {
    const { http, user } = await setup({ total: 42 });

    expect(screen.getByText('1 – 20 sur 42')).toBeTruthy();
    await user.click(screen.getByRole('button', { name: 'Page suivante' }));

    const request = http.expectOne((r) => r.url === '/api/transactions');
    expect(request.request.params.get('page')).toBe('1');
    expect(request.request.params.get('accountId')).toBe('a1');
    request.flush({ ...page([TRANSACTION], 42), page: 1 });
  });
});
