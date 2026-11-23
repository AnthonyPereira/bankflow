import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { render, screen } from '@testing-library/angular';
import userEvent from '@testing-library/user-event';
import { AuthService } from '../../core/auth/auth.service';
import { Account, PageResponse } from '../accounts/accounts.models';
import { Transfer } from './transfer.models';
import { CLOSED_SOURCE_MESSAGE, TransferPage } from './transfer';

const ACCOUNT: Account = {
  id: 'a1',
  accountNumber: 'FR7699999000010123456789012',
  customerId: 'c1',
  label: 'Courant',
  currency: 'EUR',
  balance: '1234.50',
  status: 'ACTIVE',
  createdAt: '2026-11-23T10:00:00Z',
  closedAt: null,
};
const SECOND_ACCOUNT: Account = { ...ACCOUNT, id: 'a2', label: 'Épargne', balance: '50.00' };
const CLOSED_ACCOUNT: Account = { ...ACCOUNT, id: 'a3', label: 'Ancien', status: 'CLOSED' };

const DESTINATION_IBAN = 'FR7699999000010999999999912';
const TRANSFER: Transfer = {
  id: 'tr1',
  sourceAccountId: 'a1',
  sourceIban: ACCOUNT.accountNumber,
  destinationAccountId: 'a9',
  destinationIban: DESTINATION_IBAN,
  amount: '200.00',
  currency: 'EUR',
  description: 'Loyer',
  status: 'COMPLETED',
  performedBy: 'CLIENT',
  createdAt: '2026-11-23T10:00:00Z',
  completedAt: '2026-11-23T10:00:00Z',
};

function page(content: Account[]): PageResponse<Account> {
  return { content, page: 0, size: 100, totalElements: content.length, totalPages: 1 };
}

