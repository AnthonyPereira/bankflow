package com.bankflow.auth.application.demo.model;

import com.bankflow.auth.domain.user.Role;

/** Compte de démonstration : son mot de passe est public, affiché sur la page de connexion. */
public record DemoAccount(String email, String password, Role role) {

    /** Évite d'écrire le mot de passe dans les logs, même s'il est public. */
    @Override
    public String toString() {
        return "DemoAccount[email=" + email + ", role=" + role + "]";
    }
}
