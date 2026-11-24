package com.bankflow.banking;

import java.math.BigInteger;

/** Contrôle de test de la clé IBAN : le code de production n'a besoin que de la générer. */
public final class IbanTestSupport {

    private static final BigInteger NINETY_SEVEN = BigInteger.valueOf(97);

    private IbanTestSupport() {}

    /** Vérifie la clé IBAN (ISO 13616, modulo 97) d'un IBAN sans espaces. */
    public static boolean isValidIban(String iban) {
        if (iban == null || iban.length() < 5 || !iban.matches("[A-Z]{2}[0-9]{2}[A-Z0-9]+")) {
            return false;
        }
        var rearranged = iban.substring(4) + iban.substring(0, 4);
        var digits = new StringBuilder();
        for (char character : rearranged.toCharArray()) {
            digits.append(Character.digit(character, 36));
        }
        return new BigInteger(digits.toString()).mod(NINETY_SEVEN).intValue() == 1;
    }
}
