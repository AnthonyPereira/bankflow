package com.bankflow.banking.application.demo;

import static org.assertj.core.api.Assertions.assertThat;

import com.bankflow.banking.BankingApiTestSupport;
import com.bankflow.banking.application.account.AccountService;
import com.bankflow.banking.application.customer.CustomerRefSynchronizer;
import com.bankflow.banking.application.operation.OperationService;
import com.bankflow.banking.domain.account.Account;
import com.bankflow.banking.domain.account.AccountRepository;
import com.bankflow.banking.domain.account.AccountStatus;
import com.bankflow.banking.domain.customer.CustomerRef;
import com.bankflow.banking.domain.customer.CustomerRefCreated;
import com.bankflow.banking.domain.customer.CustomerStatus;
import com.bankflow.banking.domain.money.Currency;
import com.bankflow.banking.domain.transaction.Actor;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

/** Création des comptes de démonstration du client de la vitrine. */
@RecordApplicationEvents
class DemoAccountsSeederIntegrationTests extends BankingApiTestSupport {

    private static final String DEMO_EMAIL = "demo.client@bankflow.local";

    @Autowired private DemoAccountsSeeder seeder;
    @Autowired private CustomerRefSynchronizer synchronizer;
    @Autowired private AccountRepository accounts;
    @Autowired private AccountService accountService;
    @Autowired private OperationService operations;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ApplicationEvents events;

    private CustomerRef syncCustomer(String email, CustomerStatus status) {
        var customerId = UUID.randomUUID();
        var userId = UUID.randomUUID();
        synchronizer.syncCustomer(customerId, userId, email, status, Instant.now());
        return customerRefs.findById(customerId).orElseThrow();
    }

    private List<Account> findAccounts(CustomerRef customer) {
        return accounts.findByCustomerId(customer.getCustomerId(), Pageable.unpaged()).getContent();
    }

