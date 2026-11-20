import { inject, Service } from '@angular/core';
import { MatSnackBar } from '@angular/material/snack-bar';

const DURATION_MS = 6000;

@Service()
export class NotificationUiService {
  private readonly snackBar = inject(MatSnackBar);

  showInfo(message: string): void {
    this.snackBar.open(message, 'Fermer', { duration: DURATION_MS });
  }

  showError(message: string): void {
    this.snackBar.open(message, 'Fermer', {
      duration: DURATION_MS,
      politeness: 'assertive',
    });
  }
}
