package com.bankflow.banking.application.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

import com.bankflow.banking.BankingApiTestSupport;
import com.bankflow.banking.application.customer.CustomerRefSynchronizer;
import com.bankflow.banking.application.transfer.TransferService;
import com.bankflow.banking.domain.account.AccountRepository;
import com.bankflow.banking.domain.customer.CustomerStatus;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/** Un échec au milieu du remplissage ne laisse aucune trace et ne bloque jamais le service. */
class DemoAccountsFailureIntegrationTests extends BankingApiTestSupport {

    @MockitoSpyBean private TransferService transferService;
    @Autowired private DemoAccountsSeeder seeder;
    @Autowired private CustomerRefSynchronizer synchronizer;
    @Autowired private AccountRepository accounts;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void unEchecAuMilieuNeLaisseAucuneTraceEtNeBloquePasLeDemarrage() {
        doThrow(new IllegalStateException("échec simulé"))
                .when(transferService)
                .createTransfer(any(), any(), any(), any(), any());
        var customerId = UUID.randomUUID();

        assertThatCode(() ->
                        synchronizer.syncCustomer(
                                customerId,
                                UUID.randomUUID(),
                                "demo.client@bankflow.local",
                                CustomerStatus.ACTIVE,
                                Instant.now()))
                .doesNotThrowAnyException();
        assertThatCode(seeder::seedOnStartup).doesNotThrowAnyException();

        assertThat(accounts.findByCustomerId(customerId, Pageable.unpaged()).getContent())
                .isEmpty();
        var operations =
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM transactions t JOIN accounts a ON a.id = t.account_id"
                                + " WHERE a.customer_id = ?",
                        Integer.class,
                        customerId);
        assertThat(operations).isZero();
        var entries =
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM ledger_entries e JOIN accounts a ON a.id ="
                                + " e.account_id WHERE a.customer_id = ?",
                        Integer.class,
                        customerId);
        assertThat(entries).isZero();
    }
}
