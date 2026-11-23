import {
  AMOUNT_DECIMALS_MESSAGE,
  AMOUNT_MAX_MESSAGE,
  AMOUNT_POSITIVE_MESSAGE,
  parseAmount,
} from './account-detail.models';

describe('parseAmount', () => {
  it.each([
    ['150', '150.00'],
    ['150,5', '150.50'],
    ['150.25', '150.25'],
    [' 0,01 ', '0.01'],
    ['10 000', '10000.00'],
    ['10000.00', '10000.00'],
    ['10,500', '10.50'],
    ['007', '7.00'],
  ])('accepte « %s » et l’envoie en « %s »', (input, expected) => {
    expect(parseAmount(input)).toEqual({ amount: expected });
  });

  it.each(['', 'abc', '-5', '0', '0,00', '1,2,3', ','])(
    'refuse « %s » comme non positif',
    (input) => {
      expect(parseAmount(input)).toEqual({ error: AMOUNT_POSITIVE_MESSAGE });
    },
  );

  it('refuse plus de 2 décimales', () => {
    expect(parseAmount('1,234')).toEqual({ error: AMOUNT_DECIMALS_MESSAGE });
  });

  it('refuse plus de 10 000,00', () => {
    expect(parseAmount('10000,01')).toEqual({ error: AMOUNT_MAX_MESSAGE });
    expect(parseAmount('99999999999999999999')).toEqual({ error: AMOUNT_MAX_MESSAGE });
  });
});
