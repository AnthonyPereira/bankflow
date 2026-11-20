-- Date d'ouverture de la session, recopiée à chaque rotation : elle borne la durée de vie
-- absolue d'une famille de jetons (ADR-003, section 2.4)
ALTER TABLE refresh_tokens ADD COLUMN family_created_at TIMESTAMPTZ;

-- Pour les familles existantes, le plus ancien jeton encore en base fait foi
UPDATE refresh_tokens t
SET family_created_at = (
    SELECT min(f.created_at) FROM refresh_tokens f WHERE f.family_id = t.family_id
);

ALTER TABLE refresh_tokens ALTER COLUMN family_created_at SET NOT NULL;
