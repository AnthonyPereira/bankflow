import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  linkedSignal,
  signal,
} from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { RouterLink } from '@angular/router';
import { finalize } from 'rxjs';
import { AuthService } from '../../core/auth/auth.service';
import { AmountPipe } from '../../core/format/amount.pipe';
import { getRangeLabel } from '../../core/format/page-range';
import {
  Account,
  CURRENCIES,
  Currency,
  DEFAULT_ACCOUNT_LABEL,
  formatIban,
  getAccountsTitle,
  PageResponse,
} from './accounts.models';
import { AccountsService, getAccountErrorMessage } from './accounts.service';

export const PAGE_SIZE = 20;
export const OPENED_MESSAGE = 'Votre compte a été ouvert.';
const CUSTOMER_ID_PREFIX_LENGTH = 8;

@Component({
  selector: 'app-accounts',
  imports: [
    AmountPipe,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatProgressSpinnerModule,
    ReactiveFormsModule,
    RouterLink,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './accounts.html',
  styleUrl: './accounts.scss',
})
export class Accounts {
  private readonly service = inject(AccountsService);

  protected readonly isAdmin = inject(AuthService).isAdmin;
  protected readonly title = computed(() => getAccountsTitle(this.isAdmin()));
  protected readonly pageSize = PAGE_SIZE;
  protected readonly currencies = CURRENCIES;
  protected readonly defaultLabel = DEFAULT_ACCOUNT_LABEL;
  protected readonly pageIndex = signal(0);
  protected readonly accounts = rxResource({
    params: () => ({ page: this.pageIndex() }),
    stream: ({ params }) => this.service.listAccounts(params.page, PAGE_SIZE),
  });

  /** Dernière page reçue : elle reste affichée pendant le chargement de la suivante. */
  protected readonly page = linkedSignal<
    PageResponse<Account> | undefined,
    PageResponse<Account> | undefined
  >({
    source: () => (this.accounts.hasValue() ? this.accounts.value() : undefined),
    computation: (value, previous) => value ?? previous?.value,
  });

  protected readonly form = inject(NonNullableFormBuilder).group({
    currency: ['EUR' as Currency],
    label: ['', Validators.maxLength(50)],
  });
  protected readonly isSubmitting = signal(false);
  protected readonly successMessage = signal<string | null>(null);
  protected readonly errorMessage = signal<string | null>(null);

  protected readonly formatIban = formatIban;

  protected getShortCustomerId(customerId: string): string {
    return customerId.slice(0, CUSTOMER_ID_PREFIX_LENGTH);
  }

  protected readonly getRangeLabel = getRangeLabel;

  protected changePage(pageIndex: number): void {
    this.pageIndex.set(pageIndex);
  }

  protected submit(): void {
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
      .openAccount({ currency, label: label.trim() || null })
      .pipe(finalize(() => this.isSubmitting.set(false)))
      .subscribe({
        next: () => {
          this.successMessage.set(OPENED_MESSAGE);
          this.form.reset({ currency, label: '' });
          // Le changement de page déclenche déjà le rechargement : un seul appel suffit.
          if (this.pageIndex() === 0) {
            this.accounts.reload();
          } else {
            this.pageIndex.set(0);
          }
        },
        error: (error: unknown) => this.errorMessage.set(getAccountErrorMessage(error)),
      });
  }
}
