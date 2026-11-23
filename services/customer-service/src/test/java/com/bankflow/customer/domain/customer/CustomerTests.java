package com.bankflow.customer.domain.customer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CustomerTests {

    private static final Instant T0 = Instant.parse("2026-11-23T10:00:00Z");
    private static final Instant T1 = Instant.parse("2026-11-23T11:00:00Z");

    @Test
    void laCreationDonneUnClientActifEtNormaliseLEmail() {
        var customer =
                Customer.createFromUser(UUID.randomUUID(), "  Alice@BankFlow.TEST ", false, T0);

        assertThat(customer.getEmail()).isEqualTo("alice@bankflow.test");
        assertThat(customer.getStatus()).isEqualTo(CustomerStatus.ACTIVE);
        assertThat(customer.isDemo()).isFalse();
        assertThat(customer.getCreatedAt()).isEqualTo(T0);
        assertThat(customer.getUpdatedAt()).isEqualTo(T0);
        assertThat(customer.getFirstName()).isNull();
    }

    @Test
    void updateContactTransformeLesChainesVidesEnNull() {
        var customer = Customer.createFromUser(UUID.randomUUID(), "a@bankflow.test", false, T0);

        customer.updateContact("  Alice ", "   ", "", null, T1);

        assertThat(customer.getFirstName()).isEqualTo("Alice");
        assertThat(customer.getLastName()).isNull();
        assertThat(customer.getPhone()).isNull();
        assertThat(customer.getAddress()).isNull();
        assertThat(customer.getUpdatedAt()).isEqualTo(T1);
    }

    @Test
    void unClientDeDemonstrationNePeutPasEtreSuspendu() {
        var customer = Customer.createFromUser(UUID.randomUUID(), "d@bankflow.test", true, T0);

        assertThatThrownBy(() -> customer.suspend(T1))
                .isInstanceOf(DemoCustomerCannotBeSuspendedException.class);
        assertThat(customer.getStatus()).isEqualTo(CustomerStatus.ACTIVE);
    }

    @Test
    void unClientOrdinairePeutEtreSuspenduPuisReactive() {
        var customer = Customer.createFromUser(UUID.randomUUID(), "a@bankflow.test", false, T0);

        customer.suspend(T1);
        assertThat(customer.getStatus()).isEqualTo(CustomerStatus.SUSPENDED);
        assertThat(customer.getUpdatedAt()).isEqualTo(T1);

        customer.activate(T1.plusSeconds(60));
        assertThat(customer.getStatus()).isEqualTo(CustomerStatus.ACTIVE);
    }

    @Test
    void suspendreUnClientDejaSuspenduNeChangeRien() {
        var customer = Customer.createFromUser(UUID.randomUUID(), "a@bankflow.test", false, T0);
        customer.suspend(T1);

        customer.suspend(T1.plusSeconds(60));

        assertThat(customer.getUpdatedAt()).isEqualTo(T1);
    }

    @Test
    void passerEnDemonstrationReactiveLeClientSuspendu() {
        var customer = Customer.createFromUser(UUID.randomUUID(), "a@bankflow.test", false, T0);
        customer.suspend(T1);

        customer.markAsDemo(T1.plusSeconds(60));

        assertThat(customer.isDemo()).isTrue();
        assertThat(customer.getStatus()).isEqualTo(CustomerStatus.ACTIVE);
    }
}
