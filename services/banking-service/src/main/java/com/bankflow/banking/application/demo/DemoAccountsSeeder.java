package com.bankflow.banking.application.demo;

import com.bankflow.banking.application.account.AccountService;
import com.bankflow.banking.application.operation.OperationService;
import com.bankflow.banking.application.transfer.TransferService;
import com.bankflow.banking.domain.account.AccountRepository;
import com.bankflow.banking.domain.account.AccountStatus;
import com.bankflow.banking.domain.customer.CustomerRef;
import com.bankflow.banking.domain.customer.CustomerRefCreated;
import com.bankflow.banking.domain.customer.CustomerRefRepository;
import com.bankflow.banking.domain.customer.CustomerStatus;
import com.bankflow.banking.domain.money.Currency;
import com.bankflow.banking.domain.transaction.Actor;
import java.math.BigDecimal;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Donne au client de démonstration deux comptes et un historique, pour que la vitrine ne soit
 * jamais vide. Tout passe par les services métier, au nom du client : les règles et les écritures
 * comptables sont celles d'un vrai usage.
 *
 * <p>Déclenché au démarrage et à la création de la référence du client. Rien n'est fait si le
 * client a déjà un compte actif ; la ligne du client est verrouillée pour que deux déclenchements
 * simultanés ne créent pas de doublon. Un échec est journalisé sans jamais bloquer le service.
 */
@Component
@EnableConfigurationProperties(DemoAccountsProperties.class)
public class DemoAccountsSeeder {

    private static final Logger log = LoggerFactory.getLogger(DemoAccountsSeeder.class);

    private final DemoAccountsProperties properties;
    private final CustomerRefRepository customerRefs;
    private final AccountRepository accounts;
    private final AccountService accountService;
    private final OperationService operations;
    private final TransferService transfers;
    private final TransactionTemplate transaction;

    DemoAccountsSeeder(
            DemoAccountsProperties properties,
            CustomerRefRepository customerRefs,
            AccountRepository accounts,
            AccountService accountService,
            OperationService operations,
            TransferService transfers,
            PlatformTransactionManager transactionManager) {
        this.properties = properties;
        this.customerRefs = customerRefs;
        this.accounts = accounts;
        this.accountService = accountService;
        this.operations = operations;
        this.transfers = transfers;
        this.transaction = new TransactionTemplate(transactionManager);
        this.transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /** Rattrape au démarrage un client dont la référence existe déjà. */
    @EventListener(ApplicationReadyEvent.class)
    public void seedOnStartup() {
        if (!properties.enabled()) {
            return;
        }
        try {
            customerRefs
                    .findAllByEmailIgnoreCase(properties.clientEmail())
                    .forEach(ref -> seedAccounts(ref.getCustomerId()));
        } catch (RuntimeException exception) {
            log.warn("Données de démonstration non créées au démarrage ({})", exception.toString());
        }
    }

    /** Après le commit de la création de la référence : la nouvelle transaction la voit. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void seedOnCustomerRefCreated(CustomerRefCreated event) {
        if (properties.enabled()) {
            seedAccounts(event.customerId());
        }
    }

    /** Ne lève jamais d'exception : la création est tout ou rien, un échec annule tout. */
    public void seedAccounts(UUID customerId) {
        try {
            transaction.executeWithoutResult(status -> createAccountsIfNeeded(customerId));
        } catch (RuntimeException exception) {
            log.warn(
                    "Données de démonstration non créées pour le client {} ({})",
                    customerId,
                    exception.toString());
        }
    }

    private void createAccountsIfNeeded(UUID customerId) {
        var customer = customerRefs.findByCustomerIdForUpdate(customerId).orElse(null);
        if (customer == null || !isEligible(customer)) {
            return;
        }
        if (accounts.countByCustomerIdAndStatus(customerId, AccountStatus.ACTIVE) > 0) {
            return;
        }
        var userId = customer.getUserId();
        var actor = Actor.createFrom(userId, false);
        var current = accountService.openAccount(userId, Currency.EUR, "Compte courant");
        var savings = accountService.openAccount(userId, Currency.EUR, "Livret épargne");
        operations.deposit(actor, current.getId(), amount("2450.00"), "Salaire de novembre");
        operations.withdraw(actor, current.getId(), amount("60.00"), "Retrait distributeur");
        operations.deposit(actor, current.getId(), amount("35.50"), "Remboursement");
        transfers.createTransfer(
                actor,
                current.getId(),
                savings.getAccountNumber(),
                amount("200.00"),
                "Épargne du mois");
        log.info("Données de démonstration créées");
    }

    private boolean isEligible(CustomerRef customer) {
        return customer.getStatus() == CustomerStatus.ACTIVE
                && customer.getEmail().equalsIgnoreCase(properties.clientEmail());
    }

    private static BigDecimal amount(String value) {
        return new BigDecimal(value);
    }
}
