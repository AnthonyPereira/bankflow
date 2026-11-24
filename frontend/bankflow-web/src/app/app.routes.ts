import { inject } from '@angular/core';
import { Routes } from '@angular/router';
import { AuthService } from './core/auth/auth.service';
import { adminGuard, authGuard, clientGuard, guestGuard } from './core/auth/auth.guards';
import { getAccountsTitle, getOperationsTitle } from './core/layout/navigation-labels';

export const routes: Routes = [
  {
    path: 'connexion',
    title: 'Connexion · BankFlow',
    canActivate: [guestGuard],
    loadComponent: () => import('./features/auth/login/login').then((m) => m.Login),
  },
  {
    path: 'inscription',
    title: 'Inscription · BankFlow',
    canActivate: [guestGuard],
    loadComponent: () => import('./features/auth/register/register').then((m) => m.Register),
  },
  {
    path: '',
    canActivate: [authGuard],
    loadComponent: () => import('./core/layout/shell').then((m) => m.Shell),
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'tableau-de-bord' },
      {
        path: 'tableau-de-bord',
        title: 'Tableau de bord · BankFlow',
        loadComponent: () => import('./features/dashboard/dashboard').then((m) => m.Dashboard),
      },
      {
        path: 'comptes',
        title: () => `${getAccountsTitle(inject(AuthService).isAdmin())} · BankFlow`,
        loadComponent: () => import('./features/accounts/accounts').then((m) => m.Accounts),
      },
      {
        path: 'comptes/:id',
        title: 'Détail du compte · BankFlow',
        loadComponent: () =>
          import('./features/account-detail/account-detail').then((m) => m.AccountDetail),
      },
      {
        path: 'operations',
        title: () => `${getOperationsTitle(inject(AuthService).isAdmin())} · BankFlow`,
        loadComponent: () => import('./features/operations/operations').then((m) => m.Operations),
      },
      {
        path: 'virement',
        title: 'Faire un virement · BankFlow',
        loadComponent: () => import('./features/transfer/transfer').then((m) => m.TransferPage),
      },
      {
        path: 'profil',
        title: 'Mon profil · BankFlow',
        canActivate: [clientGuard],
        loadComponent: () => import('./features/profile/profile').then((m) => m.Profile),
      },
      {
        path: 'administration',
        canActivate: [adminGuard],
        children: [
          { path: '', pathMatch: 'full', redirectTo: 'clients' },
          {
            path: 'clients',
            title: 'Clients · BankFlow',
            loadComponent: () => import('./features/customers/customers').then((m) => m.Customers),
          },
          {
            // Le titre définitif (nom du client) est posé par la fiche une fois le client chargé.
            path: 'clients/:id',
            title: 'Fiche client · BankFlow',
            loadComponent: () =>
              import('./features/customers/customer-detail').then((m) => m.CustomerDetail),
          },
        ],
      },
    ],
  },
  { path: '**', redirectTo: '' },
];
