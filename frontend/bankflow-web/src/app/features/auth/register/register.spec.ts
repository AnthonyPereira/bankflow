import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { render, screen } from '@testing-library/angular';
import userEvent from '@testing-library/user-event';
import { of, throwError } from 'rxjs';
import { AuthService } from '../../../core/auth/auth.service';
import { Register } from './register';

const EMAIL = 'a@bankflow.test';
const PASSWORD = 'un-mot-de-passe-solide';

describe('Register', () => {
  const auth = { register: vi.fn(), login: vi.fn() };

  async function setup() {
    auth.register.mockReset();
    auth.login.mockReset();
    await render(Register, {
      providers: [provideRouter([]), { provide: AuthService, useValue: auth }],
    });
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    return { navigate, user: userEvent.setup() };
  }

  async function submit(user: ReturnType<typeof userEvent.setup>, email: string, password: string) {
    await user.type(screen.getByLabelText('Adresse email'), email);
    await user.type(screen.getByLabelText('Mot de passe'), password);
    await user.click(screen.getByRole('button', { name: 'Créer mon compte' }));
  }

  it('refuse un mot de passe trop court', async () => {
    const { user } = await setup();

    await submit(user, EMAIL, 'trop-court');

    expect(auth.register).not.toHaveBeenCalled();
    expect(
      await screen.findByText('Le mot de passe doit contenir au moins 12 caractères.'),
    ).toBeTruthy();
  });

  it('refuse un mot de passe de plus de 72 octets', async () => {
    const { user } = await setup();

    await submit(user, EMAIL, 'é'.repeat(37));

    expect(auth.register).not.toHaveBeenCalled();
    expect(await screen.findByText('Le mot de passe est trop long.')).toBeTruthy();
  });

  it('refuse une adresse email invalide', async () => {
    const { user } = await setup();

    await submit(user, 'adresse-invalide', PASSWORD);

    expect(auth.register).not.toHaveBeenCalled();
    expect(await screen.findByText("L'adresse email est invalide.")).toBeTruthy();
  });

  it('inscrit, connecte puis ouvre le tableau de bord', async () => {
    const { navigate, user } = await setup();
    auth.register.mockReturnValue(of({ id: '1', email: EMAIL }));
    auth.login.mockReturnValue(of(undefined));

    await submit(user, EMAIL, PASSWORD);

    expect(auth.register).toHaveBeenCalledWith({ email: EMAIL, password: PASSWORD });
    expect(auth.login).toHaveBeenCalledWith({ email: EMAIL, password: PASSWORD });
    expect(navigate).toHaveBeenCalledWith('/tableau-de-bord');
  });

  it('signale une adresse déjà utilisée', async () => {
    const { navigate, user } = await setup();
    auth.register.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 409 })));

    await submit(user, EMAIL, PASSWORD);

    expect((await screen.findByRole('alert')).textContent).toContain(
      'Un compte existe déjà avec cette adresse email.',
    );
    expect(navigate).not.toHaveBeenCalled();
  });
});
