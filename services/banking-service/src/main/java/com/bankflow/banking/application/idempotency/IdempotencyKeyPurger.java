package com.bankflow.banking.application.idempotency;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Supprime les clés d'idempotence plus anciennes que la durée de rétention.
 *
 * <p>Au-delà de la rétention, une clé rejouée n'est plus reconnue : l'opération est exécutée de
 * nouveau. Une opération en cours n'est pas concernée, car sa ligne n'est visible qu'une fois la
 * transaction validée.
 */
@Service
public class IdempotencyKeyPurger {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyKeyPurger.class);

    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final Duration retention;

    IdempotencyKeyPurger(
            JdbcTemplate jdbc,
            Clock clock,
            @Value("${bankflow.idempotency.retention}") Duration retention) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.retention = retention;
    }

    /** Supprime les clés expirées et renvoie leur nombre. */
    @Scheduled(cron = "${bankflow.idempotency.purge-cron}")
    @Transactional
    public int purgeExpired() {
        var limit = OffsetDateTime.now(clock).minus(retention);
        int deleted = jdbc.update("DELETE FROM idempotency_keys WHERE created_at < ?", limit);
        if (deleted > 0) {
            log.info("{} clé(s) d'idempotence expirée(s) supprimée(s)", deleted);
        }
        return deleted;
    }
}
