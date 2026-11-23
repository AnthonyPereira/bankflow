/**
 * Libellé de la plage affichée d'une liste paginée : « 1 – 20 sur 25 » (« 0 sur 0 » si vide).
 * Le paginateur Material n'est pas utilisé : son `MatSelect` entraîne le code des surcouches (overlay)
 * dans le bundle initial, qui dépasserait alors son budget.
 */
export function getRangeLabel(pageIndex: number, pageSize: number, length: number): string {
  if (length === 0 || pageSize === 0) {
    return '0 sur 0';
  }
  const start = pageIndex * pageSize;
  const end = Math.min(start + pageSize, length);
  return `${start + 1} – ${end} sur ${length}`;
}