    private int countTransactions(CustomerRef customer) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM transactions t JOIN accounts a ON a.id = t.account_id"
                        + " WHERE a.customer_id = ?",
                Integer.class,
                customer.getCustomerId());
    }

    @Test
    void laReceptionDeLaReferenceCreeLesDeuxComptesEtLHistorique() {
        var customer = syncCustomer(DEMO_EMAIL, CustomerStatus.ACTIVE);

        var all = findAccounts(customer);
        assertThat(all).hasSize(2);
        var current = all.stream().filter(a -> "Compte courant".equals(a.getLabel())).findFirst();
        var savings = all.stream().filter(a -> "Livret épargne".equals(a.getLabel())).findFirst();
        assertThat(current).isPresent();
        assertThat(savings).isPresent();
        assertThat(current.get().getBalance().amount()).isEqualByComparingTo("2225.50");
        assertThat(savings.get().getBalance().amount()).isEqualByComparingTo("200.00");
        assertThat(all).allMatch(a -> a.getCurrency() == Currency.EUR);
        // 3 opérations sur le compte courant + virement sortant, et virement entrant sur le livret
        assertThat(countTransactions(customer)).isEqualTo(5);
        assertLedgerInvariants(
                jdbc, current.get().getId().toString(), savings.get().getId().toString());
        var performedBy =
                jdbc.queryForList(
                        "SELECT DISTINCT t.performed_by_role FROM transactions t"
                                + " JOIN accounts a ON a.id = t.account_id WHERE a.customer_id = ?",
                        String.class,
                        customer.getCustomerId());
        assertThat(performedBy).containsExactly("CLIENT");
    }

    @Test
    void leDemarrageCreeLesComptesDUneReferenceDejaPresente() {
        var customer =
                customerRefs.saveAndFlush(
                        new CustomerRef(
                                UUID.randomUUID(),
                                UUID.randomUUID(),
                                DEMO_EMAIL,
                                CustomerStatus.ACTIVE,
                                Instant.now()));

        seeder.seedOnStartup();

        assertThat(findAccounts(customer)).hasSize(2);
        assertThat(countTransactions(customer)).isEqualTo(5);
    }

    @Test
    void rienNEstCreeSiUnCompteActifExisteDeja() {
        var customer = createCustomerRef(UUID.randomUUID(), CustomerStatus.ACTIVE);
        customerRefs.saveAndFlush(
                new CustomerRef(
                        customer.getCustomerId(),
                        customer.getUserId(),
                        DEMO_EMAIL,
                        CustomerStatus.ACTIVE,
                        Instant.now()));
        accountService.openAccount(customer.getUserId(), Currency.EUR, "Mon compte");

        seeder.seedAccounts(customer.getCustomerId());
        seeder.seedAccounts(customer.getCustomerId());

        assertThat(findAccounts(customer)).hasSize(1);
        assertThat(countTransactions(customer)).isZero();
    }

    @Test
    void deuxDeclenchementsSuccessifsNeCreentQueDeuxComptes() {
        var customer = syncCustomer(DEMO_EMAIL, CustomerStatus.ACTIVE);

        seeder.seedAccounts(customer.getCustomerId());
        seeder.seedOnStartup();

        assertThat(findAccounts(customer)).hasSize(2);
        assertThat(countTransactions(customer)).isEqualTo(5);
    }

    @Test
    void lesComptesSontRecreesApresLaClotureDeTousLesComptes() {
        var customer = syncCustomer(DEMO_EMAIL, CustomerStatus.ACTIVE);
        var actor = Actor.createFrom(customer.getUserId(), false);
        for (var account : findAccounts(customer)) {
            var balance = account.getBalance().amount();
            operations.withdraw(actor, account.getId(), balance, "Solde à zéro");
            accountService.closeAccount(actor, account.getId());
        }
        assertThat(
                        accounts.countByCustomerIdAndStatus(
                                customer.getCustomerId(), AccountStatus.ACTIVE))
                .isZero();

        seeder.seedAccounts(customer.getCustomerId());

        var active =
                findAccounts(customer).stream()
                        .filter(account -> account.getStatus() == AccountStatus.ACTIVE)
                        .toList();
        assertThat(active).hasSize(2);
        assertThat(active)
                .extracting(account -> account.getBalance().amount())
                .allSatisfy(amount -> assertThat(amount).isGreaterThan(BigDecimal.ZERO));
        assertLedgerInvariants(
                jdbc, active.get(0).getId().toString(), active.get(1).getId().toString());
    }

    @Test
    void deuxDeclenchementsConcurrentsNeCreentQueDeuxComptes() throws Exception {
        var customer =
                customerRefs.saveAndFlush(
                        new CustomerRef(
                                UUID.randomUUID(),
                                UUID.randomUUID(),
                                DEMO_EMAIL,
                                CustomerStatus.ACTIVE,
                                Instant.now()));
        var start = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(2);
        try {
            var tasks =
                    List.of(
                            pool.submit(() -> {
                                start.await();
                                seeder.seedAccounts(customer.getCustomerId());
                                return null;
                            }),
                            pool.submit(() -> {
                                start.await();
                                seeder.seedAccounts(customer.getCustomerId());
                                return null;
                            }));
            start.countDown();
            for (var task : tasks) {
                task.get(60, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(findAccounts(customer)).hasSize(2);
        assertThat(countTransactions(customer)).isEqualTo(5);
    }

    @Test
    void uneMiseAJourDeLaReferenceNePublieAucunCustomerRefCreated() {
        var customer = syncCustomer("maj@bankflow.test", CustomerStatus.ACTIVE);
        assertThat(createdEvents(customer)).isEqualTo(1);

        synchronizer.syncCustomer(
                customer.getCustomerId(),
                customer.getUserId(),
                "maj@bankflow.test",
                CustomerStatus.SUSPENDED,
                Instant.now().plusSeconds(10));

        assertThat(createdEvents(customer)).isEqualTo(1);
    }

    private long createdEvents(CustomerRef customer) {
        return events.stream(CustomerRefCreated.class)
                .filter(event -> event.customerId().equals(customer.getCustomerId()))
                .count();
    }

    @Test
    void unAutreEmailNeDeclencheRien() {
        var customer = syncCustomer("autre.client@bankflow.test", CustomerStatus.ACTIVE);

        seeder.seedAccounts(customer.getCustomerId());
        seeder.seedOnStartup();

        assertThat(findAccounts(customer)).isEmpty();
    }

    @Test
    void unClientSuspenduNeRecoitRien() {
        var customer = syncCustomer(DEMO_EMAIL, CustomerStatus.SUSPENDED);

        seeder.seedAccounts(customer.getCustomerId());

        assertThat(findAccounts(customer)).isEmpty();
    }

    @Test
    void uneReferenceInconnueNeLevePasDException() {
        seeder.seedAccounts(UUID.randomUUID());
    }
}
