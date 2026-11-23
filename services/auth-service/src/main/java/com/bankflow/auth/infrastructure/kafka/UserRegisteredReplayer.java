package com.bankflow.auth.infrastructure.kafka;

import com.bankflow.auth.domain.user.UserRegistered;
import com.bankflow.auth.domain.user.UserRepository;
import com.bankflow.auth.infrastructure.bootstrap.AdminAccountSeeder;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Republie {@code UserRegistered} pour tous les utilisateurs à chaque démarrage.
 *
 * <p>Ce rejeu rattrape les comptes créés sans événement : les comptes seedés (administrateur et
 * démonstration), les comptes antérieurs à la MVP 2 et les événements perdus lors d'une panne de
 * Kafka. Les consommateurs sont idempotents sur la clé métier ({@code userId}) : recevoir un
 * événement déjà traité ne change rien. Tant que l'Outbox de BF-011 n'existe pas, c'est ce rejeu
 * qui garantit la livraison. Une panne de Kafka est journalisée sans empêcher le démarrage.
 *
 * <p>Il s'exécute après les seeders, pour inclure les comptes qu'ils viennent de créer, et se
 * désactive avec {@code bankflow.events.replay-on-startup=false}.
 */
@Component
@Order(AdminAccountSeeder.ORDER + 2)
class UserRegisteredReplayer implements ApplicationRunner {

    static final int PAGE_SIZE = 100;

    private static final Logger log = LoggerFactory.getLogger(UserRegisteredReplayer.class);
    private static final Sort ORDER_BY_CREATION = Sort.by("createdAt", "id");

    private final UserRepository users;
    private final UserRegisteredPublisher publisher;
    private final TransactionTemplate readOnlyTransaction;
    private final boolean enabled;

    UserRegisteredReplayer(
            UserRepository users,
            UserRegisteredPublisher publisher,
            PlatformTransactionManager transactionManager,
            @Value("${bankflow.events.replay-on-startup:true}") boolean enabled) {
        this.users = users;
        this.publisher = publisher;
        this.readOnlyTransaction = new TransactionTemplate(transactionManager);
        this.readOnlyTransaction.setReadOnly(true);
        this.enabled = enabled;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!enabled) {
            log.info("Rejeu de UserRegistered désactivé");
            return;
        }
        try {
            replayAll();
        } catch (RuntimeException exception) {
            log.error("Rejeu de UserRegistered interrompu", exception);
        }
    }

    /** Republie tous les utilisateurs, page par page, et renvoie leur nombre. */
    int replayAll() {
        var count = 0;
        var pageNumber = 0;
        while (true) {
            var events = readPage(pageNumber);
            events.forEach(publisher::send);
            count += events.size();
            if (events.size() < PAGE_SIZE) {
                break;
            }
            pageNumber++;
        }
        log.info("UserRegistered republié pour {} utilisateurs", count);
        return count;
    }

    private List<UserRegistered> readPage(int pageNumber) {
        return readOnlyTransaction.execute(status ->
                users.findAll(PageRequest.of(pageNumber, PAGE_SIZE, ORDER_BY_CREATION))
                        .map(user ->
                                new UserRegistered(
                                        user.getId(),
                                        user.getEmail(),
                                        user.isDemo(),
                                        user.getCreatedAt()))
                        .getContent());
    }
}
