package com.bankflow.auth.api.demo;

import com.bankflow.auth.application.demo.DemoAccounts;
import com.bankflow.auth.domain.user.Role;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Expose les comptes de démonstration, affichés sur la page de connexion. */
@RestController
class DemoAccountsController {

    private final DemoAccounts demoAccounts;

    DemoAccountsController(DemoAccounts demoAccounts) {
        this.demoAccounts = demoAccounts;
    }

    @GetMapping("/auth/demo-accounts")
    List<DemoAccountResponse> list() {
        return demoAccounts.list().stream()
                .map(account ->
                        new DemoAccountResponse(
                                account.email(), account.password(), account.role()))
                .toList();
    }

    record DemoAccountResponse(String email, String password, Role role) {}
}
