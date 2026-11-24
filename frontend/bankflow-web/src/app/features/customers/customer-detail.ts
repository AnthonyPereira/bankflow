import {
  afterNextRender,
  ChangeDetectionStrategy,
  Component,
  computed,
  DestroyRef,
  effect,
  ElementRef,
  inject,
  Injector,
  input,
  linkedSignal,
  signal,
  untracked,
  viewChild,
} from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { rxResource, takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { Title } from '@angular/platform-browser';
import { RouterLink } from '@angular/router';
import { finalize } from 'rxjs';
import { AmountPipe } from '../../core/format/amount.pipe';
import { DateFrPipe } from '../../core/format/date-fr.pipe';
import {
  CURRENCIES,
  Currency,
  DEFAULT_ACCOUNT_LABEL,
  formatIban,
} from '../accounts/accounts.models';
import { STATUS_LABELS } from '../profile/profile.models';
import {
  Customer,
  CustomerStatus,
  getCustomerName,
  getCustomerTitle,
  UNNAMED_CUSTOMER,
} from './customers.models';
import { CustomersService, getCustomerErrorMessage } from './customers.service';

export const ACCOUNT_OPENED_MESSAGE = 'Le compte a été ouvert.';
export const SUSPENDED_MESSAGE = 'Client suspendu.';
export const REACTIVATED_MESSAGE = 'Client réactivé.';
export const DEMO_CUSTOMER_NOTICE =
  'Il s’agit d’un compte de démonstration : il ne peut pas être suspendu.';
const NOT_PROVIDED = 'Non renseigné';
const TITLE_SUFFIX = ' · BankFlow';

@Component({
  selector: 'app-customer-detail',
  imports: [
    AmountPipe,
    DateFrPipe,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatProgressSpinnerModule,
    ReactiveFormsModule,
    RouterLink,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './customer-detail.html',
  styleUrl: './customer-detail.scss',
})
export class CustomerDetail {
  private readonly service = inject(CustomersService);
  private readonly injector = inject(Injector);
  private readonly destroyRef = inject(DestroyRef);
  private readonly titleService = inject(Title);
  private readonly confirmButton = viewChild('confirmButton', { read: ElementRef });
  private readonly statusButton = viewChild('statusButton', { read: ElementRef });

  /** Paramètre `:id` de la route (liaison des paramètres de route sur les entrées). */
  readonly id = input.required<string>();

  protected readonly statusLabels = STATUS_LABELS;
  protected readonly currencies = CURRENCIES;
  protected readonly defaultLabel = DEFAULT_ACCOUNT_LABEL;
  protected readonly demoNotice = DEMO_CUSTOMER_NOTICE;
  protected readonly unnamed = UNNAMED_CUSTOMER;
  protected readonly notProvided = NOT_PROVIDED;
  protected readonly formatIban = formatIban;
  protected readonly getName = getCustomerName;

  private readonly loaded = rxResource({
    params: () => this.id(),
    stream: ({ params }) => this.service.getCustomer(params),
  });
  /** Le client chargé, remplacé par la réponse d'un changement de statut. */
  protected readonly customer = linkedSignal<Customer | undefined>(() =>
    this.loaded.hasValue() ? this.loaded.value() : undefined,
  );
  protected readonly hasLoadError = computed(() => this.loaded.error() !== undefined);
  protected readonly isNotFound = computed(() => this.hasLoadError() && this.isNotFoundError());
  protected readonly isSuspended = computed(() => this.customer()?.status === 'SUSPENDED');

  protected readonly accounts = rxResource({
    params: () => this.id(),
    stream: ({ params }) => this.service.listCustomerAccounts(params),
  });

  protected readonly isConfirmingSuspension = signal(false);
  protected readonly isChangingStatus = signal(false);
  protected readonly statusError = signal<string | null>(null);
  protected readonly statusMessage = signal<string | null>(null);

  protected readonly form = inject(NonNullableFormBuilder).group({
    currency: ['EUR' as Currency],
    label: ['', Validators.maxLength(50)],
  });
  protected readonly isSubmitting = signal(false);
  protected readonly successMessage = signal<string | null>(null);
  protected readonly errorMessage = signal<string | null>(null);

  constructor() {
    // Un autre client : l'état du précédent (formulaire, messages, confirmation) ne doit pas rester.
    effect(() => {
      this.id();
      untracked(() => this.resetClientState());
    });
    // Un client suspendu ne peut pas recevoir de nouveau compte.
    effect(() => {
      if (this.isSuspended()) {
        this.form.disable({ emitEvent: false });
      } else {
        this.form.enable({ emitEvent: false });
      }
    });
    effect(() => {
      this.titleService.setTitle(`${getCustomerTitle(this.customer())}${TITLE_SUFFIX}`);
    });
  }

  private resetClientState(): void {
    this.form.reset({ currency: 'EUR', label: '' });
    this.statusError.set(null);
    this.statusMessage.set(null);
    this.successMessage.set(null);
    this.errorMessage.set(null);
    this.isConfirmingSuspension.set(false);
  }

  protected reloadCustomer(): void {
    this.loaded.reload();
  }

  protected requestSuspension(): void {
    this.statusError.set(null);
    this.isConfirmingSuspension.set(true);
    afterNextRender(() => this.confirmButton()?.nativeElement.focus(), {
      injector: this.injector,
    });
  }

  protected cancelSuspension(): void {
    this.isConfirmingSuspension.set(false);
    afterNextRender(() => this.statusButton()?.nativeElement.focus(), {
      injector: this.injector,
    });
  }

  protected suspend(): void {
    this.updateStatus('SUSPENDED');
  }

  protected reactivate(): void {
    this.updateStatus('ACTIVE');
  }

  protected submitAccount(): void {
    if (this.isSubmitting()) {
      return;
    }
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const { currency, label } = this.form.getRawValue();
    this.isSubmitting.set(true);
    this.successMessage.set(null);
    this.errorMessage.set(null);
    const customerId = this.id();
    this.service
      .openAccount(customerId, { currency, label: label.trim() || null })
      .pipe(
        finalize(() => this.isSubmitting.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: () => {
          if (this.id() !== customerId) {
            return;
          }
          this.successMessage.set(ACCOUNT_OPENED_MESSAGE);
          this.form.reset({ currency, label: '' });
          this.accounts.reload();
        },
        error: (error: unknown) => {
          if (this.id() === customerId) {
            this.errorMessage.set(getCustomerErrorMessage(error));
          }
        },
      });
  }

  private updateStatus(status: CustomerStatus): void {
    if (this.isChangingStatus()) {
      return;
    }
    this.isChangingStatus.set(true);
    this.statusError.set(null);
    this.statusMessage.set(null);
    const customerId = this.id();
    this.service
      .changeStatus(customerId, status)
      .pipe(
        finalize(() => this.isChangingStatus.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (updated) => {
          if (this.id() !== customerId) {
            return;
          }
          this.customer.set(updated);
          this.statusMessage.set(status === 'SUSPENDED' ? SUSPENDED_MESSAGE : REACTIVATED_MESSAGE);
          this.isConfirmingSuspension.set(false);
          afterNextRender(() => this.statusButton()?.nativeElement.focus(), {
            injector: this.injector,
          });
        },
        error: (error: unknown) => {
          if (this.id() !== customerId) {
            return;
          }
          this.isConfirmingSuspension.set(false);
          this.statusError.set(getCustomerErrorMessage(error));
          // 409 ou 404 : l'écran n'est plus à jour (statut changé ailleurs, client supprimé).
          if (error instanceof HttpErrorResponse && [404, 409].includes(error.status)) {
            this.loaded.reload();
          }
        },
      });
  }

  private isNotFoundError(): boolean {
    const error: unknown = this.loaded.error();
    const cause = error instanceof Error ? (error.cause ?? error) : error;
    return (cause as { status?: number } | undefined)?.status === 404;
  }
}
