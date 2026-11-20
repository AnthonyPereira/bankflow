import { ChangeDetectionStrategy, Component } from '@angular/core';

@Component({
  selector: 'app-admin',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h1>Administration</h1>
    <p>Les outils d'administration seront bientôt disponibles.</p>
  `,
  styles: `
    h1 {
      font: var(--mat-sys-headline-medium);
    }

    p {
      margin-top: 4px;
      color: var(--mat-sys-on-surface-variant);
    }
  `,
})
export class Admin {}
