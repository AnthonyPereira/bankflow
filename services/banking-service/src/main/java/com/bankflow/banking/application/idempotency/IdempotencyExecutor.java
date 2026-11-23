package com.bankflow.banking.application.idempotency;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Exécute une opération une seule fois par clé d'idempotence et par personne. Tout se passe dans la
 * transaction de l'opération : la réservation de la clé bloque une requête concurrente portant la
 * même clé jusqu'au commit de la première, et une erreur métier annule aussi la réservation (la clé
 * peut alors être réessayée).
 */
@Service
public class IdempotencyExecutor {

    private final JdbcTemplate jdbc;
    private final Clock clock;

    IdempotencyExecutor(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    /**
     * @param key clé de l'en-tête déjà validée, ou {@code null} : l'opération s'exécute alors sans
     *     mémorisation
     * @param operation produit la réponse ; elle s'exécute dans cette transaction
     */
    @Transactional
    public IdempotencyResult execute(
            UUID actorUserId, String key, String requestHash, Supplier<StoredResponse> operation) {
        if (key == null) {
            return new IdempotencyResult(operation.get(), false);
        }
        if (!reserve(actorUserId, key, requestHash)) {
            return replay(actorUserId, key, requestHash);
        }
        var response = operation.get();
        jdbc.update(
                "UPDATE idempotency_keys SET response_status = ?, response_body = ? "
                        + "WHERE actor_user_id = ? AND idempotency_key = ?",
                response.status(),
                response.body(),
                actorUserId,
                key);
        return new IdempotencyResult(response, false);
    }

    private boolean reserve(UUID actorUserId, String key, String requestHash) {
        var inserted =
                jdbc.update(
                        "INSERT INTO idempotency_keys "
                                + "(actor_user_id, idempotency_key, request_hash, created_at) "
                                + "VALUES (?, ?, ?, ?) "
                                + "ON CONFLICT (actor_user_id, idempotency_key) DO NOTHING",
                        actorUserId,
                        key,
                        requestHash,
                        OffsetDateTime.now(clock));
        return inserted == 1;
    }

    private IdempotencyResult replay(UUID actorUserId, String key, String requestHash) {
        var rows =
                jdbc.query(
                        "SELECT request_hash, response_status, response_body FROM idempotency_keys "
                                + "WHERE actor_user_id = ? AND idempotency_key = ?",
                        (resultSet, index) ->
                                new StoredRow(
                                        resultSet.getString("request_hash"),
                                        resultSet.getObject("response_status", Integer.class),
                                        resultSet.getString("response_body")),
                        actorUserId,
                        key);
        var row = rows.getFirst();
        if (!row.requestHash().equals(requestHash) || row.status() == null) {
            throw new IdempotencyKeyReusedException();
        }
        return new IdempotencyResult(new StoredResponse(row.status(), row.body()), true);
    }

    private record StoredRow(String requestHash, Integer status, String body) {}
}
