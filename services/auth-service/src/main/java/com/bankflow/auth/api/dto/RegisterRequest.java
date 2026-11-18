package com.bankflow.auth.api.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.nio.charset.StandardCharsets;

public record RegisterRequest(
        @NotBlank(message = "L'adresse email est obligatoire.")
                @Email(message = "L'adresse email est invalide.")
                @Size(max = 254, message = "L'adresse email est trop longue.")
                String email,
        @NotBlank(message = "Le mot de passe est obligatoire.")
                @Size(min = 12, message = "Le mot de passe doit contenir au moins 12 caractères.")
                String password) {

    /** BCrypt ne prend en compte que les 72 premiers octets d'un mot de passe. */
    @AssertTrue(message = "Le mot de passe ne doit pas dépasser 72 octets.")
    boolean isPasswordWithinBcryptLimit() {
        return password == null || password.getBytes(StandardCharsets.UTF_8).length <= 72;
    }

    /** Évite d'écrire le mot de passe dans les logs. */
    @Override
    public String toString() {
        return "RegisterRequest[email=" + email + "]";
    }
}
