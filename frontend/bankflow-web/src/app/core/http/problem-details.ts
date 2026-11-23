import { HttpErrorResponse } from '@angular/common/http';

export const UNEXPECTED_ERROR_MESSAGE = 'Une erreur est survenue. Réessayez plus tard.';
export const NETWORK_ERROR_MESSAGE = 'Le serveur est injoignable. Réessayez dans un instant.';

/** Corps d'erreur de l'API (ProblemDetail), `null` s'il n'a pas la forme d'un objet. */
function getProblemBody(error: unknown): Record<string, unknown> | null {
  const body: unknown = error instanceof HttpErrorResponse ? error.error : null;
  return typeof body === 'object' && body !== null ? (body as Record<string, unknown>) : null;
}

/** Code métier du ProblemDetail, `null` si l'erreur n'en porte pas. */
export function getErrorCode(error: unknown): string | null {
  const code = getProblemBody(error)?.['code'];
  return typeof code === 'string' ? code : null;
}

/** Messages de validation par champ (`errors` du ProblemDetail), vide si le serveur n'en donne pas. */
export function getFieldErrors(error: unknown): Record<string, string> {
  const raw = getProblemBody(error)?.['errors'];
  const result: Record<string, string> = {};
  if (raw && typeof raw === 'object') {
    for (const [field, message] of Object.entries(raw)) {
      if (typeof message === 'string') {
        result[field] = message;
      }
    }
  }
  return result;
}

/** Une erreur réseau ou serveur laisse l'issue inconnue : la nouvelle tentative réutilise la clé. */
export function isRetryableError(error: unknown): boolean {
  return error instanceof HttpErrorResponse && (error.status === 0 || error.status >= 500);
}

/**
 * Traduit une erreur de l'API en français : message de la table de la fonctionnalité pour le `code`
 * du ProblemDetail, sinon son `detail`, sinon `fallback`. Un serveur injoignable a son propre message.
 */
export function translateProblem(
  error: unknown,
  messages: Readonly<Record<string, string>>,
  fallback: string = UNEXPECTED_ERROR_MESSAGE,
): string {
  if (!(error instanceof HttpErrorResponse)) {
    return fallback;
  }
  if (error.status === 0) {
    return NETWORK_ERROR_MESSAGE;
  }
  const code = getErrorCode(error);
  if (code !== null && Object.hasOwn(messages, code)) {
    return messages[code];
  }
  const detail = getProblemBody(error)?.['detail'];
  return typeof detail === 'string' && detail ? detail : fallback;
}

/**
 * Messages dont le sens dépend du rôle : un client se voit parler de son profil, un conseiller
 * du client sur lequel il agit.
 */
export function getRoleMessages(isAdmin: boolean): Record<string, string> {
  return isAdmin
    ? {
        CUSTOMER_SUSPENDED: "Ce client est suspendu : aucune opération n'est possible.",
        ACCOUNT_LIMIT_REACHED: 'Ce client a déjà 5 comptes ouverts.',
      }
    : {
        CUSTOMER_SUSPENDED: 'Votre profil est suspendu : contactez un conseiller.',
        ACCOUNT_LIMIT_REACHED: 'Vous avez atteint la limite de 5 comptes ouverts.',
      };
}
