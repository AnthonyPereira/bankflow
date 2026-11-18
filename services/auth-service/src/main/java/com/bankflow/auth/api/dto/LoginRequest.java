package com.bankflow.auth.api.dto;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank(message = "L'adresse email est obligatoire.") String email,
        @NotBlank(message = "Le mot de passe est obligatoire.") String password) {

    /** Évite d'écrire le mot de passe dans les logs. */
    @Override
    public String toString() {
        return "LoginRequest[email=" + email + "]";
    }
}
