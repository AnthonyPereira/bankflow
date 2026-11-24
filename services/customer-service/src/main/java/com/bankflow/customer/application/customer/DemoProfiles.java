package com.bankflow.customer.application.customer;

import com.bankflow.customer.application.customer.model.ContactDetails;
import java.util.Map;
import java.util.Optional;

/**
 * Profils fictifs des comptes de démonstration, pour que la vitrine ne montre jamais un profil
 * vide. La correspondance se fait par email ; un email inconnu n'a pas de profil.
 */
public final class DemoProfiles {

    private static final String PHONE = "+33 6 00 00 00 00";
    private static final String ADDRESS = "1 rue de la Démo, 75000 Paris";

    private static final Map<String, ContactDetails> BY_EMAIL =
            Map.of(
                    "demo.client@bankflow.local",
                    new ContactDetails("Camille", "Martin", PHONE, ADDRESS));

    private DemoProfiles() {}

    /**
     * @param email email déjà normalisé (minuscules, sans espaces)
     */
    public static Optional<ContactDetails> findByEmail(String email) {
        return Optional.ofNullable(BY_EMAIL.get(email));
    }
}
