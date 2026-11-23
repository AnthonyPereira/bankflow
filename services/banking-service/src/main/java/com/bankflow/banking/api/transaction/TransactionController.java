package com.bankflow.banking.api.transaction;

import com.bankflow.banking.api.page.PageResponse;
import com.bankflow.banking.application.transaction.CompensationService;
import com.bankflow.banking.application.transaction.TransactionQuery;
import com.bankflow.banking.application.transaction.TransactionService;
import com.bankflow.banking.application.transaction.TransactionView;
import com.bankflow.banking.domain.transaction.Actor;
import com.bankflow.banking.domain.transaction.TransactionType;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
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

    private static final String ADMIN_ROLE = "ROLE_ADMIN";

    private final TransactionService transactionService;
    private final CompensationService compensationService;
    private final Validator validator;

    TransactionController(
            TransactionService transactionService,
            CompensationService compensationService,
            Validator validator) {
        this.transactionService = transactionService;
        this.compensationService = compensationService;
        this.validator = validator;
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
                    int page,
            @RequestParam(defaultValue = "20")
                    @Min(value = 1, message = "La taille doit être comprise entre 1 et 100.")
                    @Max(value = 100, message = "La taille doit être comprise entre 1 et 100.")
                    int size) {
        var query = new TransactionQuery(accountId, type, from, to, customerId, page, size);
        var result = transactionService.listTransactions(createActor(jwt), query);
        return PageResponse.createFrom(result, TransactionController::createResponse);
    }

    @GetMapping("/{id}")
    TransactionResponse getTransaction(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return createResponse(transactionService.getTransaction(createActor(jwt), id));
    }

    /**
     * Le rôle est contrôlé avant la validation du corps : un client reçoit 403 même si le motif est
     * invalide (la validation du corps n'est donc pas déléguée à Spring).
     */
    @PostMapping("/{id}/compensations")
    ResponseEntity<TransactionResponse> createCompensation(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id,
            @RequestBody CompensationRequest request) {
        var actor = createActor(jwt);
        if (!actor.isAdmin()) {
            throw new AccessDeniedException("Réservé aux administrateurs");
        }
        var violations = validator.validate(request);
        if (!violations.isEmpty()) {
            throw new ConstraintViolationException(violations);
        }
        var compensation = compensationService.compensate(actor, id, request.reason());
        var location =
                ServletUriComponentsBuilder.fromCurrentContextPath()
                        .path("/transactions/{id}")
                        .buildAndExpand(compensation.getId())
                        .toUri();
        return ResponseEntity.created(location)
                .body(TransactionResponse.createFrom(compensation, null));
    }

    private static TransactionResponse createResponse(TransactionView view) {
        return TransactionResponse.createFrom(
                view.transaction(), view.compensatedByTransactionId());
    }

    private static Actor createActor(Jwt jwt) {
        return Actor.createFrom(
                UUID.fromString(jwt.getSubject()), ADMIN_ROLE.equals(jwt.getClaimAsString("role")));
    }
}
