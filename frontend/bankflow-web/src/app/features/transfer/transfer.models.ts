import { Currency } from '../accounts/accounts.models';
import { PerformedBy } from '../operations/operations.models';

export interface TransferRequest {
  sourceAccountId: string;
  /** Saisi librement par l'utilisateur : le service le normalise avant l'envoi. */
  destinationIban: string;
  /** Chaîne décimale à 2 chiffres (`"200.00"`). */
  amount: string;
  description: string | null;
}

export interface Transfer {
  id: string;
  sourceAccountId: string | null;
  sourceIban: string;
  destinationAccountId: string;
  destinationIban: string;
  amount: string;
  currency: Currency;
  description: string | null;
  status: 'PENDING' | 'COMPLETED' | 'REJECTED';
  performedBy: PerformedBy;
  createdAt: string;
  completedAt: string | null;
}

export const IBAN_PATTERN = /^[A-Z]{2}\d{2}[A-Z0-9]{10,30}$/;
export const INVALID_IBAN_MESSAGE =
  'Saisissez un IBAN valide, par exemple FR76 9999 0000 1012 3456 7890 12.';
export const CHOOSE_ACCOUNT_FIRST_MESSAGE =
  'Choisissez d’abord un compte depuis la fiche d’un client.';
