/** Un administrateur voit les comptes de tous les clients : le titre le dit, partout où il apparaît. */
export function getAccountsTitle(isAdmin: boolean): string {
  return isAdmin ? 'Tous les comptes' : 'Mes comptes';
}

/** Un administrateur voit les opérations de tous les clients : le titre le dit, partout où il apparaît. */
export function getOperationsTitle(isAdmin: boolean): string {
  return isAdmin ? 'Toutes les opérations' : 'Mes opérations';
}
