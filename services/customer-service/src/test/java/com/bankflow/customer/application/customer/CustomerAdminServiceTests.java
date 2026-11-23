package com.bankflow.customer.application.customer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bankflow.customer.domain.customer.Customer;
import com.bankflow.customer.domain.customer.CustomerRepository;
import com.bankflow.customer.domain.customer.CustomerStatus;
import com.bankflow.customer.domain.customer.CustomerUpdated;
import com.bankflow.customer.domain.customer.DemoCustomerCannotBeSuspendedException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

class CustomerAdminServiceTests {

    private static final Instant NOW = Instant.parse("2026-11-23T10:00:00Z");

    private final CustomerRepository customers = mock(CustomerRepository.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final CustomerAdminService service =
            new CustomerAdminService(customers, events, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void laSuspensionPublieCustomerUpdated() {
        var customer = Customer.createFromUser(UUID.randomUUID(), "a@bankflow.test", false, NOW);
        when(customers.findById(customer.getId())).thenReturn(Optional.of(customer));

        service.changeStatus(customer.getId(), CustomerStatus.SUSPENDED, false);

        assertThat(customer.getStatus()).isEqualTo(CustomerStatus.SUSPENDED);
        verify(events).publishEvent(any(CustomerUpdated.class));
    }

    @Test
    void unStatutIdentiqueNePublieRien() {
        var customer = Customer.createFromUser(UUID.randomUUID(), "a@bankflow.test", false, NOW);
        when(customers.findById(customer.getId())).thenReturn(Optional.of(customer));

        service.changeStatus(customer.getId(), CustomerStatus.ACTIVE, false);

        verify(events, never()).publishEvent(any(Object.class));
        verify(customers, never()).save(any(Customer.class));
    }

    @Test
    void suspendreUnClientDeDemonstrationEstRefuseSansEvenement() {
        var customer = Customer.createFromUser(UUID.randomUUID(), "d@bankflow.test", true, NOW);
        when(customers.findById(customer.getId())).thenReturn(Optional.of(customer));

        assertThatThrownBy(() ->
                        service.changeStatus(customer.getId(), CustomerStatus.SUSPENDED, false))
                .isInstanceOf(DemoCustomerCannotBeSuspendedException.class);
        verify(events, never()).publishEvent(any(Object.class));
    }

    @Test
    void laReactivationPublieCustomerUpdated() {
        var customer = Customer.createFromUser(UUID.randomUUID(), "a@bankflow.test", false, NOW);
        customer.suspend(NOW);
        when(customers.findById(customer.getId())).thenReturn(Optional.of(customer));

        service.changeStatus(customer.getId(), CustomerStatus.ACTIVE, false);

        verify(events, times(1)).publishEvent(any(CustomerUpdated.class));
    }

    @Test
    void unClientInexistantRecoitIntrouvable() {
        var unknown = UUID.randomUUID();
        when(customers.findById(unknown)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.changeStatus(unknown, CustomerStatus.SUSPENDED, false))
                .isInstanceOf(CustomerNotFoundException.class);
    }
}
