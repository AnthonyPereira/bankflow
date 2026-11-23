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

/** Un administrateur voit les comptes de tous les clients : le titre le dit, partout où il apparaît. */
export function getAccountsTitle(isAdmin: boolean): string {
  return isAdmin ? 'Tous les comptes' : 'Mes comptes';
}
