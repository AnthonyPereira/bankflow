package com.bankflow.auth.domain.user;

/**
 * Un compte de démonstration reste toujours utilisable : son mot de passe est public et il peut
 * être verrouillé temporairement comme les autres, mais jamais désactivé.
 */
public class DemoAccountCannotBeDisabledException extends RuntimeException {

    public DemoAccountCannotBeDisabledException() {
        super("Un compte de démonstration ne peut pas être désactivé.");
    }
}
