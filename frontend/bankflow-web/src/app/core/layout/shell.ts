import { BreakpointObserver } from '@angular/cdk/layout';
import {
  ChangeDetectionStrategy,
  Component,
  computed,
  ElementRef,
  inject,
  signal,
  viewChild,
} from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatListModule } from '@angular/material/list';
import { MatSidenavModule } from '@angular/material/sidenav';
import { MatToolbarModule } from '@angular/material/toolbar';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { filter, map, skip } from 'rxjs';
import { AuthService } from '../auth/auth.service';
import { NotificationUiService } from '../notifications/notification-ui.service';
import { getAccountsTitle } from '../../features/accounts/accounts.models';
import { getOperationsTitle } from '../../features/operations/operations.models';

const COMPACT_LAYOUT = '(max-width: 839.98px)';
export const UNCONFIRMED_LOGOUT_MESSAGE =
  "La déconnexion n'a pas pu être confirmée par le serveur. Fermez le navigateur sur un poste partagé.";

@Component({
  selector: 'app-shell',
  imports: [
    MatButtonModule,
    MatIconModule,
    MatListModule,
    MatSidenavModule,
    MatToolbarModule,
    RouterLink,
    RouterLinkActive,
    RouterOutlet,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './shell.html',
  styleUrl: './shell.scss',
})
export class Shell {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly notifications = inject(NotificationUiService);
  private readonly main = viewChild.required<ElementRef<HTMLElement>>('main');

  protected readonly isAdmin = this.auth.isAdmin;
  protected readonly accountsTitle = computed(() => getAccountsTitle(this.isAdmin()));
  protected readonly operationsTitle = computed(() => getOperationsTitle(this.isAdmin()));
  protected readonly isCompact = toSignal(
    inject(BreakpointObserver)
      .observe(COMPACT_LAYOUT)
      .pipe(map((state) => state.matches)),
    { initialValue: false },
  );
  protected readonly isMenuOpen = signal(false);
  protected readonly isNavOpened = computed(() => !this.isCompact() || this.isMenuOpen());

  constructor() {
    // Au chargement de la page, le focus reste au début du document, là où le lecteur d'écran
    // l'attend. Ensuite, chaque navigation le place sur le nouveau contenu.
    const isFirstLoad = !this.router.navigated;
    this.router.events
      .pipe(
        filter((event) => event instanceof NavigationEnd),
        skip(isFirstLoad ? 1 : 0),
        takeUntilDestroyed(),
      )
      .subscribe(() => this.focusMain());
  }

  protected closeMenu(): void {
    this.isMenuOpen.set(false);
  }

  protected skipToContent(event: Event): void {
    event.preventDefault();
    this.focusMain();
  }

  protected logout(): void {
    this.auth.logout().subscribe({
      next: (isConfirmed) => {
        if (!isConfirmed) {
          this.notifications.showError(UNCONFIRMED_LOGOUT_MESSAGE);
        }
      },
      complete: () => void this.router.navigateByUrl('/connexion'),
    });
  }

  private focusMain(): void {
    this.main().nativeElement.focus();
  }
}
