import {
  afterNextRender,
  ChangeDetectionStrategy,
  Component,
  effect,
  ElementRef,
  inject,
  Injector,
  input,
  linkedSignal,
  signal,
  viewChild,
} from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
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
  protected readonly isNotFound = () => this.loaded.error() !== undefined && this.isNotFoundError();
  protected readonly hasLoadError = () => this.loaded.error() !== undefined;

  protected readonly accounts = rxResource({
    params: () => this.id(),
    stream: ({ params }) => this.service.listCustomerAccounts(params),
  });

  protected readonly isConfirmingSuspension = signal(false);
  protected readonly isChangingStatus = signal(false);
  protected readonly statusError = signal<string | null>(null);

  protected readonly form = inject(NonNullableFormBuilder).group({
    currency: ['EUR' as Currency],
    label: ['', Validators.maxLength(50)],
  });
  protected readonly isSubmitting = signal(false);
  protected readonly successMessage = signal<string | null>(null);
  protected readonly errorMessage = signal<string | null>(null);

  constructor() {
    effect(() => {
      this.titleService.setTitle(`${getCustomerTitle(this.customer())}${TITLE_SUFFIX}`);
    });
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
    this.service
      .openAccount(this.id(), { currency, label: label.trim() || null })
      .pipe(finalize(() => this.isSubmitting.set(false)))
      .subscribe({
        next: () => {
          this.successMessage.set(ACCOUNT_OPENED_MESSAGE);
          this.form.reset({ currency, label: '' });
          this.accounts.reload();
        },
        error: (error: unknown) => this.errorMessage.set(getCustomerErrorMessage(error)),
      });
  }

  private updateStatus(status: CustomerStatus): void {
    if (this.isChangingStatus()) {
      return;
    }
    this.isChangingStatus.set(true);
    this.statusError.set(null);
    this.service
      .changeStatus(this.id(), status)
      .pipe(finalize(() => this.isChangingStatus.set(false)))
      .subscribe({
        next: (updated) => {
          this.customer.set(updated);
          this.isConfirmingSuspension.set(false);
          afterNextRender(() => this.statusButton()?.nativeElement.focus(), {
            injector: this.injector,
          });
        },
        error: (error: unknown) => {
          this.isConfirmingSuspension.set(false);
          this.statusError.set(getCustomerErrorMessage(error));
        },
      });
  }

  private isNotFoundError(): boolean {
    const error: unknown = this.loaded.error();
    const cause = error instanceof Error ? (error.cause ?? error) : error;
    return (cause as { status?: number } | undefined)?.status === 404;
  }
}
