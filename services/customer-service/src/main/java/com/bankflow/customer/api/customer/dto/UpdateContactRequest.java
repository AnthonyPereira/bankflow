package com.bankflow.customer.api.customer.dto;

import com.bankflow.customer.application.customer.ContactDetails;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Tous les champs sont facultatifs. Les chaînes vides deviennent {@code null} avant la validation,
 * pour qu'effacer un champ ne soit pas refusé par le format du téléphone.
 */
public record UpdateContactRequest(
        @Size(max = 100, message = "100 caractères au maximum") String firstName,
        @Size(max = 100, message = "100 caractères au maximum") String lastName,
        @Pattern(
                        regexp = "^\\+?[0-9 .-]{6,20}$",
                        message = "Numéro de téléphone invalide (6 à 20 chiffres)")
                String phone,
        @Size(max = 255, message = "255 caractères au maximum") String address) {

    public UpdateContactRequest {
        firstName = stripToNull(firstName);
        lastName = stripToNull(lastName);
        phone = stripToNull(phone);
        address = stripToNull(address);
    }

    public ContactDetails toContactDetails() {
        return new ContactDetails(firstName, lastName, phone, address);
    }

    private static String stripToNull(String value) {
        if (value == null) {
            return null;
        }
        var stripped = value.strip();
        return stripped.isEmpty() ? null : stripped;
    }
}
