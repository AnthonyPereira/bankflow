import { Account, getBalanceTotals, normalizeIban } from './accounts.models';

function build(currency: 'EUR' | 'USD', balance: string): Account {
  return {
    id: `${currency}-${balance}`,
    accountNumber: 'FR7699999000010123456789012',
    customerId: 'c1',
    label: null,
    currency,
    balance,
    status: 'ACTIVE',
    createdAt: '2026-11-23T10:00:00Z',
    closedAt: null,
  };
}

describe('normalizeIban', () => {
  it('retire les espaces et passe en majuscules', () => {
    expect(normalizeIban(' fr76 9999 9000 0101 2345 6789 012 ')).toBe(
      'FR7699999000010123456789012',
    );
  });
});

describe('getBalanceTotals', () => {
  it('additionne les soldes par devise en centimes exacts, triés par devise', () => {
    const totals = getBalanceTotals([
      build('USD', '10.00'),
      build('EUR', '0.10'),
      build('EUR', '0.20'),
      build('EUR', '9999999999999.99'),
    ]);

    expect(totals).toEqual([
      { currency: 'EUR', balance: '10000000000000.29', openAccounts: 3 },
      { currency: 'USD', balance: '10.00', openAccounts: 1 },
    ]);
  });

  it('renvoie une liste vide sans compte', () => {
    expect(getBalanceTotals([])).toEqual([]);
  });
});
