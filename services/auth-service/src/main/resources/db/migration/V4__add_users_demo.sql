-- Un compte de démonstration ne peut jamais être désactivé définitivement
ALTER TABLE users ADD COLUMN demo BOOLEAN NOT NULL DEFAULT FALSE;

-- Les comptes de démonstration créés avant cette migration
UPDATE users SET demo = TRUE
WHERE email IN ('demo.client@bankflow.local', 'demo.admin@bankflow.local');
