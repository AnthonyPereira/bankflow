import { inject } from '@angular/core';
import { MatIconRegistry } from '@angular/material/icon';
import { DomSanitizer } from '@angular/platform-browser';

const ICON_NAME = /^[a-z0-9_]+$/;

/** Chaque icône est un petit fichier SVG téléchargé à la demande depuis `public/icons`. */
export function registerIcons(): void {
  const sanitizer = inject(DomSanitizer);
  inject(MatIconRegistry).addSvgIconResolver((name) =>
    ICON_NAME.test(name)
      ? // Sûr : le nom est validé et l'URL reste dans le dossier local des icônes.
        sanitizer.bypassSecurityTrustResourceUrl(`icons/${name}.svg`) // NOSONAR
      : null,
  );
}
