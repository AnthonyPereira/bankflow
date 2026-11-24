-- La purge des clés d'idempotence parcourt la table par date de création
CREATE INDEX ix_idempotency_keys_created_at ON idempotency_keys (created_at);
