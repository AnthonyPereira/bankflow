import { HttpErrorResponse } from '@angular/common/http';
import {
  getErrorCode,
  getFieldErrors,
  getRoleMessages,
  isRetryableError,
  NETWORK_ERROR_MESSAGE,
  translateProblem,
  UNEXPECTED_ERROR_MESSAGE,
} from './problem-details';

function build(body: unknown, status = 409): HttpErrorResponse {
  return new HttpErrorResponse({ status, error: body });
}

describe('problem-details', () => {
  const messages = { KNOWN: 'Message connu.' };

  describe('translateProblem', () => {
    it('traduit le code connu', () => {
      expect(translateProblem(build({ code: 'KNOWN', detail: 'autre' }), messages)).toBe(
        'Message connu.',
      );
    });

    it('se replie sur le detail du serveur pour un code inconnu', () => {
      expect(translateProblem(build({ code: 'AUTRE', detail: 'Détail.' }), messages)).toBe(
        'Détail.',
      );
    });

    it('se replie sur le message par défaut, ou sur celui de la fonctionnalité', () => {
      expect(translateProblem(build(null, 500), messages)).toBe(UNEXPECTED_ERROR_MESSAGE);
      expect(translateProblem(new Error('x'), messages)).toBe(UNEXPECTED_ERROR_MESSAGE);
      expect(translateProblem(build(null, 404), messages, 'Introuvable.')).toBe('Introuvable.');
    });

    it('ne prend pas un nom hérité de Object pour un code', () => {
      expect(translateProblem(build({ code: 'toString' }), messages)).toBe(
        UNEXPECTED_ERROR_MESSAGE,
      );
    });

    it('signale un serveur injoignable', () => {
      expect(translateProblem(build(null, 0), messages)).toBe(NETWORK_ERROR_MESSAGE);
    });
  });

  it('lit le code métier', () => {
    expect(getErrorCode(build({ code: 'KNOWN' }))).toBe('KNOWN');
    expect(getErrorCode(build({ code: 3 }))).toBeNull();
    expect(getErrorCode(build(null))).toBeNull();
    expect(getErrorCode(new Error('x'))).toBeNull();
  });

  it('lit les erreurs par champ', () => {
    expect(getFieldErrors(build({ errors: { amount: 'Trop grand.', autre: 3 } }, 400))).toEqual({
      amount: 'Trop grand.',
    });
    expect(getFieldErrors(build(null, 400))).toEqual({});
  });

  it("considère réessayables les erreurs réseau et serveur, pas un refus de l'API", () => {
    expect(isRetryableError(build(null, 0))).toBe(true);
    expect(isRetryableError(build(null, 503))).toBe(true);
    expect(isRetryableError(build(null, 409))).toBe(false);
    expect(isRetryableError(new Error('x'))).toBe(false);
  });

  it('adapte les messages au rôle', () => {
    expect(getRoleMessages(false)['CUSTOMER_SUSPENDED']).toBe(
      'Votre profil est suspendu : contactez un conseiller.',
    );
    expect(getRoleMessages(true)['CUSTOMER_SUSPENDED']).toBe(
      "Ce client est suspendu : aucune opération n'est possible.",
    );
    expect(getRoleMessages(false)['ACCOUNT_LIMIT_REACHED']).toBe(
      'Vous avez atteint la limite de 5 comptes ouverts.',
    );
    expect(getRoleMessages(true)['ACCOUNT_LIMIT_REACHED']).toBe(
      'Ce client a déjà 5 comptes ouverts.',
    );
  });
});
