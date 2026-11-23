package com.bankflow.banking.domain.account;

import static org.assertj.core.api.Assertions.assertThat;

import com.bankflow.banking.IbanTestSupport;
import java.math.BigInteger;
import java.security.SecureRandom;
import org.junit.jupiter.api.Test;

class AccountNumberGeneratorTests {

    private final AccountNumberGenerator generator = new AccountNumberGenerator(new SecureRandom());

    @Test
    void genereUnIbanFrancaisDe27Caracteres() {
        var iban = generator.generateIban();

        assertThat(iban).hasSize(27).startsWith("FR").matches("FR[0-9]{25}");
        assertThat(iban.substring(4, 9)).isEqualTo("99999");
        assertThat(iban.substring(9, 14)).isEqualTo("00001");
    }

    @Test
    void laCleIbanVerifieLeModulo97() {
        var iban = generator.generateIban();

        assertThat(computeIbanRemainder(iban)).isEqualTo(1);
    }

    @Test
    void laCleRibEstCorrecte() {
        var bban = generator.generateIban().substring(4);

        var bank = Long.parseLong(bban.substring(0, 5));
        var branch = Long.parseLong(bban.substring(5, 10));
        var account = Long.parseLong(bban.substring(10, 21));
        var key = Long.parseLong(bban.substring(21, 23));
        assertThat((89 * bank + 15 * branch + 3 * account + key) % 97).isZero();
    }

    @Test
    void milleNumerosGeneresSontTousValides() {
        for (int i = 0; i < 1000; i++) {
            var iban = generator.generateIban();
            assertThat(iban).hasSize(27);
            assertThat(computeIbanRemainder(iban)).as(iban).isEqualTo(1);
            assertThat(IbanTestSupport.isValidIban(iban)).as(iban).isTrue();
        }
    }

    @Test
    void reconnaitUnIbanReelEtRefuseUneCleFausse() {
        // IBAN d'exemple publié dans la norme ISO 13616
        assertThat(IbanTestSupport.isValidIban("GB82WEST12345698765432")).isTrue();
        assertThat(IbanTestSupport.isValidIban("GB83WEST12345698765432")).isFalse();
        assertThat(IbanTestSupport.isValidIban("")).isFalse();
        assertThat(IbanTestSupport.isValidIban(null)).isFalse();
    }

    /** Implémentation indépendante du contrôle : déplace les 4 premiers caractères en fin. */
    private static int computeIbanRemainder(String iban) {
        var rearranged = iban.substring(4) + iban.substring(0, 4);
        var digits = new StringBuilder();
        for (char character : rearranged.toCharArray()) {
            digits.append(Character.isDigit(character) ? character - '0' : character - 'A' + 10);
        }
        return new BigInteger(digits.toString()).mod(BigInteger.valueOf(97)).intValue();
    }
}
