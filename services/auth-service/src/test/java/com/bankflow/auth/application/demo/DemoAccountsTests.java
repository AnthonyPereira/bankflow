package com.bankflow.auth.application.demo;

import static org.assertj.core.api.Assertions.assertThat;

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
}
