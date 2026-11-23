import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { getRangeLabel } from '../format/page-range';

/**
 * Pagination d'une liste (« 1 – 20 sur 25 », pages précédente et suivante). Elle ne s'affiche que
 * si la liste dépasse une page. Le paginateur Material n'est pas utilisé : son `MatSelect` entraîne
 * le code des surcouches (overlay) dans le bundle initial.
 */
@Component({
  selector: 'app-pager',
  imports: [MatButtonModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './pager.html',
  styleUrl: './pager.scss',
})
export class Pager {
  readonly pageIndex = input.required<number>();
  readonly pageSize = input.required<number>();
  readonly totalElements = input.required<number>();
  readonly totalPages = input.required<number>();
  /** Nom accessible de la navigation, par exemple « Pagination des comptes ». */
  readonly label = input.required<string>();
  readonly pageChange = output<number>();

  protected readonly isVisible = computed(() => this.totalElements() > this.pageSize());
  protected readonly rangeLabel = computed(() =>
    getRangeLabel(this.pageIndex(), this.pageSize(), this.totalElements()),
  );
}
