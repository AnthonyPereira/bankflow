import { importProvidersFrom } from '@angular/core';
import { MatIconTestingModule } from '@angular/material/icon/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { Router, provideRouter } from '@angular/router';
import { TestBed } from '@angular/core/testing';
import { RouterTestingHarness } from '@angular/router/testing';
import { render, screen } from '@testing-library/angular';
import userEvent from '@testing-library/user-event';
import { of, throwError } from 'rxjs';
import { AuthService } from '../../../core/auth/auth.service';
import { DemoAccount } from '../../../core/auth/auth.models';
import {
  INVALID_CREDENTIALS_MESSAGE,
  Login,
  LoginNavigationState,
  UNEXPECTED_ERROR_MESSAGE,
} from './login';

const EMAIL = 'a@bankflow.test';
const PASSWORD = 'un-mot-de-passe-solide';
const DEMO_ACCOUNTS: DemoAccount[] = [
  { email: 'demo.client@bankflow.local', password: 'demo-client-bankflow', role: 'ROLE_CLIENT' },
  { email: 'demo.admin@bankflow.local', password: 'demo-admin-bankflow', role: 'ROLE_ADMIN' },
];

describe('Login', () => {
  const auth = { login: vi.fn(), fetchDemoAccounts: vi.fn() };

  async function setup(returnUrl?: string | string[], demoAccounts: DemoAccount[] = []) {
    auth.login.mockReset();
    auth.fetchDemoAccounts.mockReturnValue(of(demoAccounts));
    await render(Login, {
      inputs: returnUrl ? { returnUrl } : {},
      providers: [
        importProvidersFrom(MatIconTestingModule),
        provideRouter([]),
        { provide: AuthService, useValue: auth },
      ],
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

  it.each([
    ['externe', '//site-externe.example'],
    ['avec antislash', '/\\site-externe.example'],
    ['répétée', ['/administration', '/tableau-de-bord']],
  ])('ignore une adresse de retour %s', async (_cas, returnUrl) => {
    const { navigate, user } = await setup(returnUrl);
    auth.login.mockReturnValue(of(undefined));

    await fillAndSubmit(user);

    expect(navigate).toHaveBeenCalledWith('/tableau-de-bord');
  });

  it('signale des identifiants incorrects sans révéler un verrouillage', async () => {
    const { navigate, user } = await setup();
    auth.login.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 401 })));

    await fillAndSubmit(user);

    expect((await screen.findByRole('alert')).textContent?.trim()).toBe(
      INVALID_CREDENTIALS_MESSAGE,
    );
    expect(INVALID_CREDENTIALS_MESSAGE).toBe(
      'Adresse email ou mot de passe incorrect. Après plusieurs échecs, le compte est bloqué quelques minutes.',
    );
    expect(navigate).not.toHaveBeenCalled();
  });

  it('affiche un message générique pour une erreur qui ne vient pas du serveur', async () => {
    const { navigate, user } = await setup();
    auth.login.mockReturnValue(throwError(() => new Error('jeton indécodable')));

    await fillAndSubmit(user);

    expect((await screen.findByRole('alert')).textContent).toContain(UNEXPECTED_ERROR_MESSAGE);
    expect(navigate).not.toHaveBeenCalled();
  });

  it("laisse l'intercepteur annoncer les autres erreurs du serveur", async () => {
    const { user } = await setup();
    auth.login.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 503 })));

    await fillAndSubmit(user);

    expect(screen.queryByRole('alert')).toBeNull();
    expect(screen.getByRole('button', { name: 'Se connecter' })).toBeTruthy();
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

  it('affiche les comptes de démonstration et remplit le formulaire', async () => {
    const { navigate, user } = await setup(undefined, DEMO_ACCOUNTS);
    auth.login.mockReturnValue(of(undefined));

    expect(screen.getByRole('heading', { name: 'Comptes de démonstration' })).toBeTruthy();
    expect(screen.getByText('demo-admin-bankflow')).toBeTruthy();

    await user.click(screen.getByRole('button', { name: 'Utiliser le compte Administrateur' }));
    await user.click(screen.getByRole('button', { name: 'Se connecter' }));

    expect(auth.login).toHaveBeenCalledWith({
      email: 'demo.admin@bankflow.local',
      password: 'demo-admin-bankflow',
    });
    expect(navigate).toHaveBeenCalledWith('/tableau-de-bord');
  });

  it("n'affiche pas le tableau sans compte de démonstration", async () => {
    await setup();

    expect(screen.queryByRole('heading', { name: 'Comptes de démonstration' })).toBeNull();
  });

  it("annonce le compte créé et préremplit l'email après l'inscription", async () => {
    auth.fetchDemoAccounts.mockReturnValue(of([]));
    TestBed.configureTestingModule({
      providers: [
        importProvidersFrom(MatIconTestingModule),
        provideRouter([{ path: 'connexion', component: Login }]),
        { provide: AuthService, useValue: auth },
      ],
    });
    const harness = await RouterTestingHarness.create();
    const state: LoginNavigationState = { registeredEmail: EMAIL };

    await TestBed.inject(Router).navigateByUrl('/connexion', { state });
    harness.detectChanges();

    expect(screen.getByRole('status').textContent).toContain(
      'Votre compte a été créé. Connectez-vous pour accéder à votre espace.',
    );
    expect(screen.getByLabelText<HTMLInputElement>('Adresse email').value).toBe(EMAIL);
    expect(screen.getByLabelText<HTMLInputElement>('Mot de passe').value).toBe('');
  });

  it("n'annonce rien lors d'une connexion ordinaire", async () => {
    await setup();

    expect(screen.queryByRole('status')).toBeNull();
    expect(screen.getByLabelText<HTMLInputElement>('Adresse email').value).toBe('');
  });
});
