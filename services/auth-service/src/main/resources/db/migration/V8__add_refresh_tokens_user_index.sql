-- PostgreSQL n'indexe pas une clé étrangère : la suppression d'un utilisateur (ON DELETE CASCADE)
-- et la recherche de ses sessions parcouraient toute la table
CREATE INDEX ix_refresh_tokens_user ON refresh_tokens (user_id);
