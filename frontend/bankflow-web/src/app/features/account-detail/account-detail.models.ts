export const MAX_AMOUNT_CENTS = 1_000_000;
export const MAX_DESCRIPTION_LENGTH = 140;
export const MIN_REASON_LENGTH = 10;
export const MAX_REASON_LENGTH = 200;

export const AMOUNT_POSITIVE_MESSAGE = 'Le montant doit être positif.';
export const AMOUNT_DECIMALS_MESSAGE = 'Le montant a au plus 2 décimales.';
export const AMOUNT_MAX_MESSAGE = 'Le montant maximal par opération est de 10 000,00.';

const AMOUNT_PATTERN = /^(\d+)(?:[.,](\d*))?$/;
const CENTS_PER_UNIT = 100;

export type AmountParse = { amount: string } | { error: string };

/**
 * Convertit la saisie (virgule ou point) en chaîne décimale à 2 chiffres (`"150.00"`), sans passer
 * par un nombre à virgule flottante. Les règles sont celles de l'API : positif, 2 décimales, 10 000 max.
 */
export function parseAmount(input: string): AmountParse {
  const match = AMOUNT_PATTERN.exec(input.trim().replace(/\s/g, ''));
  if (!match) {
    return { error: AMOUNT_POSITIVE_MESSAGE };
  }
  const [, units, rawDecimals = ''] = match;
  // Des zéros en fin de partie décimale ne comptent pas (« 10,500 » reste valide).
  const decimals = rawDecimals.replace(/0+$/, '');
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
