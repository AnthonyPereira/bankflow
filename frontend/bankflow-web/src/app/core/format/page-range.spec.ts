import { getRangeLabel } from './page-range';

describe('getRangeLabel', () => {
  it('affiche la plage de la première page', () => {
    expect(getRangeLabel(0, 20, 25)).toBe('1 – 20 sur 25');
  });

  it('borne la plage de la dernière page', () => {
    expect(getRangeLabel(1, 20, 25)).toBe('21 – 25 sur 25');
  });

  it('affiche « 0 sur 0 » quand la liste est vide', () => {
    expect(getRangeLabel(0, 20, 0)).toBe('0 sur 0');
  });
});
