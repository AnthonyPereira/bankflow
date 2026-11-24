import { AbstractControl, ValidationErrors } from '@angular/forms';

export const MAX_AMOUNT_CENTS = 1_000_000;

export const AMOUNT_NOT_A_NUMBER_MESSAGE = 'Saisissez un nombre, par exemple 150,00.';
export const AMOUNT_POSITIVE_MESSAGE = 'Le montant doit être positif.';
export const AMOUNT_DECIMALS_MESSAGE = 'Le montant a au plus 2 décimales.';
export const AMOUNT_MAX_MESSAGE = 'Le montant maximal par opération est de 10 000,00.';

/** Entier simple, ou groupes de milliers séparés par une espace (« 1 000,50 »), partie décimale facultative. */
const AMOUNT_PATTERN = /^(-?)(\d+|\d{1,3}(?:\s\d{3})+)(?:[.,](\d+))?$/;
const CENTS_PER_UNIT = 100;

export type AmountParse = { amount: string } | { error: string };

/**
 * Convertit la saisie (virgule ou point) en chaîne décimale à 2 chiffres (`"150.00"`), sans passer
 * par un nombre à virgule flottante. Les règles sont celles de l'API : positif, 2 décimales, 10 000 max.
 */
export function parseAmount(input: string): AmountParse {
  const text = input.trim();
  if (text === '') {
    return { error: AMOUNT_POSITIVE_MESSAGE };
  }
  const match = AMOUNT_PATTERN.exec(text);
  if (!match) {
    return { error: AMOUNT_NOT_A_NUMBER_MESSAGE };
  }
  const [, sign, rawUnits, rawDecimals = ''] = match;
  if (sign) {
    return { error: AMOUNT_POSITIVE_MESSAGE };
  }
  const units = rawUnits.replace(/\D/g, '');
  // Comme l'API, plus de 2 chiffres après le séparateur sont refusés, zéros compris (« 10,500 »).
  const decimals = rawDecimals;
  if (decimals.length > 2) {
    return { error: AMOUNT_DECIMALS_MESSAGE };
  }
  const cents = BigInt(units) * BigInt(CENTS_PER_UNIT) + BigInt(decimals.padEnd(2, '0') || '0');
  if (cents <= 0n) {
    return { error: AMOUNT_POSITIVE_MESSAGE };
  }
  if (cents > BigInt(MAX_AMOUNT_CENTS)) {
    return { error: AMOUNT_MAX_MESSAGE };
  }
  return { amount: `${cents / 100n}.${String(cents % 100n).padStart(2, '0')}` };
}

/** Le montant saisi suit les règles de l'API (positif, 2 décimales, 10 000 au plus). */
export function validateAmount(control: AbstractControl<string>): ValidationErrors | null {
  const result = parseAmount(control.value);
  return 'error' in result ? { amount: result.error } : null;
}
