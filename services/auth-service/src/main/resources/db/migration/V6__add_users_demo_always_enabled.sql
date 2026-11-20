-- Un compte de démonstration ne peut jamais être désactivé définitivement, même par une requête
-- qui contournerait User.disable()
ALTER TABLE users ADD CONSTRAINT users_demo_always_enabled CHECK (NOT demo OR enabled);
