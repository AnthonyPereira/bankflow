package com.bankflow.banking.application.idempotency;

import static org.assertj.core.api.Assertions.assertThat;

import com.bankflow.banking.BankingApiTestSupport;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/** La purge supprime les clés plus vieilles que la rétention (24 h par défaut). */
class IdempotencyKeyPurgerIntegrationTests extends BankingApiTestSupport {

    @Autowired private IdempotencyKeyPurger purger;
    @Autowired private JdbcTemplate jdbc;

    @BeforeEach
    void clean() {
        jdbc.update("DELETE FROM idempotency_keys");
    }

    @Test
    void laPurgeSupprimeLesClesPlusVieillesQueLaRetention() {
        insertKey("vieille", OffsetDateTime.now().minusHours(25), 201);
        insertKey("recente", OffsetDateTime.now().minusHours(23), 201);
        insertKey("sans-reponse", OffsetDateTime.now().minusHours(25), null);

        int deleted = purger.purgeExpired();

        assertThat(deleted).isEqualTo(2);
        List<String> remaining =
                jdbc.queryForList("SELECT idempotency_key FROM idempotency_keys", String.class);
        assertThat(remaining).containsExactly("recente");
    }

    @Test
    void laPurgeNeSupprimeRienQuandAucuneCleNEstExpiree() {
        insertKey("recente", OffsetDateTime.now().minusHours(1), 201);

        assertThat(purger.purgeExpired()).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM idempotency_keys", Integer.class))
                .isEqualTo(1);
    }

    private void insertKey(String key, OffsetDateTime createdAt, Integer status) {
        jdbc.update(
                "INSERT INTO idempotency_keys (actor_user_id, idempotency_key, request_hash,"
                        + " response_status, created_at) VALUES (?, ?, ?, ?, ?)",
                UUID.randomUUID(),
                key,
                "hash",
                status,
                createdAt);
    }
}
