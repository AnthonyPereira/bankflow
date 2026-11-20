import { BreakpointObserver } from '@angular/cdk/layout';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatListModule } from '@angular/material/list';
import { MatSidenavModule } from '@angular/material/sidenav';
import { MatToolbarModule } from '@angular/material/toolbar';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { map } from 'rxjs';
import { AuthService } from '../auth/auth.service';

const COMPACT_LAYOUT = '(max-width: 839.98px)';

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

  protected readonly isAdmin = this.auth.isAdmin;
  protected readonly isCompact = toSignal(
    inject(BreakpointObserver)
      .observe(COMPACT_LAYOUT)
      .pipe(map((state) => state.matches)),
    { initialValue: false },
  );
  protected readonly isMenuOpen = signal(false);
  protected readonly isNavOpened = computed(() => !this.isCompact() || this.isMenuOpen());

  protected closeMenu(): void {
    this.isMenuOpen.set(false);
  }

  protected logout(): void {
    this.auth.logout().subscribe({
      complete: () => void this.router.navigateByUrl('/connexion'),
    });
  }
}
