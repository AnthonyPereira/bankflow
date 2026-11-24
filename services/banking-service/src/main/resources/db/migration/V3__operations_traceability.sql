-- Tables encore vides : les colonnes obligatoires s'ajoutent sans valeur par défaut
ALTER TABLE transactions ADD COLUMN performed_by_user_id UUID NOT NULL;
ALTER TABLE transactions ADD COLUMN performed_by_role VARCHAR(16) NOT NULL
    CHECK (performed_by_role IN ('CLIENT', 'ADMIN'));
ALTER TABLE transfers ADD COLUMN performed_by_user_id UUID NOT NULL;
ALTER TABLE transfers ADD COLUMN performed_by_role VARCHAR(16) NOT NULL
    CHECK (performed_by_role IN ('CLIENT', 'ADMIN'));
-- Une opération ne se compense qu'une fois
CREATE UNIQUE INDEX ux_transactions_compensates ON transactions (compensates_transaction_id)
    WHERE compensates_transaction_id IS NOT NULL;

-- La clé d'idempotence appartient à la personne qui agit (client ou administrateur)
DROP TABLE idempotency_keys;
CREATE TABLE idempotency_keys (
    actor_user_id   UUID        NOT NULL,
    idempotency_key VARCHAR(64) NOT NULL,
    request_hash    VARCHAR(64) NOT NULL,
    response_status INTEGER,
    response_body   TEXT,
    created_at      TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (actor_user_id, idempotency_key)
);

-- Écritures immuables : toute modification ou suppression est refusée par la base
CREATE FUNCTION forbid_ledger_change() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'Les écritures comptables sont immuables (%).', TG_TABLE_NAME;
END;
$$;
CREATE TRIGGER transactions_immutable BEFORE UPDATE OR DELETE ON transactions
    FOR EACH ROW EXECUTE FUNCTION forbid_ledger_change();
CREATE TRIGGER transactions_no_truncate BEFORE TRUNCATE ON transactions
    FOR EACH STATEMENT EXECUTE FUNCTION forbid_ledger_change();
CREATE TRIGGER ledger_entries_immutable BEFORE UPDATE OR DELETE ON ledger_entries
    FOR EACH ROW EXECUTE FUNCTION forbid_ledger_change();
CREATE TRIGGER ledger_entries_no_truncate BEFORE TRUNCATE ON ledger_entries
    FOR EACH STATEMENT EXECUTE FUNCTION forbid_ledger_change();

-- Historique trié du plus récent au plus ancien, et lecture des écritures d une opération
CREATE INDEX ix_transactions_created ON transactions (created_at DESC, id DESC);
CREATE INDEX ix_ledger_entries_transaction ON ledger_entries (transaction_id);
