package com.bankflow.auth.infrastructure.kafka;

import com.bankflow.auth.domain.user.UserRepository;
import com.bankflow.auth.domain.user.event.UserRegistered;
import java.time.Duration;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Republie {@code UserRegistered} pour tous les utilisateurs à chaque démarrage.
 *
 * <p>Ce rejeu est un rattrapage : il couvre les comptes créés sans événement (comptes seedés,
 * comptes antérieurs à la MVP 2, événements perdus lors d'une panne de Kafka). Les consommateurs
 * sont idempotents sur la clé métier ({@code userId}) : recevoir un événement déjà traité ne change
 * rien. Tant que l'Outbox de BF-011 n'existe pas, c'est ce rejeu qui garantit la livraison.
 *
 * <p>Il démarre sur {@link ApplicationReadyEvent}, donc après les seeders (leurs comptes sont
 * inclus), et dans un thread virtuel dédié : un Kafka lent ou absent ne retarde ni la disponibilité
 * de l'application ni son healthcheck. Un envoi en échec est retenté avec une attente croissante
 * (environ deux minutes au total) : au démarrage, la première lecture des métadonnées de Kafka peut
 * dépasser {@code max.block.ms} sur une machine lente. Si Kafka reste indisponible, le rejeu
 * s'arrête avec un seul avertissement ; le prochain démarrage recommencera. Il se désactive avec
 * {@code bankflow.events.replay-on-startup=false}.
 */
@Component
class UserRegisteredReplayer {

    static final int DEFAULT_PAGE_SIZE = 100;

    /** Attentes entre deux essais d'un même envoi : environ deux minutes au total. */
    static final List<Duration> DEFAULT_RETRY_DELAYS =
            List.of(1, 2, 4, 8, 15, 30, 30, 30).stream().map(Duration::ofSeconds).toList();

    private static final Logger log = LoggerFactory.getLogger(UserRegisteredReplayer.class);
    private static final Sort ORDER_BY_CREATION = Sort.by("createdAt", "id");

    private final UserRepository users;
    private final UserRegisteredPublisher publisher;
    private final TransactionTemplate readOnlyTransaction;
    private final boolean enabled;
    private final int pageSize;
    private final List<Duration> retryDelays;
    private final Sleeper sleeper;

    @Autowired
    UserRegisteredReplayer(
            UserRepository users,
            UserRegisteredPublisher publisher,
            PlatformTransactionManager transactionManager,
            @Value("${bankflow.events.replay-on-startup:true}") boolean enabled) {
        this(
                users,
                publisher,
                transactionManager,
                enabled,
                DEFAULT_PAGE_SIZE,
                DEFAULT_RETRY_DELAYS,
                Thread::sleep);
    }

    UserRegisteredReplayer(
            UserRepository users,
            UserRegisteredPublisher publisher,
            PlatformTransactionManager transactionManager,
            boolean enabled,
            int pageSize,
            List<Duration> retryDelays,
            Sleeper sleeper) {
        this.users = users;
        this.publisher = publisher;
        this.readOnlyTransaction = new TransactionTemplate(transactionManager);
        this.readOnlyTransaction.setReadOnly(true);
        this.enabled = enabled;
        this.pageSize = pageSize;
        this.retryDelays = List.copyOf(retryDelays);
        this.sleeper = sleeper;
    }

    @EventListener(ApplicationReadyEvent.class)
    void startReplay() {
        if (!enabled) {
            log.info("Rejeu de UserRegistered désactivé");
            return;
        }
        Thread.ofVirtual().name("user-registered-replay").start(this::replaySafely);
    }

    /** Rejoue tous les utilisateurs sans jamais lever : une erreur est journalisée. */
    void replaySafely() {
        try {
            replayAll();
        } catch (RuntimeException exception) {
            log.error("Rejeu de UserRegistered interrompu", exception);
        }
    }

    /**
     * Republie les utilisateurs page par page jusqu'au premier envoi définitivement en échec, et
     * renvoie le nombre envoyé.
     */
    int replayAll() {
        var sent = 0;
        var pageNumber = 0;
        boolean hasNext;
        do {
            var page = readPage(pageNumber++);
            for (var event : page.events()) {
                if (!sendWithRetry(event)) {
                    log.warn(
                            "Rejeu de UserRegistered interrompu après {} utilisateurs : Kafka"
                                    + " indisponible",
                            sent);
                    return sent;
                }
                sent++;
            }
            hasNext = page.hasNext();
        } while (hasNext);
        log.info("UserRegistered republié pour {} utilisateurs", sent);
        return sent;
    }

    /** Envoie l'événement, en le retentant après chaque attente prévue tant que l'envoi échoue. */
    private boolean sendWithRetry(UserRegistered event) {
        if (publisher.send(event)) {
            return true;
        }
        for (var delay : retryDelays) {
            log.info("Kafka indisponible, nouvel essai du rejeu de UserRegistered dans {}", delay);
            try {
                sleeper.sleep(delay);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return false;
            }
            if (publisher.send(event)) {
                return true;
            }
        }
        return false;
    }

    private EventsPage readPage(int pageNumber) {
        return readOnlyTransaction.execute(status -> {
            var slice = users.findAllBy(PageRequest.of(pageNumber, pageSize, ORDER_BY_CREATION));
            var events =
                    slice.map(user ->
                                    new UserRegistered(
                                            user.getId(),
                                            user.getEmail(),
                                            user.isDemo(),
                                            user.getRole(),
                                            user.getCreatedAt()))
                            .getContent();
            return new EventsPage(events, slice.hasNext());
        });
    }

    /** Attente entre deux essais, remplaçable dans les tests. */
    @FunctionalInterface
    interface Sleeper {
        void sleep(Duration duration) throws InterruptedException;
    }

    private record EventsPage(List<UserRegistered> events, boolean hasNext) {}
}
