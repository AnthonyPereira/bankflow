package com.bankflow.banking.domain.ledger;

public enum LedgerDirection {
    DEBIT,
    CREDIT;

    public LedgerDirection createOpposite() {
        return this == DEBIT ? CREDIT : DEBIT;
    }
}