describe('TransferPage', () => {
  const isAdmin = signal(false);

  async function setup(options: { admin?: boolean; from?: string; account?: Account } = {}) {
    isAdmin.set(options.admin ?? false);
    const { fixture } = await render(TransferPage, {
      inputs: options.from ? { from: options.from } : {},
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: AuthService, useValue: { isAdmin } },
      ],
    });
    const http = TestBed.inject(HttpTestingController);
    if (options.admin) {
      if (options.from) {
        http.expectOne(`/api/accounts/${options.from}`).flush(options.account ?? ACCOUNT);
      }
    } else {
      http
        .expectOne((r) => r.url === '/api/accounts')
        .flush(page([ACCOUNT, SECOND_ACCOUNT, CLOSED_ACCOUNT]));
    }
    await fixture.whenStable();
    return { http, user: userEvent.setup() };
  }

  function getField(name: string): HTMLInputElement | HTMLSelectElement {
    return document.querySelector(`[formControlName="${name}"]`) as HTMLInputElement;
  }

  async function fillForm(
    user: ReturnType<typeof userEvent.setup>,
    values: { iban?: string; amount?: string; description?: string } = {},
  ) {
    await user.clear(getField('destinationIban'));
    await user.type(
      getField('destinationIban'),
      values.iban ?? 'fr76 9999 9000 0109 9999 9999 912',
    );
    await user.clear(getField('amount'));
    await user.type(getField('amount'), values.amount ?? '200');
    if (values.description) {
      await user.type(getField('description'), values.description);
    }
  }

  async function goToReview(user: ReturnType<typeof userEvent.setup>) {
    await user.click(screen.getByRole('button', { name: 'Continuer' }));
    await screen.findByRole('heading', { name: '2. Vérifier le virement' });
  }

  it('propose seulement les comptes actifs et présélectionne celui de `from`', async () => {
    await setup({ from: 'a2' });

    const select = getField('sourceAccountId') as HTMLSelectElement;
    expect(select.value).toBe('a2');
    const labels = Array.from(select.options).map((option) => option.textContent?.trim());
    expect(labels.join('|')).toContain('Courant');
    expect(labels.join('|')).toContain('Épargne');
    expect(labels.join('|')).not.toContain('Ancien');
  });

  it("ignore un `from` qui n'est pas un compte actif", async () => {
    await setup({ from: 'a3' });

    expect((getField('sourceAccountId') as HTMLSelectElement).value).toBe('');
  });

  it('refuse un IBAN invalide et un montant invalide sans passer au récapitulatif', async () => {
    const { user } = await setup({ from: 'a1' });

    await fillForm(user, { iban: 'abc', amount: '0' });
    await user.click(screen.getByRole('button', { name: 'Continuer' }));

    expect(screen.getByText(/Saisissez un IBAN valide/)).toBeTruthy();
    expect(screen.getByText('Le montant doit être positif.')).toBeTruthy();
    expect(screen.queryByRole('heading', { name: '2. Vérifier le virement' })).toBeNull();
  });

  it('passe du formulaire au récapitulatif puis au résultat', async () => {
    const { http, user } = await setup({ from: 'a1' });

    await fillForm(user, { description: ' Loyer ' });
    await goToReview(user);

    expect(document.activeElement?.textContent).toContain('2. Vérifier le virement');
    expect(screen.getByText('FR76 9999 9000 0109 9999 9999 912')).toBeTruthy();
    expect(screen.getByText(/200,00\s€/)).toBeTruthy();
    expect(screen.getByText('Loyer')).toBeTruthy();

    await user.click(screen.getByRole('button', { name: 'Confirmer le virement' }));
    const post = http.expectOne('/api/transfers');
    expect(post.request.body).toEqual({
      sourceAccountId: 'a1',
      destinationIban: DESTINATION_IBAN,
      amount: '200.00',
      description: 'Loyer',
    });
    expect(post.request.headers.get('Idempotency-Key')).toBeTruthy();
    post.flush(TRANSFER, { status: 201, statusText: 'Created' });

    expect(await screen.findByRole('heading', { name: 'Virement effectué' })).toBeTruthy();
    expect(screen.getByText(/200,00\s€ vers FR76 9999 9000 0109 9999 9999 912/)).toBeTruthy();
    expect(screen.getByRole('link', { name: 'Voir le compte source' }).getAttribute('href')).toBe(
      '/comptes/a1',
    );
    http.expectOne((r) => r.url === '/api/accounts').flush(page([ACCOUNT]));
  });

  it('« Modifier » revient au formulaire en gardant la saisie', async () => {
    const { user } = await setup({ from: 'a1' });
    await fillForm(user, { description: 'Loyer' });
    await goToReview(user);

    await user.click(screen.getByRole('button', { name: 'Modifier' }));

    expect(await screen.findByRole('heading', { name: '1. Saisir le virement' })).toBeTruthy();
    expect(getField('destinationIban').value).toBe('fr76 9999 9000 0109 9999 9999 912');
    expect(getField('amount').value).toBe('200');
    expect(getField('description').value).toBe('Loyer');
    expect((getField('sourceAccountId') as HTMLSelectElement).value).toBe('a1');
  });

  it("n'envoie qu'une requête en cas de double clic", async () => {
    const { http, user } = await setup({ from: 'a1' });
    await fillForm(user);
    await goToReview(user);

    await user.dblClick(screen.getByRole('button', { name: 'Confirmer le virement' }));

    const post = http.expectOne('/api/transfers');
    post.flush(TRANSFER, { status: 201, statusText: 'Created' });
  });

  it('garde la clé après une erreur 5xx, en change après un 4xx ou une modification', async () => {
    const { http, user } = await setup({ from: 'a1' });
    await fillForm(user);
    await goToReview(user);

    await user.click(screen.getByRole('button', { name: 'Confirmer le virement' }));
    const first = http.expectOne('/api/transfers');
    const key = first.request.headers.get('Idempotency-Key');
    first.flush(null, { status: 503, statusText: 'Unavailable' });
    expect((await screen.findByRole('alert')).textContent).toContain('sans risque de doublon');

    await user.click(screen.getByRole('button', { name: 'Réessayer' }));
    const retry = http.expectOne('/api/transfers');
    expect(retry.request.headers.get('Idempotency-Key')).toBe(key);
    retry.flush({ code: 'INSUFFICIENT_FUNDS' }, { status: 409, statusText: 'Conflict' });
    expect((await screen.findByRole('alert')).textContent?.trim()).toBe(
      'Le solde est insuffisant.',
    );
    expect(screen.queryByRole('button', { name: 'Réessayer' })).toBeNull();

    // Un refus définitif (4xx) clôt l'intention : même formulaire, nouvelle clé.
    await user.click(screen.getByRole('button', { name: 'Modifier le virement' }));
    await goToReview(user);
    await user.click(screen.getByRole('button', { name: 'Confirmer le virement' }));
    const afterRefusal = http.expectOne('/api/transfers');
    const secondKey = afterRefusal.request.headers.get('Idempotency-Key');
    expect(secondKey).not.toBe(key);
    afterRefusal.flush(null, { status: 500, statusText: 'Error' });
    await screen.findByRole('button', { name: 'Réessayer' });

    // Une modification du formulaire est une nouvelle demande.
    await user.click(screen.getByRole('button', { name: 'Modifier le virement' }));
    await user.clear(getField('amount'));
    await user.type(getField('amount'), '210');
    await goToReview(user);
    await user.click(screen.getByRole('button', { name: 'Confirmer le virement' }));
    const afterEdit = http.expectOne('/api/transfers');
    expect(afterEdit.request.headers.get('Idempotency-Key')).not.toBe(secondKey);
    afterEdit.flush(TRANSFER, { status: 201, statusText: 'Created' });
  });

  it('traduit le code DESTINATION_ACCOUNT_NOT_FOUND', async () => {
    const { http, user } = await setup({ from: 'a1' });
    await fillForm(user);
    await goToReview(user);

    await user.click(screen.getByRole('button', { name: 'Confirmer le virement' }));
    http
      .expectOne('/api/transfers')
      .flush({ code: 'DESTINATION_ACCOUNT_NOT_FOUND' }, { status: 404, statusText: 'Not Found' });

    expect((await screen.findByRole('alert')).textContent?.trim()).toBe(
      'Aucun compte actif ne correspond à cet IBAN.',
    );
  });

  it('traduit le code CURRENCY_MISMATCH', async () => {
    const { http, user } = await setup({ from: 'a1' });
    await fillForm(user);
    await goToReview(user);

    await user.click(screen.getByRole('button', { name: 'Confirmer le virement' }));
    http
      .expectOne('/api/transfers')
      .flush({ code: 'CURRENCY_MISMATCH' }, { status: 409, statusText: 'Conflict' });

    expect((await screen.findByRole('alert')).textContent?.trim()).toBe(
      'Les deux comptes doivent avoir la même devise.',
    );
  });

  it('rattache une erreur de validation du serveur à son champ', async () => {
    const { http, user } = await setup({ from: 'a1' });
    await fillForm(user);
    await goToReview(user);

    await user.click(screen.getByRole('button', { name: 'Confirmer le virement' }));
    http.expectOne('/api/transfers').flush(
      {
        code: 'VALIDATION_ERROR',
        errors: {
          destinationIban: 'Le compte de destination doit être différent du compte source.',
        },
      },
      { status: 400, statusText: 'Bad Request' },
    );

    expect(
      await screen.findByText('Le compte de destination doit être différent du compte source.'),
    ).toBeTruthy();
    expect(screen.getByRole('heading', { name: '1. Saisir le virement' })).toBeTruthy();
  });

  it('propose de créer un compte quand le client n’en a aucun d’actif', async () => {
    isAdmin.set(false);
    await render(TransferPage, {
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: AuthService, useValue: { isAdmin } },
      ],
    });
    TestBed.inject(HttpTestingController)
      .expectOne((r) => r.url === '/api/accounts')
      .flush(page([CLOSED_ACCOUNT]));

    expect(await screen.findByText(/aucun compte actif/)).toBeTruthy();
  });

  it('affiche une erreur de chargement des comptes avec un bouton Réessayer', async () => {
    isAdmin.set(false);
    await render(TransferPage, {
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: AuthService, useValue: { isAdmin } },
      ],
    });
    const http = TestBed.inject(HttpTestingController);
    http
      .expectOne((r) => r.url === '/api/accounts')
      .flush(null, { status: 500, statusText: 'Error' });

    expect((await screen.findByRole('alert')).textContent).toContain('pas pu être chargé');
    expect(screen.getByRole('button', { name: 'Réessayer' })).toBeTruthy();
  });

  describe('administrateur', () => {
    it('sans `from`, demande de choisir un compte depuis la fiche d’un client', async () => {
      await setup({ admin: true });

      expect(
        screen.getByText('Choisissez d’abord un compte depuis la fiche d’un client.'),
      ).toBeTruthy();
      expect(screen.getByRole('link', { name: 'Voir les clients' }).getAttribute('href')).toBe(
        '/administration/clients',
      );
      expect(document.querySelector('form')).toBeNull();
    });

    it('part du compte indiqué par `from` et envoie le virement', async () => {
      const { http, user } = await setup({ admin: true, from: 'a1' });

      expect(await screen.findByText(/Vous agissez au nom du client/)).toBeTruthy();
      expect(document.querySelector('[formControlName="sourceAccountId"]')).toBeNull();
      await fillForm(user);
      await goToReview(user);
      await user.click(screen.getByRole('button', { name: 'Confirmer le virement' }));

      const post = http.expectOne('/api/transfers');
      expect(post.request.body.sourceAccountId).toBe('a1');
      post.flush({ ...TRANSFER, performedBy: 'ADMIN' }, { status: 201, statusText: 'Created' });
      expect(await screen.findByRole('heading', { name: 'Virement effectué' })).toBeTruthy();
      http.expectOne('/api/accounts/a1').flush(ACCOUNT);
    });

    it('refuse de partir d’un compte clôturé', async () => {
      await setup({ admin: true, from: 'a3', account: CLOSED_ACCOUNT });

      expect((await screen.findByRole('alert')).textContent?.trim()).toBe(CLOSED_SOURCE_MESSAGE);
    });
  });
});
