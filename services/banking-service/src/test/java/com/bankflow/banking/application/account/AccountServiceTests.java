package com.bankflow.banking.application.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bankflow.banking.IbanTestSupport;
import com.bankflow.banking.domain.account.Account;
import com.bankflow.banking.domain.account.AccountNumberGenerator;
import com.bankflow.banking.domain.account.AccountRepository;
import com.bankflow.banking.domain.account.AccountStatus;
import com.bankflow.banking.domain.customer.CustomerRef;
import com.bankflow.banking.domain.customer.CustomerRefRepository;
import com.bankflow.banking.domain.customer.CustomerStatus;
import com.bankflow.banking.domain.money.Currency;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AccountServiceTests {

    private static final Instant NOW = Instant.parse("2026-11-23T10:00:00Z");

    private final AccountRepository accounts = mock(AccountRepository.class);
    private final CustomerRefRepository customerRefs = mock(CustomerRefRepository.class);
    private final UUID userId = UUID.randomUUID();
    private AccountService service;

    @BeforeEach
    void setUp() {
        service =
                new AccountService(
                        accounts,
                        customerRefs,
                        new AccountNumberGenerator(new SecureRandom()),
                        Clock.fixed(NOW, ZoneOffset.UTC));
        when(accounts.insertIfNumberFree(any(Account.class))).thenReturn(true);
    }

    private CustomerRef givenCustomer(CustomerStatus status) {
        var customer = new CustomerRef(UUID.randomUUID(), userId, "a@bankflow.test", status, NOW);
        when(customerRefs.findByUserIdForUpdate(userId)).thenReturn(Optional.of(customer));
        when(customerRefs.findByUserId(userId)).thenReturn(Optional.of(customer));
        return customer;
    }

    @Test
    void ouvreUnCompteSousLaLimiteDeCinqComptesActifs() {
        var customer = givenCustomer(CustomerStatus.ACTIVE);
        when(accounts.countByCustomerIdAndStatus(customer.getCustomerId(), AccountStatus.ACTIVE))
                .thenReturn(4L);

        var account = service.openAccount(userId, Currency.EUR, "Courant");

        assertThat(account.getCustomerId()).isEqualTo(customer.getCustomerId());
        assertThat(account.getLabel()).isEqualTo("Courant");
        assertThat(IbanTestSupport.isValidIban(account.getAccountNumber())).isTrue();
    }

    @Test
    void refuseLeSixiemeCompteActif() {
        var customer = givenCustomer(CustomerStatus.ACTIVE);
        when(accounts.countByCustomerIdAndStatus(customer.getCustomerId(), AccountStatus.ACTIVE))
                .thenReturn(5L);

        assertThatThrownBy(() -> service.openAccount(userId, Currency.EUR, null))
                .isInstanceOf(AccountLimitReachedException.class);
        verify(accounts, never()).insertIfNumberFree(any());
    }

    @Test
    void refuseUnClientSuspendu() {
        givenCustomer(CustomerStatus.SUSPENDED);

        assertThatThrownBy(() -> service.openAccount(userId, Currency.EUR, null))
                .isInstanceOf(CustomerSuspendedException.class);
    }

    @Test
    void refuseUnClientSansReference() {
        when(customerRefs.findByUserIdForUpdate(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.openAccount(userId, Currency.EUR, null))
                .isInstanceOf(CustomerNotReadyException.class);
    }

    @Test
    void regenereLeNumeroEnCasDeCollision() {
        var customer = givenCustomer(CustomerStatus.ACTIVE);
        when(accounts.countByCustomerIdAndStatus(customer.getCustomerId(), AccountStatus.ACTIVE))
                .thenReturn(0L);
        when(accounts.insertIfNumberFree(any())).thenReturn(false, false, true);

        var account = service.openAccount(userId, Currency.USD, null);

        assertThat(account.getAccountNumber()).hasSize(27);
        verify(accounts, org.mockito.Mockito.times(3)).insertIfNumberFree(any());
    }

    @Test
    void abandonneApresTroisCollisions() {
        var customer = givenCustomer(CustomerStatus.ACTIVE);
        when(accounts.countByCustomerIdAndStatus(customer.getCustomerId(), AccountStatus.ACTIVE))
                .thenReturn(0L);
        when(accounts.insertIfNumberFree(any())).thenReturn(false);

        assertThatThrownBy(() -> service.openAccount(userId, Currency.EUR, null))
                .isInstanceOf(AccountNumberUnavailableException.class);
    }

    @Test
    void unNonProprietaireRecoitCompteIntrouvable() {
        givenCustomer(CustomerStatus.ACTIVE);
        var foreign =
                Account.open(
                        UUID.randomUUID(), "FR7699999000010000000000000", Currency.EUR, null, NOW);
        when(accounts.findById(foreign.getId())).thenReturn(Optional.of(foreign));
        when(accounts.findByIdForUpdate(foreign.getId())).thenReturn(Optional.of(foreign));

        assertThatThrownBy(() -> service.getAccount(userId, false, foreign.getId()))
                .isInstanceOf(AccountNotFoundException.class);
        assertThatThrownBy(() -> service.closeAccount(userId, false, foreign.getId()))
                .isInstanceOf(AccountNotFoundException.class);
        assertThat(service.getAccount(userId, true, foreign.getId())).isSameAs(foreign);
    }
}
