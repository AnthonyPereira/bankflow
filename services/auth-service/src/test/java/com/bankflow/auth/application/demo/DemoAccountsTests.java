package com.bankflow.auth.application.demo;

import static org.assertj.core.api.Assertions.assertThat;

import com.bankflow.auth.application.demo.model.DemoAccount;
import org.junit.jupiter.api.Test;

class DemoAccountsTests {

    @Test
    void laListeContientLeClientEtLAdministrateurQuandLaDemonstrationEstActive() {
        assertThat(new DemoAccounts(true).list())
                .extracting(DemoAccount::email)
                .containsExactly("demo.client@bankflow.local", "demo.admin@bankflow.local");
    }

    @Test
    void laListeEstMasqueeQuandLaDemonstrationEstCoupee() {
        assertThat(new DemoAccounts(false).list()).isEmpty();
    }

    @Test
    void lesAdressesDeDemonstrationSontReconnuesQuelQueSoitLeDrapeau() {
        for (var enabled : new boolean[] {true, false}) {
            var accounts = new DemoAccounts(enabled);

            assertThat(accounts.isDemoEmail("demo.client@bankflow.local")).isTrue();
            assertThat(accounts.isDemoEmail(" Demo.Admin@BankFlow.local ")).isTrue();
            assertThat(accounts.isDemoEmail("client@bankflow.local")).isFalse();
        }
    }
}
