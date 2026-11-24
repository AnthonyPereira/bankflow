package com.bankflow.auth.application.demo;

import com.bankflow.auth.application.demo.model.DemoAccount;
import com.bankflow.auth.domain.user.Role;
import com.bankflow.auth.domain.user.User;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Comptes de démonstration, pour que les visiteurs découvrent l'application sans s'inscrire. Ils
 * sont distincts du compte administrateur initial, dont le mot de passe reste secret.
 *
 * <p>Ils suivent exactement les mêmes règles que les autres comptes, verrouillage temporaire après
 * trop d'échecs compris, sauf qu'ils ne peuvent jamais être désactivés définitivement.
 *
 * <p>Avec {@code DEMO_ACCOUNTS_ENABLED=false}, la liste n'est plus servie et les comptes absents ne
 * sont plus créés. Les comptes déjà présents en base ne sont ni supprimés ni désactivés : ils
 * restent utilisables par qui connaît leur mot de passe.
 */
@Component
public class DemoAccounts {

    private static final List<DemoAccount> ACCOUNTS =
            List.of(
                    new DemoAccount(
                            "demo.client@bankflow.local", "demo-client-bankflow", Role.ROLE_CLIENT),
                    new DemoAccount(
                            "demo.admin@bankflow.local", "demo-admin-bankflow", Role.ROLE_ADMIN));

    private final boolean enabled;

    public DemoAccounts(@Value("${bankflow.demo.enabled}") boolean enabled) {
        this.enabled = enabled;
    }

    /** Liste vide quand l'affichage et la création des comptes de démonstration sont coupés. */
    public List<DemoAccount> list() {
        return enabled ? ACCOUNTS : List.of();
    }

    /**
     * Les adresses des comptes de démonstration restent réservées même quand la démonstration est
     * coupée : personne ne peut s'en emparer en s'inscrivant avant le seeder.
     */
    public boolean isDemoEmail(String email) {
        var normalized = User.normalizeEmail(email);
        return ACCOUNTS.stream().anyMatch(account -> account.email().equals(normalized));
    }
}
