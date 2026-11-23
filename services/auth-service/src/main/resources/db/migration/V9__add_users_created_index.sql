-- Le rejeu de UserRegistered pagine les utilisateurs par (created_at, id) : sans index, chaque
-- page triait toute la table
CREATE INDEX ix_users_created_at_id ON users (created_at, id);
