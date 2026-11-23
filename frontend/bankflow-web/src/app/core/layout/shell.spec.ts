import { BreakpointObserver, BreakpointState } from '@angular/cdk/layout';
import { Component, importProvidersFrom, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { MatIconTestingModule } from '@angular/material/icon/testing';
import { provideRouter, Router } from '@angular/router';
import { render, screen } from '@testing-library/angular';
import userEvent from '@testing-library/user-event';
import { of } from 'rxjs';
import { AuthService } from '../auth/auth.service';
import { NotificationUiService } from '../notifications/notification-ui.service';
import { Shell, UNCONFIRMED_LOGOUT_MESSAGE } from './shell';

@Component({ template: '<p>Page</p>' })
class StubPage {}

describe('Shell', () => {
  const isAdmin = signal(false);
  const auth = { isAdmin, logout: vi.fn() };
  const notifications = { showError: vi.fn() };

  async function setup(options: { isAdmin?: boolean; isCompact?: boolean } = {}) {
    isAdmin.set(options.isAdmin ?? false);
    auth.logout.mockReset();
    notifications.showError.mockReset();
    const breakpoints = {
      observe: () =>
        of({ matches: options.isCompact ?? false, breakpoints: {} } as BreakpointState),
    };
    await render(Shell, {
      providers: [
        importProvidersFrom(MatIconTestingModule),
        provideRouter([
          { path: 'tableau-de-bord', component: StubPage },
          { path: 'administration', component: StubPage },
        ]),
        { provide: AuthService, useValue: auth },
        { provide: NotificationUiService, useValue: notifications },
        { provide: BreakpointObserver, useValue: breakpoints },
      ],
    });
    const router = TestBed.inject(Router);
    return { router, user: userEvent.setup() };
  }

  function getMain(): HTMLElement {
    return screen.getByRole('main');
  }

  it("n'affiche pas l'administration à un client", async () => {
    await setup();

    expect(screen.getByRole('link', { name: 'Tableau de bord' })).toBeTruthy();
    expect(screen.queryByRole('link', { name: 'Administration' })).toBeNull();
  });

  it("affiche l'administration à un administrateur", async () => {
    await setup({ isAdmin: true });

    expect(screen.getByRole('link', { name: 'Administration' })).toBeTruthy();
  });

  it('nomme le menu des comptes selon le rôle', async () => {
    await setup();
    expect(screen.getByRole('link', { name: 'Mes comptes' })).toBeTruthy();
  });

  it('nomme le menu « Tous les comptes » pour un administrateur', async () => {
    await setup({ isAdmin: true });
    expect(screen.getByRole('link', { name: 'Tous les comptes' })).toBeTruthy();
    expect(screen.queryByRole('link', { name: 'Mes comptes' })).toBeNull();
  });

  it('déconnecte puis renvoie vers la connexion', async () => {
    const { router, user } = await setup();
    const navigate = vi.spyOn(router, 'navigateByUrl').mockResolvedValue(true);
    auth.logout.mockReturnValue(of(true));

    await user.click(screen.getByRole('button', { name: 'Se déconnecter' }));

    expect(auth.logout).toHaveBeenCalled();
    expect(navigate).toHaveBeenCalledWith('/connexion');
    expect(notifications.showError).not.toHaveBeenCalled();
  });

  it('prévient quand le serveur n’a pas confirmé la déconnexion', async () => {
    const { router, user } = await setup();
    const navigate = vi.spyOn(router, 'navigateByUrl').mockResolvedValue(true);
    auth.logout.mockReturnValue(of(false));

    await user.click(screen.getByRole('button', { name: 'Se déconnecter' }));

    expect(notifications.showError).toHaveBeenCalledWith(
      "La déconnexion n'a pas pu être confirmée par le serveur. Fermez le navigateur sur un poste partagé.",
    );
    expect(UNCONFIRMED_LOGOUT_MESSAGE).toContain('poste partagé');
    expect(navigate).toHaveBeenCalledWith('/connexion');
  });

  it("n'affiche pas le bouton de menu sur grand écran", async () => {
    await setup();

    expect(screen.queryByRole('button', { name: 'Ouvrir le menu' })).toBeNull();
  });

  it('indique l’état du menu sur petit écran', async () => {
    const { user } = await setup({ isCompact: true });
    const button = screen.getByRole('button', { name: 'Ouvrir le menu' });
    const controls = button.getAttribute('aria-controls');

    expect(button.getAttribute('aria-expanded')).toBe('false');
    expect(controls && document.getElementById(controls)?.tagName).toBe('MAT-SIDENAV');

    await user.click(button);

    expect(button.getAttribute('aria-expanded')).toBe('true');
  });

  it('propose un lien d’évitement vers le contenu principal', async () => {
    const { user } = await setup();
    const skipLink = screen.getByRole('link', { name: 'Aller au contenu' });

    expect(skipLink.getAttribute('href')).toBe(`#${getMain().id}`);

    await user.click(skipLink);

    expect(document.activeElement).toBe(getMain());
  });

  it('place le focus sur le contenu après une navigation, sauf au chargement', async () => {
    const { router } = await setup();
    const main = getMain();

    await router.navigateByUrl('/tableau-de-bord');
    expect(document.activeElement).not.toBe(main);

    await router.navigateByUrl('/administration');
    expect(document.activeElement).toBe(main);
  });
});
