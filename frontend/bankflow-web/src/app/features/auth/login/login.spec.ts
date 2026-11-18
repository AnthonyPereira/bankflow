import { HttpErrorResponse } from '@angular/common/http';
import { Router, provideRouter } from '@angular/router';
import { TestBed } from '@angular/core/testing';
import { render, screen } from '@testing-library/angular';
import userEvent from '@testing-library/user-event';
import { of, throwError } from 'rxjs';
import { AuthService } from '../../../core/auth/auth.service';
import { Login } from './login';

const EMAIL = 'a@bankflow.test';
const PASSWORD = 'un-mot-de-passe-solide';

describe('Login', () => {
  const auth = { login: vi.fn() };

  async function setup(returnUrl?: string) {
    auth.login.mockReset();
    await render(Login, {
      inputs: returnUrl ? { returnUrl } : {},
      providers: [provideRouter([]), { provide: AuthService, useValue: auth }],
    });
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    return { navigate, user: userEvent.setup() };
  }

  async function fillAndSubmit(user: ReturnType<typeof userEvent.setup>) {
    await user.type(screen.getByLabelText('Adresse email'), EMAIL);
    await user.type(screen.getByLabelText('Mot de passe'), PASSWORD);
    await user.click(screen.getByRole('button', { name: 'Se connecter' }));
  }

  it("n'envoie pas un formulaire vide et affiche les erreurs", async () => {
    const { user } = await setup();

    await user.click(screen.getByRole('button', { name: 'Se connecter' }));

    expect(auth.login).not.toHaveBeenCalled();
    expect(await screen.findByText("L'adresse email est obligatoire.")).toBeTruthy();
    expect(await screen.findByText('Le mot de passe est obligatoire.')).toBeTruthy();
  });

  it('connecte puis ouvre le tableau de bord', async () => {
    const { navigate, user } = await setup();
    auth.login.mockReturnValue(of(undefined));

    await fillAndSubmit(user);

    expect(auth.login).toHaveBeenCalledWith({ email: EMAIL, password: PASSWORD });
    expect(navigate).toHaveBeenCalledWith('/tableau-de-bord');
  });

  it('revient à la page demandée avant la connexion', async () => {
    const { navigate, user } = await setup('/administration');
    auth.login.mockReturnValue(of(undefined));

    await fillAndSubmit(user);

    expect(navigate).toHaveBeenCalledWith('/administration');
  });

  it('ignore une adresse de retour externe', async () => {
    const { navigate, user } = await setup('//site-externe.example');
    auth.login.mockReturnValue(of(undefined));

    await fillAndSubmit(user);

    expect(navigate).toHaveBeenCalledWith('/tableau-de-bord');
  });

  it('signale des identifiants incorrects', async () => {
    const { navigate, user } = await setup();
    auth.login.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 401 })));

    await fillAndSubmit(user);

    expect((await screen.findByRole('alert')).textContent).toContain(
      'Adresse email ou mot de passe incorrect.',
    );
    expect(navigate).not.toHaveBeenCalled();
  });

  it('permet d’afficher le mot de passe', async () => {
    const { user } = await setup();
    const password = screen.getByLabelText<HTMLInputElement>('Mot de passe');

    await user.click(screen.getByRole('button', { name: 'Afficher le mot de passe' }));

    expect((await screen.findByRole('button', { name: 'Masquer le mot de passe' })).tagName).toBe(
      'BUTTON',
    );
    expect(password.type).toBe('text');
  });
});
