CREATE TABLE customer_refs (
    customer_id UUID PRIMARY KEY,
    user_id     UUID         NOT NULL UNIQUE,
    email       VARCHAR(254) NOT NULL,
    status      VARCHAR(16)  NOT NULL CHECK (status IN ('ACTIVE', 'SUSPENDED')),
    updated_at  TIMESTAMPTZ  NOT NULL
);

CREATE TABLE accounts (
    id             UUID PRIMARY KEY,
    customer_id    UUID          NOT NULL REFERENCES customer_refs (customer_id),
    account_number VARCHAR(34)   NOT NULL UNIQUE,
    label          VARCHAR(50),
    currency       VARCHAR(3)    NOT NULL CHECK (currency IN ('EUR', 'USD')),
    balance        NUMERIC(19,4) NOT NULL DEFAULT 0 CHECK (balance >= 0),
    status         VARCHAR(16)   NOT NULL CHECK (status IN ('ACTIVE', 'CLOSED')),
    version        BIGINT        NOT NULL DEFAULT 0,
    created_at     TIMESTAMPTZ   NOT NULL,
    closed_at      TIMESTAMPTZ,
    CONSTRAINT accounts_closed_at CHECK ((status = 'CLOSED') = (closed_at IS NOT NULL))
);
CREATE INDEX ix_accounts_customer ON accounts (customer_id);

CREATE TABLE transfers (
    id                     UUID PRIMARY KEY,
    source_account_id      UUID          NOT NULL REFERENCES accounts (id),
    destination_account_id UUID          NOT NULL REFERENCES accounts (id),
    amount                 NUMERIC(19,4) NOT NULL CHECK (amount > 0),
    currency               VARCHAR(3)    NOT NULL,
    description            VARCHAR(140),
    status                 VARCHAR(16)   NOT NULL CHECK (status IN ('PENDING', 'COMPLETED', 'REJECTED')),
    rejection_code         VARCHAR(64),
    created_by_customer_id UUID          NOT NULL,
    created_at             TIMESTAMPTZ   NOT NULL,
    completed_at           TIMESTAMPTZ,
    CONSTRAINT transfers_distinct_accounts CHECK (source_account_id <> destination_account_id)
);
CREATE INDEX ix_transfers_source ON transfers (source_account_id);
CREATE INDEX ix_transfers_destination ON transfers (destination_account_id);

CREATE TABLE transactions (
    id                        UUID PRIMARY KEY,
    account_id                UUID          NOT NULL REFERENCES accounts (id),
    type                      VARCHAR(16)   NOT NULL CHECK (type IN ('DEPOSIT', 'WITHDRAWAL', 'TRANSFER_IN', 'TRANSFER_OUT', 'COMPENSATION')),
    amount                    NUMERIC(19,4) NOT NULL CHECK (amount > 0),
    currency                  VARCHAR(3)    NOT NULL,
    balance_after             NUMERIC(19,4) NOT NULL CHECK (balance_after >= 0),
    transfer_id               UUID REFERENCES transfers (id),
    compensates_transaction_id UUID REFERENCES transactions (id),
    description               VARCHAR(140),
    created_at                TIMESTAMPTZ   NOT NULL
);
CREATE INDEX ix_transactions_account_created ON transactions (account_id, created_at DESC);

-- Partie double : chaque opération produit des écritures équilibrées. Le compte technique CASH
-- représente la caisse de la banque (contrepartie des dépôts et retraits).
CREATE TABLE ledger_entries (
    id             UUID PRIMARY KEY,
    transaction_id UUID          NOT NULL REFERENCES transactions (id),
    ledger_account VARCHAR(16)   NOT NULL CHECK (ledger_account IN ('CUSTOMER_ACCOUNT', 'CASH')),
    account_id     UUID REFERENCES accounts (id),
    direction      VARCHAR(6)    NOT NULL CHECK (direction IN ('DEBIT', 'CREDIT')),
    amount         NUMERIC(19,4) NOT NULL CHECK (amount > 0),
    currency       VARCHAR(3)    NOT NULL,
    created_at     TIMESTAMPTZ   NOT NULL,
    CONSTRAINT ledger_entries_account CHECK ((ledger_account = 'CASH') = (account_id IS NULL))
);
CREATE INDEX ix_ledger_entries_account ON ledger_entries (account_id);

CREATE TABLE idempotency_keys (
    customer_id     UUID        NOT NULL,
    idempotency_key VARCHAR(64) NOT NULL,
    request_hash    VARCHAR(64) NOT NULL,
    response_status INTEGER     NOT NULL,
    response_body   TEXT        NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (customer_id, idempotency_key)
);
