package com.bankflow.customer.application.customer;

import static org.assertj.core.api.Assertions.assertThat;

import com.bankflow.customer.CustomerApiTestSupport;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Profil fictif des comptes de démonstration, rempli à la création du client. */
class DemoProfilesIntegrationTests extends CustomerApiTestSupport {

    private static final String CLIENT_EMAIL = "demo.client@bankflow.local";
    private static final String ADMIN_EMAIL = "demo.admin@bankflow.local";

    @Test
    void leClientDeDemonstrationRecoitSonProfilFictif() {
        var customer = createCustomer(CLIENT_EMAIL, true);

        assertThat(customer.getFirstName()).isEqualTo("Camille");
        assertThat(customer.getLastName()).isEqualTo("Martin");
        assertThat(customer.getPhone()).isEqualTo("+33 6 00 00 00 00");
        assertThat(customer.getAddress()).isEqualTo("1 rue de la Démo, 75000 Paris");
    }

    @Test
    void lAdministrateurDeDemonstrationNAPlusDeProfilFictif() {
        var customer = createCustomer(ADMIN_EMAIL, true);

        assertThat(customer.hasEmptyProfile()).isTrue();
    }

    @Test
    void unEmailInconnuNeDeclencheRien() {
        var customer = createCustomer("demo.autre@bankflow.local", true);

        assertThat(customer.hasEmptyProfile()).isTrue();
    }

    @Test
    void unClientNonDemonstrationGardeUnProfilVide() {
        var customer = createCustomer(CLIENT_EMAIL, false);

        assertThat(customer.hasEmptyProfile()).isTrue();
    }

    @Test
    void passerUnClientExistantEnDemonstrationRemplitSonProfilVide() {
        var customer = createCustomer(CLIENT_EMAIL, false);

        registrationService.registerUser(customer.getUserId(), CLIENT_EMAIL, true);

        var stored = customerRepository.findById(customer.getId()).orElseThrow();
        assertThat(stored.getFirstName()).isEqualTo("Camille");
    }

    @Test
    void unProfilDejaRempliNEstJamaisRemplace() {
        var userId = UUID.randomUUID();
        registrationService.registerUser(userId, CLIENT_EMAIL, true);
        var customer = customerRepository.findByUserId(userId).orElseThrow();
        customer.updateContact("Léa", null, null, null, Instant.now());
        customerRepository.saveAndFlush(customer);

        registrationService.registerUser(userId, CLIENT_EMAIL, true);

        var stored = customerRepository.findById(customer.getId()).orElseThrow();
        assertThat(stored.getFirstName()).isEqualTo("Léa");
        assertThat(stored.getLastName()).isNull();
    }
}
