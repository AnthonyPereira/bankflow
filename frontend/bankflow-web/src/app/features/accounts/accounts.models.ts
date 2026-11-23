export type Currency = 'EUR' | 'USD';
export type AccountStatus = 'ACTIVE' | 'CLOSED';

export interface Account {
  id: string;
  accountNumber: string;
  customerId: string;
  label: string | null;
  currency: Currency;
  balance: string;
  status: AccountStatus;
  createdAt: string;
  closedAt: string | null;
}

export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface OpenAccountRequest {
  currency: Currency;
  label: string | null;
}

export const CURRENCIES: readonly Currency[] = ['EUR', 'USD'];
export const DEFAULT_ACCOUNT_LABEL = 'Compte sans libellé';

/** Regroupe l'IBAN par blocs de quatre caractères pour la lecture. */
export function formatIban(accountNumber: string): string {
  return accountNumber.replace(/\s/g, '').replace(/(.{4})(?=.)/g, '$1 ');
}

/** Nombre de comptes chargés pour un choix ou un résumé : au-delà, la liste est partielle. */
export const MAX_ACCOUNT_CHOICES = 100;
export const PARTIAL_ACCOUNTS_MESSAGE = `Seuls les ${MAX_ACCOUNT_CHOICES} comptes les plus récents sont affichés.`;

/** Les comptes actifs d'une page (liste vide tant qu'elle n'est pas chargée). */
export function getActiveAccounts(page: PageResponse<Account> | undefined): Account[] {
  return page ? page.content.filter((account) => account.status === 'ACTIVE') : [];
}

export function isPartialPage(page: PageResponse<Account> | undefined): boolean {
  return !!page && page.totalElements > MAX_ACCOUNT_CHOICES;
}

/** Forme envoyée à l'API : sans espaces, en majuscules. */
export function normalizeIban(iban: string): string {
  return iban.replace(/\s/g, '').toUpperCase();
}

export interface CurrencyTotal {
  currency: Currency;
  balance: string;
  openAccounts: number;
}

/** Réponse de `GET /accounts/summary` (administrateur). */
export interface AccountsSummary {
  customers: { total: number; active: number; suspended: number };
  accounts: { open: number; closed: number };
  totalsByCurrency: CurrencyTotal[];
}

/** Convertit un solde décimal en centimes, sans passer par un nombre à virgule flottante. */
function getCents(balance: string): bigint {
  const [units, decimals = ''] = balance.split('.');
  const cents =
    BigInt(units.replace('-', '') || '0') * 100n +
    BigInt(decimals.padEnd(2, '0').slice(0, 2) || '0');
  return units.startsWith('-') ? -cents : cents;
}

/** Total des soldes par devise (chaînes à 2 décimales), triées par devise. */
export function getBalanceTotals(accounts: readonly Account[]): CurrencyTotal[] {
  const totals = new Map<Currency, { cents: bigint; openAccounts: number }>();
  for (const account of accounts) {
    const total = totals.get(account.currency) ?? { cents: 0n, openAccounts: 0 };
    total.cents += getCents(account.balance);
    total.openAccounts += 1;
    totals.set(account.currency, total);
  }
  return [...totals.entries()]
    .sort(([a], [b]) => a.localeCompare(b))
    .map(([currency, { cents, openAccounts }]) => {
      const abs = cents < 0n ? -cents : cents;
      const sign = cents < 0n ? '-' : '';
      return {
        currency,
        balance: `${sign}${abs / 100n}.${String(abs % 100n).padStart(2, '0')}`,
        openAccounts,
      };
    });
}

/** Un administrateur voit les comptes de tous les clients : le titre le dit, partout où il apparaît. */
export function getAccountsTitle(isAdmin: boolean): string {
  return isAdmin ? 'Tous les comptes' : 'Mes comptes';
}
