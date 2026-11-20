package com.bankflow.auth.application.demo;

import com.bankflow.auth.domain.user.Role;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Comptes de démonstration, pour que les visiteurs découvrent l'application sans s'inscrire. Ils
 * sont distincts du compte administrateur initial, dont le mot de passe reste secret. On les coupe
 * avec {@code DEMO_ACCOUNTS_ENABLED=false}.
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

    /** Liste vide quand les comptes de démonstration sont désactivés. */
    public List<DemoAccount> list() {
        return enabled ? ACCOUNTS : List.of();
    }
}
