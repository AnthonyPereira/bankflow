package com.bankflow.banking.application.demo;

import static org.assertj.core.api.Assertions.assertThat;

import com.bankflow.banking.BankingApiTestSupport;
import com.bankflow.banking.application.customer.CustomerRefSynchronizer;
import com.bankflow.banking.domain.account.AccountRepository;
import com.bankflow.banking.domain.customer.CustomerStatus;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Pageable;

/** Avec {@code DEMO_ACCOUNTS_ENABLED=false}, aucun compte de démonstration n'est créé. */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "bankflow.demo.enabled=false")
class DemoAccountsDisabledIntegrationTests extends BankingApiTestSupport {

    @Autowired private DemoAccountsSeeder seeder;
    @Autowired private CustomerRefSynchronizer synchronizer;
    @Autowired private AccountRepository accounts;

    @Test
    void rienNEstCreeQuandLeServiceEstDesactive() {
        var customerId = UUID.randomUUID();
        synchronizer.syncCustomer(
                customerId,
                UUID.randomUUID(),
                "demo.client@bankflow.local",
                CustomerStatus.ACTIVE,
                Instant.now());

        seeder.seedOnStartup();

        assertThat(accounts.findByCustomerId(customerId, Pageable.unpaged()).getContent())
                .isEmpty();
    }
}
