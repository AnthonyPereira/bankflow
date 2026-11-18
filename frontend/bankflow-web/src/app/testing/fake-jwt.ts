import { Role } from '../shared/models/auth.models';

/** Construit un JWT lisible pour les tests. Sa signature n'est jamais vérifiée côté frontend. */
export function fakeJwt(role: Role = 'ROLE_CLIENT', id = 'utilisateur'): string {
  const encode = (value: object) =>
    btoa(JSON.stringify(value)).replaceAll('+', '-').replaceAll('/', '_').replaceAll('=', '');
  return `${encode({ alg: 'none' })}.${encode({ sub: id, role })}.signature`;
}
