import { Pipe, PipeTransform } from '@angular/core';

const EMPTY_VALUE = '—';
const DECIMAL_PATTERN = /^-?\d+(\.\d+)?$/;

/** `Intl` accepte une chaîne décimale à l'exécution, mais la bibliothèque ES2022 ne le type pas. */
interface StringNumberFormat {
  format(value: string): string;
}

/** Un `Intl.NumberFormat` coûte cher à créer : on le garde par devise. */
const formatters = new Map<string, StringNumberFormat>();

/** Renvoie `null` si le code de devise est refusé par `Intl` (RangeError). */
function getFormatter(currency: string): StringNumberFormat | null {
  let formatter = formatters.get(currency);
  if (!formatter) {
    try {
      formatter = new Intl.NumberFormat('fr-FR', {
        style: 'currency',
        currency,
      }) as unknown as StringNumberFormat;
    } catch {
      return null;
    }
    formatters.set(currency, formatter);
  }
  return formatter;
}

/**
 * Formate un montant décimal reçu en chaîne (`"1234.50"`) avec sa devise. La chaîne est transmise
 * telle quelle à `Intl` : aucune conversion en nombre JavaScript, donc aucune perte de précision.
 */
@Pipe({ name: 'amount' })
export class AmountPipe implements PipeTransform {
  transform(value: string | null | undefined, currency: string | null | undefined): string {
    if (!value || !currency || !DECIMAL_PATTERN.test(value)) {
      return EMPTY_VALUE;
    }
    const formatter = getFormatter(currency);
    return formatter ? formatter.format(value) : EMPTY_VALUE;
  }
}
