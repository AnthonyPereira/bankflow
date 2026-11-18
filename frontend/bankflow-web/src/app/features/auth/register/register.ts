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
import { finalize, switchMap } from 'rxjs';
import { AuthService } from '../../../core/auth/auth.service';
import { AuthCard } from '../auth-card/auth-card';

const PASSWORD_MIN_LENGTH = 12;
const PASSWORD_MAX_BYTES = 72;

/** BCrypt ignore tout ce qui dépasse 72 octets : le backend refuse donc ces mots de passe. */
function maxBytes(control: AbstractControl<string>): ValidationErrors | null {
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
    password: ['', [Validators.required, Validators.minLength(PASSWORD_MIN_LENGTH), maxBytes]],
  });
  protected readonly submitting = signal(false);
  protected readonly errorMessage = signal<string | null>(null);
  protected readonly passwordVisible = signal(false);

  protected submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const credentials = this.form.getRawValue();
    this.submitting.set(true);
    this.errorMessage.set(null);
    this.auth
      .register(credentials)
      .pipe(
        switchMap(() => this.auth.login(credentials)),
        finalize(() => this.submitting.set(false)),
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
}
