package com.bankflow.banking.api.operation;

import com.bankflow.banking.api.security.JwtActors;
import com.bankflow.banking.api.transaction.TransactionResponse;
import com.bankflow.banking.application.idempotency.IdempotencyExecutor;
import com.bankflow.banking.application.idempotency.IdempotencyKeys;
import com.bankflow.banking.application.idempotency.IdempotencyResult;
import com.bankflow.banking.application.idempotency.RequestFingerprint;
import com.bankflow.banking.application.idempotency.StoredResponse;
import com.bankflow.banking.application.operation.OperationService;
import com.bankflow.banking.application.transaction.TransactionView;
import com.bankflow.banking.domain.transaction.TransactionType;
import jakarta.validation.Valid;
import java.util.HashMap;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import tools.jackson.databind.json.JsonMapper;

/** Dépôts et retraits sur un compte, au nom du titulaire ou d'un conseiller. */
@RestController
@RequestMapping("/accounts/{id}")
class OperationController {

    private static final String REPLAYED_HEADER = "Idempotent-Replayed";

    private final OperationService operationService;
    private final IdempotencyExecutor idempotency;
    private final JsonMapper json;

    OperationController(
            OperationService operationService, IdempotencyExecutor idempotency, JsonMapper json) {
        this.operationService = operationService;
        this.idempotency = idempotency;
        this.json = json;
    }

    @PostMapping("/deposits")
    ResponseEntity<String> createDeposit(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id,
            @RequestHeader(value = IdempotencyKeys.HEADER, required = false) String key,
            @Valid @RequestBody OperationRequest request) {
        return performOperation(jwt, id, key, request, TransactionType.DEPOSIT);
    }

    @PostMapping("/withdrawals")
    ResponseEntity<String> createWithdrawal(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id,
            @RequestHeader(value = IdempotencyKeys.HEADER, required = false) String key,
            @Valid @RequestBody OperationRequest request) {
        return performOperation(jwt, id, key, request, TransactionType.WITHDRAWAL);
    }

    private ResponseEntity<String> performOperation(
            Jwt jwt, UUID accountId, String key, OperationRequest request, TransactionType type) {
        IdempotencyKeys.validate(key, false);
        var actor = JwtActors.createFrom(jwt);
        var amount = request.readAmount();
        var body = new HashMap<String, Object>();
        body.put("amount", amount.toPlainString());
        body.put("description", request.description());
        var path =
                "/accounts/"
                        + accountId
                        + (type == TransactionType.DEPOSIT ? "/deposits" : "/withdrawals");
        var hash = RequestFingerprint.createHash("POST", path, body);

        var result =
                idempotency.execute(
                        actor.userId(),
                        key,
                        hash,
                        () -> {
                            var transaction =
                                    type == TransactionType.DEPOSIT
                                            ? operationService.deposit(
                                                    actor, accountId, amount, request.description())
                                            : operationService.withdraw(
                                                    actor,
                                                    accountId,
                                                    amount,
                                                    request.description());
                            var response =
                                    TransactionResponse.createFrom(
                                            TransactionView.createFor(transaction, null, null));
                            return new StoredResponse(
                                    HttpStatus.CREATED.value(), json.writeValueAsString(response));
                        });
        return createResponse(result);
    }

    private ResponseEntity<String> createResponse(IdempotencyResult result) {
        var stored = result.response();
        var builder =
                ResponseEntity.status(stored.status()).contentType(MediaType.APPLICATION_JSON);
        if (stored.status() == HttpStatus.CREATED.value()) {
            var transactionId = json.readTree(stored.body()).get("id").asString();
            builder.location(
                    ServletUriComponentsBuilder.fromCurrentContextPath()
                            .path("/transactions/{id}")
                            .buildAndExpand(transactionId)
                            .toUri());
        }
        if (result.replayed()) {
            builder.header(REPLAYED_HEADER, "true");
        }
        return builder.body(stored.body());
    }
}
