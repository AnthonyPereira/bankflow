import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  inject,
  linkedSignal,
  signal,
} from '@angular/core';
import { rxResource, takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { RouterLink } from '@angular/router';
import { debounceTime } from 'rxjs';
import { getRangeLabel } from '../../core/format/page-range';
import { STATUS_LABELS } from '../profile/profile.models';
import {
  CUSTOMERS_TITLE,
  Customer,
  CustomerStatusFilter,
  getCustomerName,
  PageResponse,
  STATUS_FILTERS,
  UNNAMED_CUSTOMER,
} from './customers.models';
import { CustomersService } from './customers.service';

export const CUSTOMERS_PAGE_SIZE = 20;
export const SEARCH_DELAY_MS = 300;

@Component({
  selector: 'app-customers',
  imports: [
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatProgressSpinnerModule,
    ReactiveFormsModule,
    RouterLink,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './customers.html',
  styleUrl: './customers.scss',
})
export class Customers {
  private readonly service = inject(CustomersService);

  protected readonly title = CUSTOMERS_TITLE;
  protected readonly pageSize = CUSTOMERS_PAGE_SIZE;
  protected readonly statusFilters = STATUS_FILTERS;
  protected readonly statusLabels = STATUS_LABELS;
  protected readonly unnamed = UNNAMED_CUSTOMER;
  protected readonly searchControl = new FormControl('', { nonNullable: true });
  protected readonly term = signal('');
  protected readonly status = signal<CustomerStatusFilter>('ALL');
  protected readonly pageIndex = signal(0);
  protected readonly customers = rxResource({
    params: () => ({ page: this.pageIndex(), status: this.status(), term: this.term() }),
    stream: ({ params }) => this.service.listCustomers({ ...params, size: CUSTOMERS_PAGE_SIZE }),
  });

  /** Dernière page reçue : elle reste affichée pendant le chargement de la suivante. */
  protected readonly page = linkedSignal<
    PageResponse<Customer> | undefined,
    PageResponse<Customer> | undefined
  >({
    source: () => (this.customers.hasValue() ? this.customers.value() : undefined),
    computation: (value, previous) => value ?? previous?.value,
  });

  protected readonly getName = getCustomerName;
  protected readonly getRangeLabel = getRangeLabel;

  constructor() {
    this.searchControl.valueChanges
      .pipe(debounceTime(SEARCH_DELAY_MS), takeUntilDestroyed(inject(DestroyRef)))
      .subscribe(() => this.applySearch());
  }

  /** Validation du formulaire (touche Entrée ou bouton) : sans attendre la pause. */
  protected submitSearch(event: Event): void {
    event.preventDefault();
    this.applySearch();
  }

  protected changeStatus(event: Event): void {
    this.status.set((event.target as HTMLSelectElement).value as CustomerStatusFilter);
    this.pageIndex.set(0);
  }

  protected changePage(pageIndex: number): void {
    this.pageIndex.set(pageIndex);
  }

  private applySearch(): void {
    const term = this.searchControl.value.trim();
    if (term !== this.term()) {
      this.term.set(term);
      this.pageIndex.set(0);
    }
  }
}
