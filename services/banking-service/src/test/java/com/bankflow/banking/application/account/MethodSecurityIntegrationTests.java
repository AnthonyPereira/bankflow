package com.bankflow.banking.application.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bankflow.banking.BankingApiTestSupport;
import com.bankflow.banking.application.transaction.CompensationService;
import com.bankflow.banking.domain.transaction.Actor;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;

/** Défense en profondeur : les règles du service tiennent même sans la règle d'URL. */
class MethodSecurityIntegrationTests extends BankingApiTestSupport {

    @Autowired private AccountService accountService;
    @Autowired private CompensationService compensationService;

    @Test
    @WithMockUser(authorities = "ROLE_CLIENT")
    void laVueDensembleEstRefuseeAUnClient() {
        assertThatThrownBy(() -> accountService.getSummary())
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @WithMockUser(authorities = "ROLE_ADMIN")
    void laVueDensembleEstPermiseAUnAdmin() {
        assertThat(accountService.getSummary()).isNotNull();
    }

    @Test
    @WithMockUser(authorities = "ROLE_CLIENT")
    void uneCompensationEstRefuseeAUnClient() {
        var actor = Actor.createFrom(UUID.randomUUID(), false);

        assertThatThrownBy(() ->
                        compensationService.compensate(
                                actor, UUID.randomUUID(), "Erreur de saisie"))
                .isInstanceOf(AccessDeniedException.class);
    }
}
