package com.bankflow.banking.api.account;

import com.bankflow.banking.api.page.PageResponse;
import com.bankflow.banking.api.security.JwtActors;
import com.bankflow.banking.application.account.AccountService;
import com.bankflow.banking.domain.account.Account;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.Clock;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/accounts")
class AccountController {

    private final AccountService accountService;
    private final Clock clock;

    AccountController(AccountService accountService, Clock clock) {
        this.accountService = accountService;
        this.clock = clock;
    }

    @PostMapping
    ResponseEntity<AccountResponse> createAccount(
            @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreateAccountRequest request) {
        var account = openAccount(jwt, request);
        var location =
                ServletUriComponentsBuilder.fromCurrentContextPath()
                        .path("/accounts/{id}")
                        .buildAndExpand(account.getId())
                        .toUri();
        return ResponseEntity.created(location).body(AccountResponse.createFrom(account));
    }

    private Account openAccount(Jwt jwt, CreateAccountRequest request) {
        if (!isAdmin(jwt)) {
            if (request.customerId() != null) {
                throw new FieldValidationException(
                        "customerId", "Ce champ est réservé aux conseillers.");
            }
            return accountService.openAccount(readUserId(jwt), request.currency(), request.label());
        }
        if (request.customerId() == null) {
            throw new FieldValidationException("customerId", "Choisissez le client.");
        }
        return accountService.openAccountForCustomer(
                request.customerId(), request.currency(), request.label());
    }

    @GetMapping
    PageResponse<AccountResponse> listAccounts(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) UUID customerId,
            @RequestParam(defaultValue = "0")
                    @Min(value = 0, message = "La page doit être positive.")
                    int page,
            @RequestParam(defaultValue = "20")
                    @Min(value = 1, message = "La taille doit être comprise entre 1 et 100.")
                    @Max(value = 100, message = "La taille doit être comprise entre 1 et 100.")
                    int size) {
        // Le filtre est réservé aux administrateurs : un client ne voit que ses comptes
        var accounts =
                accountService.listAccounts(readUserId(jwt), isAdmin(jwt), customerId, page, size);
        return PageResponse.createFrom(accounts, AccountResponse::createFrom);
    }

    /** Chemin littéral : prioritaire sur {@code /{id}}, il n'est jamais lu comme un identifiant. */
    @GetMapping("/summary")
    AccountSummaryResponse getSummary() {
        return AccountSummaryResponse.createFrom(accountService.getSummary());
    }

    @GetMapping("/{id}")
    AccountResponse getAccount(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return AccountResponse.createFrom(
                accountService.getAccount(readUserId(jwt), isAdmin(jwt), id));
    }

    @GetMapping("/{id}/balance")
    BalanceResponse getBalance(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        var account = accountService.getAccount(readUserId(jwt), isAdmin(jwt), id);
        return BalanceResponse.createFrom(account, clock.instant());
    }

    @PostMapping("/{id}/close")
    AccountResponse closeAccount(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return AccountResponse.createFrom(
                accountService.closeAccount(readUserId(jwt), isAdmin(jwt), id));
    }

    private static UUID readUserId(Jwt jwt) {
        return JwtActors.createFrom(jwt).userId();
    }

    private static boolean isAdmin(Jwt jwt) {
        return JwtActors.createFrom(jwt).isAdmin();
    }
}
