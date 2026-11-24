package com.bankflow.banking.domain.money;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bankflow.banking.domain.money.exception.CurrencyMismatchException;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class MoneyTests {

    private static Money eur(String amount) {
        return new Money(new BigDecimal(amount), Currency.EUR);
    }

    @Test
    void additionneDeuxMontantsDeLaMemeDevise() {
        assertThat(eur("10.10").add(eur("0.20"))).isEqualTo(eur("10.30"));
    }

    @Test
    void soustraitEtPeutDevenirNegatif() {
        var result = eur("5").subtract(eur("7.5"));

        assertThat(result).isEqualTo(eur("-2.50"));
        assertThat(result.isNegative()).isTrue();
    }

    @Test
    void refuseLesDevisesDifferentes() {
        var usd = new Money(BigDecimal.ONE, Currency.USD);

        assertThatThrownBy(() -> eur("1").add(usd)).isInstanceOf(CurrencyMismatchException.class);
        assertThatThrownBy(() -> eur("1").subtract(usd))
                .isInstanceOf(CurrencyMismatchException.class);
    }

    @Test
    void normaliseLEchelleAQuatreDecimales() {
        assertThat(eur("1").amount().scale()).isEqualTo(4);
        assertThat(eur("1")).isEqualTo(eur("1.0000"));
    }

    @Test
    void refuseUnMontantQuiDemanderaitPlusDeQuatreDecimales() {
        assertThatThrownBy(() -> eur("0.00001")).isInstanceOf(ArithmeticException.class);
    }

    @Test
    void detecteLeZero() {
        assertThat(Money.createZero(Currency.USD).isZero()).isTrue();
        assertThat(eur("0.0001").isZero()).isFalse();
        assertThat(eur("0.0001").isNegative()).isFalse();
    }

    @Test
    void exposeUneChaineADeuxDecimales() {
        assertThat(eur("150").toApiString()).isEqualTo("150.00");
        assertThat(eur("1234.5").toApiString()).isEqualTo("1234.50");
        assertThat(Money.createZero(Currency.EUR).toApiString()).isEqualTo("0.00");
        assertThat(eur("-3").toApiString()).isEqualTo("-3.00");
    }
}
