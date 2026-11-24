package com.bankflow.banking.domain.account;

import java.math.BigInteger;
import java.security.SecureRandom;

/**
 * Génère des IBAN français fictifs mais valides (clé RIB et clé IBAN correctes). Le code banque
 * {@code 99999} n'existe pas : aucun numéro ne peut désigner un vrai compte.
 */
public class AccountNumberGenerator {

    private static final String BANK_CODE = "99999";
    private static final String BRANCH_CODE = "00001";
    private static final int ACCOUNT_DIGITS = 11;
    private static final long ACCOUNT_BOUND = 100_000_000_000L;
    private static final BigInteger NINETY_SEVEN = BigInteger.valueOf(97);

    private final SecureRandom random;

    public AccountNumberGenerator(SecureRandom random) {
        this.random = random;
    }

    /** Renvoie un IBAN de 27 caractères, sans espaces. */
    public String generateIban() {
        var accountPart =
                String.format("%0" + ACCOUNT_DIGITS + "d", random.nextLong(ACCOUNT_BOUND));
        var bban = BANK_CODE + BRANCH_CODE + accountPart + computeRibKey(accountPart);
        return "FR" + computeIbanCheckDigits(bban) + bban;
    }

    private static String computeRibKey(String accountPart) {
        long remainder =
                (89L * Long.parseLong(BANK_CODE)
                                + 15L * Long.parseLong(BRANCH_CODE)
                                + 3L * Long.parseLong(accountPart))
                        % 97;
        return String.format("%02d", 97 - remainder);
    }

    private static String computeIbanCheckDigits(String bban) {
        var remainder = convertToDigits(bban + "FR00").mod(NINETY_SEVEN).intValue();
        return String.format("%02d", 98 - remainder);
    }

    /** Les lettres valent 10 (A) à 35 (Z), les chiffres restent tels quels. */
    private static BigInteger convertToDigits(String text) {
        var digits = new StringBuilder();
        for (char character : text.toCharArray()) {
            digits.append(Character.digit(character, 36));
        }
        return new BigInteger(digits.toString());
    }
}
