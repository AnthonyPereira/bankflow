package com.bankflow.customer.application.customer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.bankflow.customer.application.customer.exception.CustomerNotFoundException;
import com.bankflow.customer.application.customer.model.ContactDetails;
import com.bankflow.customer.application.customer.model.CurrentUser;
import com.bankflow.customer.domain.customer.Customer;
import com.bankflow.customer.domain.customer.CustomerRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CustomerProfileServiceTests {

    private static final Instant NOW = Instant.parse("2026-11-23T10:00:00Z");

    private final CustomerRepository customers = mock(CustomerRepository.class);
    private final CustomerProfileService service =
            new CustomerProfileService(customers, Clock.fixed(NOW, ZoneOffset.UTC));

    private final UUID ownerUserId = UUID.randomUUID();
    private final Customer customer =
            Customer.createFromUser(ownerUserId, "owner@bankflow.test", false, NOW);

    @Test
    void leProprietaireVoitSonProfil() {
        when(customers.findById(customer.getId())).thenReturn(Optional.of(customer));

        assertThat(
                        service.getCustomer(
                                customer.getId(), new CurrentUser(ownerUserId, false, false)))
                .isSameAs(customer);
    }

    @Test
    void unAdministrateurVoitLeProfilDUnAutre() {
        when(customers.findById(customer.getId())).thenReturn(Optional.of(customer));

        assertThat(
                        service.getCustomer(
                                customer.getId(), new CurrentUser(UUID.randomUUID(), true, false)))
                .isSameAs(customer);
    }

    @Test
    void unNonProprietaireRecoitIntrouvable() {
        when(customers.findById(customer.getId())).thenReturn(Optional.of(customer));

        assertThatThrownBy(() ->
                        service.getCustomer(
                                customer.getId(), new CurrentUser(UUID.randomUUID(), false, false)))
                .isInstanceOf(CustomerNotFoundException.class);
    }

    @Test
    void unProfilInexistantRecoitIntrouvable() {
        var unknown = UUID.randomUUID();
        when(customers.findById(unknown)).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                        service.getCustomer(unknown, new CurrentUser(ownerUserId, true, false)))
                .isInstanceOf(CustomerNotFoundException.class);
    }

    @Test
    void laMiseAJourDesCoordonneesEfaceLesChampsVides() {
        when(customers.findByUserId(ownerUserId)).thenReturn(Optional.of(customer));

        var updated =
                service.updateOwnContact(ownerUserId, new ContactDetails("Alice", " ", null, ""));

        assertThat(updated.getFirstName()).isEqualTo("Alice");
        assertThat(updated.getLastName()).isNull();
    }
}
