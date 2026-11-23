import { resolveReturnUrl } from './return-url';

describe('resolveReturnUrl', () => {
  it('garde un chemin interne', () => {
    expect(resolveReturnUrl('/administration?onglet=1')).toBe('/administration?onglet=1');
  });

  it.each([
    ['absent', undefined],
    ['répété', ['/administration', '/tableau-de-bord']],
    ['relatif', 'administration'],
    ['externe', 'https://site-externe.example'],
    ['sans protocole', '//site-externe.example'],
    ['avec antislash', '/\\site-externe.example'],
  ])('remplace un chemin %s par le tableau de bord', (_cas, value) => {
    expect(resolveReturnUrl(value)).toBe('/tableau-de-bord');
  });
});
