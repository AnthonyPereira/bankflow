import { Currency } from '../accounts/accounts.models';

export type TransactionType =
  'DEPOSIT' | 'WITHDRAWAL' | 'TRANSFER_IN' | 'TRANSFER_OUT' | 'COMPENSATION';
export type PerformedBy = 'CLIENT' | 'ADMIN';

export interface Transaction {
  id: string;
  accountId: string;
  type: TransactionType;
  amount: string;
  currency: Currency;
  balanceAfter: string;
  transferId: string | null;
  compensatesTransactionId: string | null;
  compensatedByTransactionId: string | null;
  description: string | null;
  direction: 'CREDIT' | 'DEBIT';
  performedBy: PerformedBy;
  createdAt: string;
}

/** Taille de page des historiques d'opérations (une seule source pour les deux écrans). */
export const TRANSACTIONS_PAGE_SIZE = 20;

/** Signe affiché devant le montant : le sens vient de l'API, y compris pour une annulation. */
export function getAmountSign(transaction: Transaction): string {
  return transaction.direction === 'CREDIT' ? '+' : '−';
}

export interface TransactionFilters {
  accountId?: string;
  type?: TransactionType;
  from?: string;
  to?: string;
}

export interface OperationRequest {
  amount: string;
  description: string | null;
}

export const TRANSACTION_TYPES: readonly TransactionType[] = [
  'DEPOSIT',
  'WITHDRAWAL',
  'TRANSFER_IN',
  'TRANSFER_OUT',
  'COMPENSATION',
];

export const TYPE_LABELS: Record<TransactionType, string> = {
  DEPOSIT: 'Dépôt',
  WITHDRAWAL: 'Retrait',
  TRANSFER_IN: 'Virement reçu',
  TRANSFER_OUT: 'Virement émis',
  COMPENSATION: 'Annulation',
};

/** Seuls un dépôt et un retrait se compensent, une seule fois. */
export function canCompensate(transaction: Transaction): boolean {
  return (
    (transaction.type === 'DEPOSIT' || transaction.type === 'WITHDRAWAL') &&
    transaction.compensatedByTransactionId === null
  );
}

/** Un administrateur voit les opérations de tous les clients : le titre le dit, partout où il apparaît. */
export function getOperationsTitle(isAdmin: boolean): string {
  return isAdmin ? 'Toutes les opérations' : 'Mes opérations';
}
