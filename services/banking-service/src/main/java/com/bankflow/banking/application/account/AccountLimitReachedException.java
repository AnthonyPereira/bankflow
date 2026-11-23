package com.bankflow.banking.application.account;

import com.bankflow.banking.domain.account.Account;

public class AccountLimitReachedException extends RuntimeException {

    public AccountLimitReachedException() {
        super(
                "Vous avez atteint la limite de "
                        + Account.MAX_ACTIVE_PER_CUSTOMER
                        + " comptes actifs.");
    }
}
