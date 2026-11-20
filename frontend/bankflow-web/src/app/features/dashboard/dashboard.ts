import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { ROLE_LABELS } from '../../core/auth/auth.models';
import { AuthService } from '../../core/auth/auth.service';

/** Les données réelles des comptes arrivent avec la MVP 2 : aucune valeur fictive n'est affichée. */
const UPCOMING_SECTIONS = [
  {
    icon: 'account_balance_wallet',
    title: 'Mes comptes',
    text: 'Vos comptes et leurs soldes apparaîtront ici.',
  },
  {
    icon: 'receipt_long',
    title: 'Dernières opérations',
    text: 'Vos virements récents apparaîtront ici.',
  },
  {
    icon: 'notifications',
    title: 'Notifications',
    text: 'Vos alertes récentes apparaîtront ici.',
  },
];

@Component({
  selector: 'app-dashboard',
  imports: [DatePipe, MatButtonModule, MatIconModule, MatProgressSpinnerModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './dashboard.html',
  styleUrl: './dashboard.scss',
})
export class Dashboard {
  private readonly auth = inject(AuthService);

  protected readonly upcomingSections = UPCOMING_SECTIONS;
  protected readonly user = rxResource({ stream: () => this.auth.currentUser() });
  protected readonly roleLabel = computed(() => {
    const role = this.user.value()?.role;
    return role ? ROLE_LABELS[role] : '';
  });
}
