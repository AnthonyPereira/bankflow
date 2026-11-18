import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';

/** Présentation commune aux écrans de connexion et d'inscription. */
@Component({
  selector: 'app-auth-card',
  imports: [MatIconModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <main class="page">
      <section class="card">
        <p class="brand">
          <mat-icon aria-hidden="true">account_balance</mat-icon>
          BankFlow
        </p>
        <h1>{{ heading() }}</h1>
        <p class="intro">{{ intro() }}</p>
        <ng-content />
      </section>
      <p class="notice">Projet pédagogique : banque fictive, aucune donnée bancaire réelle.</p>
    </main>
  `,
  styles: `
    .page {
      box-sizing: border-box;
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      gap: 16px;
      min-height: 100dvh;
      padding: 24px 16px;
      background: var(--mat-sys-surface-container-low);
    }

    .card {
      box-sizing: border-box;
      width: min(100%, 420px);
      padding: 32px;
      border-radius: var(--mat-sys-corner-extra-large);
      background: var(--mat-sys-surface);
      box-shadow: var(--mat-sys-level1);
    }

    .brand {
      display: flex;
      align-items: center;
      gap: 8px;
      margin-bottom: 24px;
      color: var(--mat-sys-primary);
      font: var(--mat-sys-title-medium);
    }

    h1 {
      font: var(--mat-sys-headline-small);
    }

    .intro {
      margin: 4px 0 24px;
      color: var(--mat-sys-on-surface-variant);
    }

    .notice {
      max-width: 420px;
      color: var(--mat-sys-on-surface-variant);
      font: var(--mat-sys-body-small);
      text-align: center;
    }

    @media (max-width: 480px) {
      .card {
        padding: 24px 16px;
      }
    }
  `,
})
export class AuthCard {
  readonly heading = input.required<string>();
  readonly intro = input.required<string>();
}
