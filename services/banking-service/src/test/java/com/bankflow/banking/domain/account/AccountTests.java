package com.bankflow.banking.domain.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bankflow.banking.domain.account.exception.AccountBalanceNotZeroException;
import com.bankflow.banking.domain.account.exception.AccountClosedException;
import com.bankflow.banking.domain.money.Currency;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class AccountTests {

    private static final Instant NOW = Instant.parse("2026-11-23T10:00:00Z");

    private static Account openAccount() {
        return Account.open(
                UUID.randomUUID(), "FR7699999000010000000000000", Currency.EUR, null, NOW);
    }

    @Test
    void unCompteOuvertEstActifAvecUnSoldeNul() {
        var account = openAccount();

        assertThat(account.getStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(account.getBalance().isZero()).isTrue();
        assertThat(account.getClosedAt()).isNull();
        assertThat(account.isClosed()).isFalse();
    }

    @Test
    void clotureUnCompteASoldeNul() {
        var account = openAccount();

        account.close(NOW.plusSeconds(5));

        assertThat(account.isClosed()).isTrue();
        assertThat(account.getClosedAt()).isEqualTo(NOW.plusSeconds(5));
    }

    @Test
    void refuseLaClotureSiLeSoldeNEstPasNul() {
        var account = openAccount();
        ReflectionTestUtils.setField(account, "balance", new BigDecimal("0.0100"));

        assertThatThrownBy(() -> account.close(NOW))
                .isInstanceOf(AccountBalanceNotZeroException.class);
        assertThat(account.isClosed()).isFalse();
    }

    @Test
    void refuseUneSecondeCloture() {
        var account = openAccount();
        account.close(NOW);

        assertThatThrownBy(() -> account.close(NOW.plusSeconds(1)))
                .isInstanceOf(AccountClosedException.class);
        assertThat(account.getClosedAt()).isEqualTo(NOW);
    }
}
