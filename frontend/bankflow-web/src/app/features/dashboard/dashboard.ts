import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { RouterLink } from '@angular/router';
import { ROLE_LABELS } from '../../core/auth/auth.models';
import { AuthService } from '../../core/auth/auth.service';
import { AmountPipe } from '../../core/format/amount.pipe';
import { DateFrPipe } from '../../core/format/date-fr.pipe';
import { DEFAULT_ACCOUNT_LABEL, getBalanceTotals } from '../accounts/accounts.models';
import { AccountsService } from '../accounts/accounts.service';
import { getAmountSign, getTransactionLabel, Transaction } from '../operations/operations.models';
import { OperationsService } from '../operations/operations.service';

export const CLIENT_TRANSACTIONS_COUNT = 5;
export const ADMIN_TRANSACTIONS_COUNT = 10;
const MAX_ACCOUNT_CHOICES = 100;

/** Les alertes arrivent avec les notifications : aucune valeur fictive n'est affichée. */
const UPCOMING_SECTIONS = [
  {
    icon: 'notifications',
    title: 'Notifications',
    text: 'Vos alertes récentes apparaîtront ici.',
  },
];

@Component({
  selector: 'app-dashboard',
  imports: [
    AmountPipe,
    DatePipe,
    DateFrPipe,
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule,
    RouterLink,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './dashboard.html',
  styleUrl: './dashboard.scss',
})
export class Dashboard {
  private readonly auth = inject(AuthService);
  private readonly accountsService = inject(AccountsService);
  private readonly operationsService = inject(OperationsService);

  protected readonly isAdmin = this.auth.isAdmin;
  protected readonly upcomingSections = UPCOMING_SECTIONS;
  protected readonly defaultLabel = DEFAULT_ACCOUNT_LABEL;
  protected readonly getAmountSign = getAmountSign;
  protected readonly getTransactionLabel = getTransactionLabel;

  protected readonly user = rxResource({ stream: () => this.auth.fetchCurrentUser() });
  protected readonly getRoleLabel = computed(() => {
    const role = this.user.value()?.role;
    return role ? ROLE_LABELS[role] : '';
  });

  /** Chaque bloc se charge et échoue seul : une panne n'empêche pas l'affichage des autres. */
  protected readonly accounts = rxResource({
    params: () => (this.isAdmin() ? undefined : {}),
    stream: () => this.accountsService.listAccounts(0, MAX_ACCOUNT_CHOICES),
  });
  protected readonly summary = rxResource({
    params: () => (this.isAdmin() ? {} : undefined),
    stream: () => this.accountsService.getSummary(),
  });
  protected readonly transactions = rxResource({
    params: () => ({ size: this.isAdmin() ? ADMIN_TRANSACTIONS_COUNT : CLIENT_TRANSACTIONS_COUNT }),
    stream: ({ params }) => this.operationsService.listTransactions({}, 0, params.size),
  });

  protected readonly activeAccounts = computed(() =>
    this.accounts.hasValue()
      ? this.accounts.value().content.filter((account) => account.status === 'ACTIVE')
      : [],
  );
  protected readonly totals = computed(() => getBalanceTotals(this.activeAccounts()));
  private readonly accountLabels = computed(
    () =>
      new Map(this.activeAccounts().map((a) => [a.id, a.label || DEFAULT_ACCOUNT_LABEL] as const)),
  );

  protected getTransactionAccount(transaction: Transaction): string {
    return this.accountLabels().get(transaction.accountId) ?? 'Voir le compte';
  }
}
