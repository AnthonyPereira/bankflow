-- Motif de révocation d'un refresh token : seul un jeton remplacé par une rotation bénéficie du
-- délai de grâce, pas un jeton révoqué par une déconnexion ou après un vol (ADR-003, section 2.3).
-- Les jetons révoqués avant cette migration gardent un motif vide : aucun délai de grâce.
ALTER TABLE refresh_tokens ADD COLUMN revoked_reason VARCHAR(20);

ALTER TABLE refresh_tokens ADD CONSTRAINT ck_refresh_tokens_revoked_reason
    CHECK (revoked_reason IN ('ROTATION', 'LOGOUT', 'REUSE_DETECTED', 'ACCOUNT_DISABLED'));

ALTER TABLE refresh_tokens ADD CONSTRAINT ck_refresh_tokens_reason_needs_revocation
    CHECK (revoked_reason IS NULL OR revoked_at IS NOT NULL);
