package com.bankflow.banking.api.operation;

import com.bankflow.banking.application.idempotency.model.IdempotencyResult;
import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import tools.jackson.databind.json.JsonMapper;

/** Construit la réponse d'une opération idempotente, rejouée ou non, pour tous les contrôleurs. */
public final class IdempotentResponses {

    private static final String REPLAYED_HEADER = "Idempotent-Replayed";

    private IdempotentResponses() {}

    /**
     * Rend la réponse enregistrée. Un 201 porte {@code Location} : le chemin {@code resourcePath}
     * (par exemple {@code /transfers/{id}}) rempli avec l'{@code id} du corps.
     */
    public static ResponseEntity<String> createResponse(
            IdempotencyResult result, JsonMapper json, String resourcePath) {
        var stored = result.response();
        var builder =
                ResponseEntity.status(stored.status()).contentType(MediaType.APPLICATION_JSON);
        if (stored.status() == HttpStatus.CREATED.value()) {
            var id = json.readTree(stored.body()).get("id").asString();
            builder.location(createLocation(resourcePath, id));
        }
        if (result.replayed()) {
            builder.header(REPLAYED_HEADER, "true");
        }
        return builder.body(stored.body());
    }

    private static URI createLocation(String resourcePath, String id) {
        return ServletUriComponentsBuilder.fromCurrentContextPath()
                .path(resourcePath)
                .buildAndExpand(id)
                .toUri();
    }
}
