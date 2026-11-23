import { AmountPipe } from './amount.pipe';

/** `Intl` sépare les milliers et la devise par des espaces insécables : on les normalise. */
function normalize(text: string): string {
  return text.replace(/\s/g, ' ');
}

describe('AmountPipe', () => {
  const pipe = new AmountPipe();

  it('formate un montant en euros', () => {
    expect(normalize(pipe.transform('1234.50', 'EUR'))).toBe('1 234,50 €');
  });

  it('formate zéro', () => {
    expect(normalize(pipe.transform('0.00', 'EUR'))).toBe('0,00 €');
  });

  it('formate un montant en dollars', () => {
    expect(normalize(pipe.transform('1234.50', 'USD'))).toBe('1 234,50 $US');
  });

  it('ne perd aucune précision sur un grand montant', () => {
    expect(normalize(pipe.transform('9007199254740993.10', 'EUR'))).toBe(
      '9 007 199 254 740 993,10 €',
    );
  });

  it.each([null, undefined, ''])('affiche un tiret pour %j', (value) => {
    expect(pipe.transform(value, 'EUR')).toBe('—');
  });
});
