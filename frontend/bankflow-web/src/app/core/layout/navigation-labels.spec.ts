import { getAccountsTitle, getOperationsTitle } from './navigation-labels';

describe('libellés de navigation', () => {
  it('titre des comptes selon le rôle', () => {
    expect(getAccountsTitle(false)).toBe('Mes comptes');
    expect(getAccountsTitle(true)).toBe('Tous les comptes');
  });

  it('titre des opérations selon le rôle', () => {
    expect(getOperationsTitle(false)).toBe('Mes opérations');
    expect(getOperationsTitle(true)).toBe('Toutes les opérations');
  });
});
