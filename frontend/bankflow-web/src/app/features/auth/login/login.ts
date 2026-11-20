import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, inject, input, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { Router, RouterLink } from '@angular/router';
import { finalize } from 'rxjs';
import { AuthService } from '../../../core/auth/auth.service';
import { resolveReturnUrl } from '../../../core/auth/return-url';
import { DemoAccount, Role, ROLE_LABELS } from '../../../core/auth/auth.models';
import { AuthCard } from '../auth-card/auth-card';

export const INVALID_CREDENTIALS_MESSAGE =
  'Adresse email ou mot de passe incorrect. Après plusieurs échecs, le compte est bloqué quelques minutes.';
export const UNEXPECTED_ERROR_MESSAGE = 'Une erreur inattendue est survenue. Réessayez.';

/** État de navigation transmis par l'inscription quand la connexion automatique a échoué. */
export interface LoginNavigationState {
  registeredEmail: string;
}

@Component({
  selector: 'app-login',
  imports: [
    AuthCard,
    MatButtonModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    ReactiveFormsModule,
    RouterLink,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './login.html',
  styleUrls: ['../auth-form.scss', './login.scss'],
})
export class Login {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  /** Page demandée avant la redirection vers la connexion. Un paramètre répété donne un tableau. */
  readonly returnUrl = input<string | string[]>();

  private readonly registeredEmail = readRegisteredEmail(this.router);

  protected readonly form = inject(NonNullableFormBuilder).group({
    email: [this.registeredEmail ?? '', [Validators.required, Validators.email]],
    password: ['', Validators.required],
  });
  protected readonly infoMessage = signal(
    this.registeredEmail
      ? 'Votre compte a été créé. Connectez-vous pour accéder à votre espace.'
      : null,
  );
  protected readonly isSubmitting = signal(false);
  protected readonly errorMessage = signal<string | null>(null);
  protected readonly isPasswordVisible = signal(false);
  protected readonly getDemoAccounts = toSignal(this.auth.fetchDemoAccounts(), {
    initialValue: [],
  });

  protected getRoleLabel(role: Role): string {
    return ROLE_LABELS[role];
  }

  /** Remplit le formulaire : le visiteur voit ce qui est envoyé avant de se connecter. */
  protected useAccount(account: DemoAccount): void {
    this.form.setValue({ email: account.email, password: account.password });
    this.errorMessage.set(null);
  }

  protected submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.isSubmitting.set(true);
    this.infoMessage.set(null);
    this.errorMessage.set(null);
    this.auth
      .login(this.form.getRawValue())
      .pipe(finalize(() => this.isSubmitting.set(false)))
      .subscribe({
        next: () => void this.router.navigateByUrl(resolveReturnUrl(this.returnUrl())),
        error: (error: unknown) => {
          // Le message reste le même pour un compte verrouillé : le backend ne distingue pas les cas.
          if (!(error instanceof HttpErrorResponse)) {
            this.errorMessage.set(UNEXPECTED_ERROR_MESSAGE);
          } else if (error.status === 401) {
            this.errorMessage.set(INVALID_CREDENTIALS_MESSAGE);
          }
        },
      });
  }
}

/** L'état de navigation n'existe que pendant la navigation qui construit le composant. */
function readRegisteredEmail(router: Router): string | null {
  const email: unknown = router.currentNavigation()?.extras.state?.['registeredEmail'];
  return typeof email === 'string' ? email : null;
}
