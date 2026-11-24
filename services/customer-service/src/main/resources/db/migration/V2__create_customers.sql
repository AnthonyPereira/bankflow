CREATE TABLE customers (
    id          UUID PRIMARY KEY,
    user_id     UUID         NOT NULL UNIQUE,
    email       VARCHAR(254) NOT NULL,
    first_name  VARCHAR(100),
    last_name   VARCHAR(100),
    phone       VARCHAR(20),
    address     VARCHAR(255),
    status      VARCHAR(16)  NOT NULL CHECK (status IN ('ACTIVE', 'SUSPENDED')),
    demo        BOOLEAN      NOT NULL DEFAULT FALSE,
    version     BIGINT       NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ  NOT NULL,
    updated_at  TIMESTAMPTZ  NOT NULL,
    CONSTRAINT customers_demo_never_suspended CHECK (NOT (demo AND status = 'SUSPENDED'))
);
CREATE INDEX ix_customers_status ON customers (status);
