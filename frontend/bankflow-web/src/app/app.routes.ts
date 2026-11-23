import { Routes } from '@angular/router';
import { adminGuard, authGuard, guestGuard } from './core/auth/auth.guards';

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
        title: 'Mes comptes · BankFlow',
        loadComponent: () => import('./features/accounts/accounts').then((m) => m.Accounts),
      },
      {
        path: 'profil',
        title: 'Mon profil · BankFlow',
        loadComponent: () => import('./features/profile/profile').then((m) => m.Profile),
      },
      {
        path: 'administration',
        title: 'Administration · BankFlow',
        canActivate: [adminGuard],
        loadComponent: () => import('./features/admin/admin').then((m) => m.Admin),
      },
    ],
  },
  { path: '**', redirectTo: '' },
];
