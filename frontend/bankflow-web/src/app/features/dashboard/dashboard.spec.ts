import { importProvidersFrom } from '@angular/core';
import { MatIconTestingModule } from '@angular/material/icon/testing';
import { render, screen } from '@testing-library/angular';
import { of, throwError } from 'rxjs';
import { AuthService } from '../../core/auth/auth.service';
import { User } from '../../shared/models/auth.models';
import { Dashboard } from './dashboard';

const USER: User = {
  id: '1',
  email: 'a@bankflow.test',
  role: 'ROLE_CLIENT',
  createdAt: '2026-11-18T10:00:00Z',
};

describe('Dashboard', () => {
  function setup(currentUser: AuthService['currentUser']) {
    return render(Dashboard, {
      providers: [
        importProvidersFrom(MatIconTestingModule),
        { provide: AuthService, useValue: { currentUser } },
      ],
    });
  }

  it('affiche les informations du compte connecté', async () => {
    await setup(() => of(USER));

    expect(await screen.findByText('a@bankflow.test')).toBeTruthy();
    expect(screen.getByText('Client')).toBeTruthy();
  });

  it('annonce les sections à venir sans donnée fictive', async () => {
    await setup(() => of(USER));

    expect(await screen.findAllByText('Bientôt disponible')).toHaveLength(3);
    expect(document.body.textContent).not.toMatch(/\d+,\d{2}\s*€/);
  });

  it('signale une erreur de chargement', async () => {
    await setup(() => throwError(() => new Error('indisponible')));

    expect((await screen.findByRole('alert')).textContent).toContain(
      "Vos informations n'ont pas pu être chargées.",
    );
    expect(screen.getByRole('button', { name: 'Réessayer' })).toBeTruthy();
  });
});
