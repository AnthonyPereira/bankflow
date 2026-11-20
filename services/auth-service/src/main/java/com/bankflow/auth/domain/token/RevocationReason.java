package com.bankflow.auth.domain.token;

/** Motif de révocation d'un refresh token : seule une rotation ouvre le délai de grâce. */
public enum RevocationReason {
    /** Remplacé par un nouveau jeton de la même famille. */
    ROTATION,
    /** Session terminée par l'utilisateur. */
    LOGOUT,
    /** Famille révoquée après la réutilisation d'un jeton remplacé : vol possible. */
    REUSE_DETECTED,
    /** Famille révoquée parce que le compte est désactivé ou supprimé. */
    ACCOUNT_DISABLED
}
