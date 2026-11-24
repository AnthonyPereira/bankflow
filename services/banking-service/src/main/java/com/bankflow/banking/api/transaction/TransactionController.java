package com.bankflow.banking.api.transaction;

import com.bankflow.banking.api.page.PageResponse;
import com.bankflow.banking.api.security.JwtActors;
import com.bankflow.banking.api.transaction.dto.CompensationRequest;
import com.bankflow.banking.api.transaction.dto.TransactionResponse;
import com.bankflow.banking.application.transaction.CompensationService;
import com.bankflow.banking.application.transaction.TransactionService;
import com.bankflow.banking.application.transaction.model.TransactionQuery;
import com.bankflow.banking.domain.transaction.TransactionType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
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
@RequestMapping("/transactions")
class TransactionController {

    private final TransactionService transactionService;
    private final CompensationService compensationService;

    TransactionController(
            TransactionService transactionService, CompensationService compensationService) {
        this.transactionService = transactionService;
        this.compensationService = compensationService;
    }

    @GetMapping
    PageResponse<TransactionResponse> listTransactions(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) UUID accountId,
            @RequestParam(required = false) TransactionType type,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate to,
            @RequestParam(required = false) UUID customerId,
            @RequestParam(defaultValue = "0")
                    @Min(value = 0, message = "La page doit être positive.")
                    @Max(value = 10000, message = "La page doit être inférieure à 10 000.")
                    int page,
            @RequestParam(defaultValue = "20")
                    @Min(value = 1, message = "La taille doit être comprise entre 1 et 100.")
                    @Max(value = 100, message = "La taille doit être comprise entre 1 et 100.")
                    int size) {
        var query = new TransactionQuery(accountId, type, from, to, customerId, page, size);
        var result = transactionService.listTransactions(JwtActors.createFrom(jwt), query);
        return PageResponse.createFrom(result, TransactionResponse::createFrom);
    }

    @GetMapping("/{id}")
    TransactionResponse getTransaction(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return TransactionResponse.createFrom(
                transactionService.getTransaction(JwtActors.createFrom(jwt), id));
    }

    /**
     * Réservé aux administrateurs : la règle est dans SecurityConfig, avant la lecture du corps.
     */
    @PostMapping("/{id}/compensations")
    ResponseEntity<TransactionResponse> createCompensation(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id,
            @Valid @RequestBody CompensationRequest request) {
        var compensation =
                compensationService.compensate(JwtActors.createFrom(jwt), id, request.reason());
        var location =
                ServletUriComponentsBuilder.fromCurrentContextPath()
                        .path("/transactions/{id}")
                        .buildAndExpand(compensation.transaction().getId())
                        .toUri();
        return ResponseEntity.created(location).body(TransactionResponse.createFrom(compensation));
    }
}
