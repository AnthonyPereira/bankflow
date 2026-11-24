import { HttpErrorResponse } from '@angular/common/http';
import {
  afterNextRender,
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  ElementRef,
  inject,
  Injector,
  signal,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import {
  AbstractControl,
  NonNullableFormBuilder,
  ReactiveFormsModule,
  ValidationErrors,
  Validators,
} from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { finalize } from 'rxjs';
import { getFieldErrors, translateProblem } from '../../core/http/problem-details';
import { Customer, ProfileUpdate, STATUS_LABELS } from './profile.models';
import { ProfileService } from './profile.service';

export const RETRY_DELAY_MS = 2000;
export const MAX_RETRIES = 5;
export const PHONE_PATTERN = /^\+?[0-9 .-]{6,20}$/;
export const SAVED_MESSAGE = 'Votre profil a été enregistré.';
export const SAVE_ERROR_MESSAGE = "Votre profil n'a pas pu être enregistré. Réessayez.";
export const PROFILE_CREATING_MESSAGE =
  'Votre profil est en cours de création, réessayez dans quelques secondes.';

/** Teste le numéro sans les espaces de bord, comme le fait l'envoi au serveur. */
export function validatePhone(control: AbstractControl<string>): ValidationErrors | null {
  const phone = control.value.trim();
  return phone === '' || PHONE_PATTERN.test(phone) ? null : { pattern: true };
}

type LoadState = 'loading' | 'creating' | 'failed' | 'error' | 'ready';
type ProfileField = 'firstName' | 'lastName' | 'phone' | 'address';
const PROFILE_FIELDS: readonly ProfileField[] = ['firstName', 'lastName', 'phone', 'address'];

@Component({
  selector: 'app-profile',
  imports: [
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatProgressSpinnerModule,
    ReactiveFormsModule,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './profile.html',
  styleUrl: './profile.scss',
})
export class Profile {
  private readonly service = inject(ProfileService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly injector = inject(Injector);
  private isDestroyed = false;
  private retryTimer: ReturnType<typeof setTimeout> | undefined;
  private retries = 0;

  protected readonly form = inject(NonNullableFormBuilder).group({
    firstName: ['', Validators.maxLength(100)],
    lastName: ['', Validators.maxLength(100)],
    phone: ['', validatePhone],
    address: ['', Validators.maxLength(255)],
  });
  protected readonly state = signal<LoadState>('loading');
  protected readonly customer = signal<Customer | null>(null);
  protected readonly isSubmitting = signal(false);
  protected readonly savedMessage = signal<string | null>(null);
  protected readonly errorMessage = signal<string | null>(null);
  protected readonly statusLabels = STATUS_LABELS;

  constructor() {
    this.destroyRef.onDestroy(() => {
      this.isDestroyed = true;
      clearTimeout(this.retryTimer);
    });
    this.loadProfile();
  }

  protected retry(): void {
    this.retries = 0;
    this.state.set('loading');
    this.loadProfile();
  }

  protected submit(): void {
    if (this.isSubmitting()) {
      return;
    }
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.isSubmitting.set(true);
    this.savedMessage.set(null);
    this.errorMessage.set(null);
    this.service
      .updateMyProfile(this.buildUpdate())
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.isSubmitting.set(false)),
      )
      .subscribe({
        next: (customer) => {
          this.showCustomer(customer);
          this.savedMessage.set(SAVED_MESSAGE);
        },
        error: (error: unknown) => this.showSaveError(error),
      });
  }

  private loadProfile(): void {
    this.service
      .getMyProfile()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (customer) => {
          this.showCustomer(customer);
          this.state.set('ready');
        },
        error: (error: unknown) => {
          if (error instanceof HttpErrorResponse && error.status === 404) {
            this.scheduleRetry();
          } else {
            this.state.set('error');
          }
        },
      });
  }

  /** Le profil est créé par un événement Kafka : il peut manquer quelques secondes après l'inscription. */
  private scheduleRetry(): void {
    if (this.isDestroyed) {
      return;
    }
    if (this.retries >= MAX_RETRIES) {
      this.state.set('failed');
      return;
    }
    this.retries++;
    this.state.set('creating');
    this.retryTimer = setTimeout(() => this.loadProfile(), RETRY_DELAY_MS);
  }

  private showCustomer(customer: Customer): void {
    this.customer.set(customer);
    this.form.reset({
      firstName: customer.firstName ?? '',
      lastName: customer.lastName ?? '',
      phone: customer.phone ?? '',
      address: customer.address ?? '',
    });
  }

  private buildUpdate(): ProfileUpdate {
    const value = this.form.getRawValue();
    return {
      firstName: value.firstName.trim() || null,
      lastName: value.lastName.trim() || null,
      phone: value.phone.trim() || null,
      address: value.address.trim() || null,
    };
  }

  /** Les erreurs de validation du serveur sont rattachées à leur champ. */
  private showSaveError(error: unknown): void {
    const fieldErrors = getFieldErrors(error);
    let firstField: ProfileField | null = null;
    for (const field of PROFILE_FIELDS) {
      const message = fieldErrors[field];
      if (message) {
        const control = this.form.controls[field];
        control.setErrors({ server: message });
        control.markAsTouched();
        firstField ??= field;
      }
    }
    if (firstField) {
      const selector = `[formControlName="${firstField}"]`;
      afterNextRender(() => this.host.nativeElement.querySelector<HTMLElement>(selector)?.focus(), {
        injector: this.injector,
      });
      return;
    }
    this.errorMessage.set(
      error instanceof HttpErrorResponse && error.status === 404
        ? PROFILE_CREATING_MESSAGE
        : translateProblem(error, {}, SAVE_ERROR_MESSAGE),
    );
  }
}
