import { getTransactionLabel, Transaction } from './operations.models';

const TRANSACTION: Transaction = {
  id: 't1',
  accountId: 'a1',
  type: 'TRANSFER_IN',
  amount: '20.00',
  currency: 'EUR',
  balanceAfter: '980.00',
  transferId: 'tr1',
  compensatesTransactionId: null,
  compensatedByTransactionId: null,
  description: null,
  direction: 'CREDIT',
  counterpartyIban: 'FR7699999000010999999999912',
  performedBy: 'CLIENT',
  createdAt: '2026-11-23T10:00:00Z',
};

describe('getTransactionLabel', () => {
  it('nomme l’émetteur d’un virement reçu', () => {
    expect(getTransactionLabel(TRANSACTION)).toBe(
      'Virement reçu de FR76 9999 9000 0109 9999 9999 912',
    );
  });

  it('nomme le destinataire d’un virement émis', () => {
    expect(getTransactionLabel({ ...TRANSACTION, type: 'TRANSFER_OUT', direction: 'DEBIT' })).toBe(
      'Virement émis vers FR76 9999 9000 0109 9999 9999 912',
    );
  });

  it('se replie sur le type sans IBAN de contrepartie', () => {
    expect(getTransactionLabel({ ...TRANSACTION, counterpartyIban: null })).toBe('Virement reçu');
    expect(getTransactionLabel({ ...TRANSACTION, counterpartyIban: undefined })).toBe(
      'Virement reçu',
    );
  });

  it('garde le libellé du type pour les autres opérations', () => {
    expect(getTransactionLabel({ ...TRANSACTION, type: 'DEPOSIT', counterpartyIban: null })).toBe(
      'Dépôt',
    );
  });
});
