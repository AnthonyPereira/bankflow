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
import { DemoAccount, Role, ROLE_LABELS } from '../../../core/auth/auth.models';
import { AuthCard } from '../auth-card/auth-card';

const DEFAULT_DESTINATION = '/tableau-de-bord';

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

  /** Page demandée avant la redirection vers la connexion. */
  readonly returnUrl = input<string>();

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
  protected readonly submitting = signal(false);
  protected readonly errorMessage = signal<string | null>(null);
  protected readonly passwordVisible = signal(false);
  protected readonly demoAccounts = toSignal(this.auth.fetchDemoAccounts(), { initialValue: [] });

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
    this.submitting.set(true);
    this.infoMessage.set(null);
    this.errorMessage.set(null);
    this.auth
      .login(this.form.getRawValue())
      .pipe(finalize(() => this.submitting.set(false)))
      .subscribe({
        next: () => void this.router.navigateByUrl(this.resolveDestination()),
        error: (error: HttpErrorResponse) => {
          if (error.status === 401) {
            this.errorMessage.set('Adresse email ou mot de passe incorrect.');
          }
        },
      });
  }

  /** Seul un chemin interne est accepté, pour ne jamais rediriger vers un autre site. */
  private resolveDestination(): string {
    const url = this.returnUrl();
    return url?.startsWith('/') && !url.startsWith('//') ? url : DEFAULT_DESTINATION;
  }
}

/** L'état de navigation n'existe que pendant la navigation qui construit le composant. */
function readRegisteredEmail(router: Router): string | null {
  const email: unknown = router.currentNavigation()?.extras.state?.['registeredEmail'];
  return typeof email === 'string' ? email : null;
}
