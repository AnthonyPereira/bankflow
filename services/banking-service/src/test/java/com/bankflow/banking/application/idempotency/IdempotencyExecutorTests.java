package com.bankflow.banking.application.idempotency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.bankflow.banking.application.idempotency.exception.IdempotencyKeyReusedException;
import com.bankflow.banking.application.idempotency.model.StoredResponse;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.mockito.stubbing.Answer;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

/** Cas où la purge supprime la clé entre la réservation refusée et la lecture de la réponse. */
class IdempotencyExecutorTests {

    private static final String KEY = "cle-1";
    private static final String HASH = "empreinte";

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final IdempotencyExecutor executor =
            new IdempotencyExecutor(
                    jdbc, Clock.fixed(Instant.parse("2026-11-24T10:00:00Z"), ZoneOffset.UTC));
    private final UUID actor = UUID.randomUUID();
    private final AtomicInteger executions = new AtomicInteger();

    @Test
    void uneCleePurgeeEntreTempsEstReserveeDeNouveauEtLOperationSExecute() {
        when(jdbc.update(startsWith("INSERT"), any(Object[].class))).thenReturn(0, 1);
        when(jdbc.query(startsWith("SELECT"), any(RowMapper.class), any(Object[].class)))
                .thenReturn(List.of());

        var result = executor.execute(actor, KEY, HASH, this::runOperation);

        assertThat(result.replayed()).isFalse();
        assertThat(result.response().status()).isEqualTo(201);
        assertThat(executions).hasValue(1);
    }

    @Test
    void uneCleeRepriseParUneRequeteConcurrenteEstRejouee() {
        when(jdbc.update(startsWith("INSERT"), any(Object[].class))).thenReturn(0, 0);
        when(jdbc.query(startsWith("SELECT"), any(RowMapper.class), any(Object[].class)))
                .thenReturn(List.of())
                .thenAnswer(mapStoredRow());

        var result = executor.execute(actor, KEY, HASH, this::runOperation);

        assertThat(result.replayed()).isTrue();
        assertThat(result.response().body()).isEqualTo("{}");
        assertThat(executions).hasValue(0);
    }

    @Test
    void uneCleeIntrouvableApresDeuxReservationsRefuseesEstRejetee() {
        when(jdbc.update(startsWith("INSERT"), any(Object[].class))).thenReturn(0, 0);
        when(jdbc.query(startsWith("SELECT"), any(RowMapper.class), any(Object[].class)))
                .thenReturn(List.of());

        assertThatThrownBy(() -> executor.execute(actor, KEY, HASH, this::runOperation))
                .isInstanceOf(IdempotencyKeyReusedException.class);
        assertThat(executions).hasValue(0);
    }

    private StoredResponse runOperation() {
        executions.incrementAndGet();
        return new StoredResponse(201, "{\"id\":1}");
    }

    /** Fait passer une ligne enregistrée par le RowMapper réel de l'exécuteur. */
    private static Answer<List<?>> mapStoredRow() {
        return invocation -> {
            RowMapper<?> mapper = invocation.getArgument(1);
            var row = mock(ResultSet.class);
            when(row.getString("request_hash")).thenReturn(HASH);
            when(row.getObject("response_status", Integer.class)).thenReturn(201);
            when(row.getString("response_body")).thenReturn("{}");
            return List.of(mapRow(mapper, row));
        };
    }

    private static Object mapRow(RowMapper<?> mapper, ResultSet row) throws SQLException {
        return mapper.mapRow(row, 0);
    }
}
