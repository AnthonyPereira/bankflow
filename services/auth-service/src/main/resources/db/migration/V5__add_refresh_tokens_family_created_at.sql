-- Date d'ouverture de la session, recopiée à chaque rotation : elle borne la durée de vie
-- absolue d'une famille de jetons (ADR-003, section 2.4)
ALTER TABLE refresh_tokens ADD COLUMN family_created_at TIMESTAMPTZ;

-- Pour les familles existantes, le plus ancien jeton encore en base fait foi. Si les plus anciens
-- jetons d'une famille ont déjà été purgés, min(created_at) sous-estime l'âge de la session, qui
-- peut alors durer un peu plus de 30 jours. C'est acceptable : aucune session n'existe encore en
-- production au moment de cette migration.
UPDATE refresh_tokens t
SET family_created_at = (
    SELECT min(f.created_at) FROM refresh_tokens f WHERE f.family_id = t.family_id
)
WHERE t.family_created_at IS NULL;

ALTER TABLE refresh_tokens ALTER COLUMN family_created_at SET NOT NULL;
