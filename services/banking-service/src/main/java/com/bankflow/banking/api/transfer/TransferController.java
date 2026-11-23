package com.bankflow.banking.api.transfer;

import com.bankflow.banking.api.operation.IdempotentResponses;
import com.bankflow.banking.api.security.JwtActors;
import com.bankflow.banking.application.idempotency.IdempotencyExecutor;
import com.bankflow.banking.application.idempotency.IdempotencyKeys;
import com.bankflow.banking.application.idempotency.RequestFingerprint;
import com.bankflow.banking.application.idempotency.StoredResponse;
import com.bankflow.banking.application.transfer.TransferService;
import jakarta.validation.Valid;
import java.util.HashMap;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.json.JsonMapper;

/** Virements entre comptes, au nom du titulaire ou d'un conseiller. */
@RestController
@RequestMapping("/transfers")
class TransferController {

    private final TransferService transferService;
    private final IdempotencyExecutor idempotency;
    private final JsonMapper json;

    TransferController(
            TransferService transferService, IdempotencyExecutor idempotency, JsonMapper json) {
        this.transferService = transferService;
        this.idempotency = idempotency;
        this.json = json;
    }

    @PostMapping
    ResponseEntity<String> createTransfer(
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader(value = IdempotencyKeys.HEADER, required = false) String key,
            @Valid @RequestBody TransferRequest request) {
        IdempotencyKeys.validate(key, true);
        var actor = JwtActors.createFrom(jwt);
        var amount = request.readAmount();
        var body = new HashMap<String, Object>();
        body.put("sourceAccountId", request.sourceAccountId().toString());
        body.put("destinationIban", request.destinationIban());
        body.put("amount", amount.toPlainString());
        body.put("description", request.description());
        var hash = RequestFingerprint.createHash("POST", "/transfers", body);

        var result =
                idempotency.execute(
                        actor.userId(),
                        key,
                        hash,
                        () -> {
                            var view =
                                    transferService.createTransfer(
                                            actor,
                                            request.sourceAccountId(),
                                            request.destinationIban(),
                                            amount,
                                            request.description());
                            return new StoredResponse(
                                    HttpStatus.CREATED.value(),
                                    json.writeValueAsString(TransferResponse.createFrom(view)));
                        });
        return IdempotentResponses.createResponse(result, json, "/transfers/{id}");
    }

    @GetMapping("/{id}")
    TransferResponse getTransfer(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return TransferResponse.createFrom(
                transferService.getTransfer(JwtActors.createFrom(jwt), id));
    }
}
