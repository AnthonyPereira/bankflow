export type CustomerStatus = 'ACTIVE' | 'SUSPENDED';

export interface Customer {
  id: string;
  userId: string;
  email: string;
  firstName: string | null;
  lastName: string | null;
  phone: string | null;
  address: string | null;
  status: CustomerStatus;
  demo: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface ProfileUpdate {
  firstName: string | null;
  lastName: string | null;
  phone: string | null;
  address: string | null;
}

export const STATUS_LABELS: Record<CustomerStatus, string> = {
  ACTIVE: 'Actif',
  SUSPENDED: 'Suspendu',
};
