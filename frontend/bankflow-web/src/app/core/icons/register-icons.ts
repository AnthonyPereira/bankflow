import { inject } from '@angular/core';
import { MatIconRegistry } from '@angular/material/icon';
import { DomSanitizer } from '@angular/platform-browser';

/** Chaque icône est un petit fichier SVG téléchargé à la demande depuis `public/icons`. */
export function registerIcons(): void {
  const sanitizer = inject(DomSanitizer);
  inject(MatIconRegistry).addSvgIconResolver((name) =>
    sanitizer.bypassSecurityTrustResourceUrl(`icons/${name}.svg`),
  );
}
