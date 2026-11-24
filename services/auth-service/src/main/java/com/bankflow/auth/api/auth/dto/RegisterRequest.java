package com.bankflow.auth.api.auth.dto;

import com.bankflow.auth.api.auth.validation.MaxUtf8Bytes;
import com.bankflow.auth.api.auth.validation.NormalizedEmailSize;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * La longueur de l'adresse est vérifiée telle qu'elle sera enregistrée, et le mot de passe en
 * octets : BCrypt ne prend en compte que ses 72 premiers octets.
 */
public record RegisterRequest(
        @NotBlank(message = "L'adresse email est obligatoire.")
                @Email(message = "L'adresse email est invalide.")
                @NormalizedEmailSize(max = 254, message = "L'adresse email est trop longue.")
                String email,
        @NotBlank(message = "Le mot de passe est obligatoire.")
                @Size(min = 12, message = "Le mot de passe doit contenir au moins 12 caractères.")
                @MaxUtf8Bytes(
                        value = 72,
                        message = "Le mot de passe ne doit pas dépasser 72 octets.")
                String password) {

    /** Évite d'écrire le mot de passe dans les logs. */
    @Override
    public String toString() {
        return "RegisterRequest[email=" + email + "]";
    }
}
