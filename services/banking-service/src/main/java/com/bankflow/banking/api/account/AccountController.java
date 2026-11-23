package com.bankflow.banking.api.account;

import com.bankflow.banking.api.page.PageResponse;
import com.bankflow.banking.application.account.AccountService;
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

    private static final String ADMIN_ROLE = "ROLE_ADMIN";

    private final AccountService accountService;
    private final Clock clock;

    AccountController(AccountService accountService, Clock clock) {
        this.accountService = accountService;
        this.clock = clock;
    }

    @PostMapping
    ResponseEntity<AccountResponse> createAccount(
            @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreateAccountRequest request) {
        var account =
                accountService.openAccount(readUserId(jwt), request.currency(), request.label());
        var location =
                ServletUriComponentsBuilder.fromCurrentRequestUri()
                        .path("/{id}")
                        .buildAndExpand(account.getId())
                        .toUri();
        return ResponseEntity.created(location).body(AccountResponse.from(account));
    }

    @GetMapping
    PageResponse<AccountResponse> listAccounts(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "0")
                    @Min(value = 0, message = "La page doit être positive.")
                    int page,
            @RequestParam(defaultValue = "20")
                    @Min(value = 1, message = "La taille doit être comprise entre 1 et 100.")
                    @Max(value = 100, message = "La taille doit être comprise entre 1 et 100.")
                    int size) {
        var accounts = accountService.listAccounts(readUserId(jwt), isAdmin(jwt), page, size);
        return PageResponse.from(accounts, AccountResponse::from);
    }

    @GetMapping("/{id}")
    AccountResponse getAccount(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return AccountResponse.from(accountService.getAccount(readUserId(jwt), isAdmin(jwt), id));
    }

    @GetMapping("/{id}/balance")
    BalanceResponse getBalance(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        var account = accountService.getAccount(readUserId(jwt), isAdmin(jwt), id);
        return BalanceResponse.from(account, clock.instant());
    }

    @PostMapping("/{id}/close")
    AccountResponse closeAccount(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return AccountResponse.from(accountService.closeAccount(readUserId(jwt), isAdmin(jwt), id));
    }

    private static UUID readUserId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }

    private static boolean isAdmin(Jwt jwt) {
        return ADMIN_ROLE.equals(jwt.getClaimAsString("role"));
    }
}
