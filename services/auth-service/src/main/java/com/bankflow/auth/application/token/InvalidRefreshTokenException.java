package com.bankflow.auth.application.token;

public class InvalidRefreshTokenException extends RuntimeException {

    private final boolean sessionEnded;

    public InvalidRefreshTokenException() {
        this(true);
    }

    private InvalidRefreshTokenException(boolean sessionEnded) {
        super("La session a expiré. Veuillez vous reconnecter.");
        this.sessionEnded = sessionEnded;
    }

    /**
     * Un autre onglet vient de faire tourner le jeton : la session reste valide et le navigateur
     * détient déjà le nouveau cookie, qu'il ne faut surtout pas effacer.
     */
    public static InvalidRefreshTokenException concurrentRotation() {
        return new InvalidRefreshTokenException(false);
    }

    public boolean isSessionEnded() {
        return sessionEnded;
    }
}
