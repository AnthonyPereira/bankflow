# Changelog

Toutes les évolutions notables de BankFlow. Le format suit [Keep a Changelog](https://keepachangelog.com/fr/1.1.0/) et les versions suivent [SemVer](https://semver.org/lang/fr/).

## [0.1.0] — 2026-11-20

Première version publiée : **MVP 1, socle et première mise en ligne**. On peut s'inscrire, se connecter et consulter un tableau de bord. Les données bancaires (comptes, virements) arrivent en 0.2.0.

### Ajouté

- **Infrastructure**
  - Docker Compose avec PostgreSQL (une base et un utilisateur par service) et Mailpit. #6 #7
  - Kafka en mode KRaft, Kafka UI, et la création des topics. #8
  - L'enveloppe commune des événements dans `contracts/`. #9
- **API Gateway**
  - Routage `/api/**`, validation des JWT (émetteur, audience, signature), erreurs au format `ProblemDetail` et identifiant de corrélation. #11 #13
  - Limitation du débit des routes sensibles (connexion, inscription, virements) avec Bucket4j. #12
- **Auth Service**
  - Inscription, avec mots de passe hachés en BCrypt. #15
  - Connexion JWT (RS256, JWKS public), avec verrouillage temporaire après 5 échecs. #16
  - Refresh token en cookie HttpOnly, Secure et SameSite=Strict, avec rotation et révocation ([ADR-003](docs/adr/ADR-003-sessions-refresh-token.md)). #14 #17
  - Événement `UserRegistered` publié sur Kafka après chaque inscription, plus une démonstration Kafka. #10 #18
  - Compte administrateur initial créé au démarrage. #19
  - Comptes de démonstration (client et administrateur), avec des mots de passe publics affichés sur la page de connexion. `DEMO_ACCOUNTS_ENABLED=false` les désactive. #37
  - Documentation OpenAPI et Swagger UI. Elles sont désactivées en `prod-sim`. #21
- **Services métier** (squelettes) : chacun valide l'émetteur, l'audience et le rôle des JWT. #20
- **Frontend Angular 22**
  - Initialisation avec Angular Material. #22
  - Gestion de la session (JWT en mémoire), intercepteurs et guards. #27
  - Écrans de connexion et d'inscription, et tableau de bord réduit (infos du compte, sections « bientôt disponible »). #28
  - Service par Nginx dans Docker Compose. #29
- **CI/CD et déploiement**
  - CI GitHub Actions : `./mvnw verify` pour chaque service, puis lint, tests et build du frontend. #23
  - Analyse SonarQube avec la couverture. #32
  - Images Docker publiées sur GHCR à chaque fusion dans `main`, et mise en ligne sur le serveur maison via le dépôt privé `deploy-platform` ([runbook](docs/runbooks/deploiement.md)). #31 #34
- **Documentation** : ADR-001 à ADR-003, conventions, runbooks et `CLAUDE.md`. #4 #5 #38

### Modifié

- Passage à Spring Boot 4.2.0 et Spring Cloud 2026.0.0 en version GA (au lieu des RC2). #35
- Dans chaque couche, les classes sont rangées par fonctionnalité. #36
- Les icônes du frontend sont des fichiers SVG au lieu d'une police d'icônes. #30

### Corrigé

- Un refresh simultané depuis deux onglets ne perd plus le cookie de session. #26
- Les alertes de sécurité remontées par SonarQube sont traitées. #33

### Limites connues

- N'importe qui peut bloquer un compte de démonstration pendant 15 minutes en se trompant 5 fois de mot de passe.
- Le Customer Service, le Banking Service, le Notification Service et l'Analytics & Fraud Service ne sont que des squelettes sécurisés, sans fonctionnalité métier.

[0.1.0]: https://github.com/AnthonyPereira/bankflow/releases/tag/v0.1.0
