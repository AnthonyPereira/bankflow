import { Pipe, PipeTransform } from '@angular/core';

const EMPTY_VALUE = '—';

/** `Intl` accepte une chaîne décimale à l'exécution, mais la bibliothèque ES2022 ne le type pas. */
interface StringNumberFormat {
  format(value: string): string;
}

/**
 * Formate un montant décimal reçu en chaîne (`"1234.50"`) avec sa devise. La chaîne est transmise
 * telle quelle à `Intl` : aucune conversion en nombre JavaScript, donc aucune perte de précision.
 */
@Pipe({ name: 'amount' })
export class AmountPipe implements PipeTransform {
  transform(value: string | null | undefined, currency: string | null | undefined): string {
    if (!value || !currency) {
      return EMPTY_VALUE;
    }
    const formatter = new Intl.NumberFormat('fr-FR', {
      style: 'currency',
      currency,
    }) as unknown as StringNumberFormat;
    return formatter.format(value);
  }
}
