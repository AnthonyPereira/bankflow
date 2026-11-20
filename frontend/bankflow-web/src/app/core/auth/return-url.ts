export const DEFAULT_DESTINATION = '/tableau-de-bord';

/**
 * Seul un chemin interne est accepté, pour ne jamais rediriger vers un autre site. Un paramètre
 * répété (`?returnUrl=/a&returnUrl=/b`) arrive sous forme de tableau et est ignoré, tout comme
 * `//site` ou `/\site`, que le navigateur interprète comme une adresse externe.
 */
export function resolveReturnUrl(value: unknown): string {
  if (typeof value !== 'string' || !value.startsWith('/')) {
    return DEFAULT_DESTINATION;
  }
  return value.startsWith('//') || value.startsWith('/\\') ? DEFAULT_DESTINATION : value;
}
