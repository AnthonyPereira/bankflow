import { PageResponse } from '../accounts/accounts.models';
import { Customer, CustomerStatus } from '../profile/profile.models';

export type { Customer, CustomerStatus, PageResponse };

/** Filtre de statut de la liste : `ALL` n'envoie aucun paramètre. */
export type CustomerStatusFilter = CustomerStatus | 'ALL';

export interface CustomerSearch {
  page: number;
  size: number;
  status: CustomerStatusFilter;
  term: string;
}

export const STATUS_FILTERS: readonly { value: CustomerStatusFilter; label: string }[] = [
  { value: 'ALL', label: 'Tous' },
  { value: 'ACTIVE', label: 'Actifs' },
  { value: 'SUSPENDED', label: 'Suspendus' },
];

export const CUSTOMERS_TITLE = 'Clients';
export const UNNAMED_CUSTOMER = 'Nom non renseigné';

/** « Prénom Nom », ou `null` tant que le client n'a renseigné ni l'un ni l'autre. */
export function getCustomerName(customer: Customer): string | null {
  const name = [customer.firstName, customer.lastName].filter(Boolean).join(' ');
  return name || null;
}

/** Titre de la fiche, aussi utilisé comme titre de route. */
export function getCustomerTitle(customer: Customer | undefined): string {
  return (customer && getCustomerName(customer)) || 'Fiche client';
}
