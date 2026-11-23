import { DateFrPipe } from './date-fr.pipe';

describe('DateFrPipe', () => {
  const pipe = new DateFrPipe();
  // Heure locale du navigateur : le test reste valable quel que soit le fuseau.
  const iso = new Date(2026, 10, 23, 14, 5).toISOString();

  it("formate la date et l'heure", () => {
    expect(pipe.transform(iso)).toBe('23/11/2026 14:05');
  });

  it('formate la date seule', () => {
    expect(pipe.transform(iso, 'date')).toBe('23/11/2026');
  });

  it.each([null, undefined, '', 'pas une date', '2026-13-45T99:00:00Z'])(
    'affiche un tiret pour %j',
    (value) => {
      expect(pipe.transform(value)).toBe('—');
    },
  );
});
