import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import {
  AbstractControl,
  NonNullableFormBuilder,
  ReactiveFormsModule,
  ValidationErrors,
  Validators,
} from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { Router, RouterLink } from '@angular/router';
import { catchError, EMPTY, finalize, switchMap } from 'rxjs';
import { AuthService } from '../../../core/auth/auth.service';
import { AuthCard } from '../auth-card/auth-card';
import type { LoginNavigationState } from '../login/login';

const PASSWORD_MIN_LENGTH = 12;
const PASSWORD_MAX_BYTES = 72;

/** BCrypt ignore tout ce qui dépasse 72 octets : le backend refuse donc ces mots de passe. */
function validateMaxBytes(control: AbstractControl<string>): ValidationErrors | null {
  const tooLong = new TextEncoder().encode(control.value).length > PASSWORD_MAX_BYTES;
  return tooLong ? { maxBytes: true } : null;
}

@Component({
  selector: 'app-register',
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
  templateUrl: './register.html',
  styleUrl: '../auth-form.scss',
})
export class Register {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  protected readonly form = inject(NonNullableFormBuilder).group({
    email: ['', [Validators.required, Validators.email, Validators.maxLength(254)]],
    password: [
      '',
      [Validators.required, Validators.minLength(PASSWORD_MIN_LENGTH), validateMaxBytes],
    ],
  });
  protected readonly isSubmitting = signal(false);
  protected readonly errorMessage = signal<string | null>(null);
  protected readonly isPasswordVisible = signal(false);

  protected submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const credentials = this.form.getRawValue();
    this.isSubmitting.set(true);
    this.errorMessage.set(null);
    this.auth
      .register(credentials)
      .pipe(
        switchMap(() =>
          this.auth.login(credentials).pipe(
            // Le compte est créé : un nouvel essai d'inscription échouerait, place à la connexion.
            catchError(() => {
              this.redirectToLogin(credentials.email);
              return EMPTY;
            }),
          ),
        ),
        finalize(() => this.isSubmitting.set(false)),
      )
      .subscribe({
        next: () => void this.router.navigateByUrl('/tableau-de-bord'),
        error: (error: HttpErrorResponse) => {
          if (error.status === 409) {
            this.errorMessage.set('Un compte existe déjà avec cette adresse email.');
          } else if (error.status === 400) {
            this.errorMessage.set('Vérifiez les informations saisies.');
          }
        },
      });
  }

  /** L'email passe par l'état de navigation, et non par l'URL, pour ne pas finir dans les logs. */
  private redirectToLogin(email: string): void {
    const state: LoginNavigationState = { registeredEmail: email };
    void this.router.navigateByUrl('/connexion', { state });
  }
}
