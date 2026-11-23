import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  linkedSignal,
  signal,
} from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { NonNullableFormBuilder, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { AuthService } from '../../core/auth/auth.service';
import { AmountPipe } from '../../core/format/amount.pipe';
import { DateFrPipe } from '../../core/format/date-fr.pipe';
import { getRangeLabel } from '../../core/format/page-range';
import { AccountsService } from '../accounts/accounts.service';
import { Account, DEFAULT_ACCOUNT_LABEL, PageResponse } from '../accounts/accounts.models';
import {
  getOperationsTitle,
  Transaction,
  TRANSACTION_TYPES,
  TransactionFilters,
  TransactionType,
  TYPE_LABELS,
} from './operations.models';
import { OperationsService } from './operations.service';

export const PAGE_SIZE = 20;
export const MAX_ACCOUNT_CHOICES = 100;
export const INVALID_PERIOD_MESSAGE = 'La date de début doit précéder la date de fin.';
const ID_PREFIX_LENGTH = 8;

@Component({
  selector: 'app-operations',
  imports: [
    AmountPipe,
    DateFrPipe,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatProgressSpinnerModule,
    ReactiveFormsModule,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './operations.html',
  styleUrl: './operations.scss',
})
export class Operations {
  private readonly service = inject(OperationsService);
  private readonly accountsService = inject(AccountsService);

  protected readonly isAdmin = inject(AuthService).isAdmin;
  protected readonly title = computed(() => getOperationsTitle(this.isAdmin()));
  protected readonly pageSize = PAGE_SIZE;
  protected readonly types = TRANSACTION_TYPES;
  protected readonly typeLabels = TYPE_LABELS;
  protected readonly defaultLabel = DEFAULT_ACCOUNT_LABEL;
  protected readonly periodMessage = INVALID_PERIOD_MESSAGE;
  protected readonly getRangeLabel = getRangeLabel;

  protected readonly form = inject(NonNullableFormBuilder).group({
    accountId: [''],
    type: [''],
    from: [''],
    to: [''],
  });
  protected readonly isPeriodInvalid = signal(false);
  protected readonly filters = signal<TransactionFilters>({});
  protected readonly pageIndex = signal(0);

  /** Le client choisit parmi ses comptes ; l'administrateur voit toutes les opérations sans filtre de compte. */
  protected readonly accounts = rxResource({
    params: () => (this.isAdmin() ? undefined : {}),
    stream: () => this.accountsService.listAccounts(0, MAX_ACCOUNT_CHOICES),
  });
  protected readonly accountLabels = computed(() => {
    const labels = new Map<string, string>();
    if (this.accounts.hasValue()) {
      for (const account of this.accounts.value().content) {
        labels.set(account.id, this.getAccountLabel(account));
      }
    }
    return labels;
  });

  protected readonly transactions = rxResource({
    params: () => ({ filters: this.filters(), page: this.pageIndex() }),
    stream: ({ params }) => this.service.listTransactions(params.filters, params.page, PAGE_SIZE),
  });

  /** Dernière page reçue : elle reste affichée pendant le chargement de la suivante. */
  protected readonly page = linkedSignal<
    PageResponse<Transaction> | undefined,
    PageResponse<Transaction> | undefined
  >({
    source: () => (this.transactions.hasValue() ? this.transactions.value() : undefined),
    computation: (value, previous) => value ?? previous?.value,
  });

  protected getAccountLabel(account: Account): string {
    return account.label || this.defaultLabel;
  }

  protected getTransactionAccount(transaction: Transaction): string {
    return (
      this.accountLabels().get(transaction.accountId) ?? this.getShortId(transaction.accountId)
    );
  }

  protected getShortId(id: string): string {
    return id.slice(0, ID_PREFIX_LENGTH);
  }

  protected changePage(pageIndex: number): void {
    this.pageIndex.set(pageIndex);
  }

  protected applyFilters(): void {
    const { accountId, type, from, to } = this.form.getRawValue();
    if (from && to && from > to) {
      this.isPeriodInvalid.set(true);
      return;
    }
    this.isPeriodInvalid.set(false);
    this.pageIndex.set(0);
    this.filters.set({
      accountId: accountId || undefined,
      type: (type as TransactionType) || undefined,
      from: from || undefined,
      to: to || undefined,
    });
  }

  protected resetFilters(): void {
    this.form.reset();
    this.applyFilters();
  }
}
