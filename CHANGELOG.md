# Changelog

Toutes les évolutions notables de BankFlow. Le format suit [Keep a Changelog](https://keepachangelog.com/fr/1.1.0/) et les versions suivent [SemVer](https://semver.org/lang/fr/).

## [Non publié]

### Ajouté

- **Customer Service** : la fiche client est créée à l'inscription, à partir de l'événement `UserRegistered`. Le client consulte et modifie son profil (`/customers/me`). L'administrateur liste, recherche et suspend les clients, mais jamais un compte de démonstration. Les événements `CustomerCreated` et `CustomerUpdated` sont publiés sur `bank.customers`. #62
- **Rattrapage des fiches client** : à chaque démarrage, l'Auth Service republie `UserRegistered` pour tous les comptes. Les comptes de démonstration, le compte admin, les inscrits d'avant la 0.2.0 et les événements perdus pendant une panne de Kafka reçoivent ainsi leur fiche. Ce rejeu tourne en arrière-plan et ne retarde pas le démarrage. #59
- **Banking Service, comptes** :
  - ouverture de comptes en EUR ou en USD, avec un numéro IBAN fictif valide, et au plus 5 comptes ouverts par client ;
  - consultation, solde et clôture d'un compte, uniquement si son solde est nul ;
  - les montants sont des décimaux exacts, sans aucun nombre à virgule flottante ;
  - le Banking Service garde une copie locale des clients, alimentée par Kafka ([ADR-004](docs/adr/ADR-004-reference-client-banking.md)). Un événement plus ancien que l'état connu est ignoré. #61 #58
- **Banking Service, opérations** (BF-008) :
  - dépôts et retraits, au plus 10 000,00 par opération et 2 décimales, sans découvert ;
  - comptabilité en partie double avec un compte technique de caisse, écritures rendues immuables par la base (triggers) ;
  - protection contre le double envoi (`Idempotency-Key`), rattachée à la personne qui agit ;
  - historique paginé et filtrable (`GET /transactions`), avec le sens de chaque opération et le compte d'en face pour un virement ;
  - écriture compensatrice, réservée à l'administrateur, avec un motif ([ADR-006](docs/adr/ADR-006-comptabilite-coherence.md)). #71
- **Banking Service, virements** (BF-009) : virement vers tout compte actif de la banque par son IBAN, dans une seule transaction, comptes verrouillés dans un ordre fixe pour éviter les blocages mutuels. #72
- **Administrateur conseiller** ([ADR-005](docs/adr/ADR-005-role-administrateur.md)) : il ouvre un compte pour un client, y dépose, y retire, fait un virement ou le clôture. Il n'a jamais de compte à son nom. Chaque opération indique qui l'a faite. Vue de la banque (`GET /accounts/summary`). #68 #71 #72
- **Vitrine** ([ADR-007](docs/adr/ADR-007-garde-fous-vitrine.md)) : `demo.client` (Camille Martin) a un compte courant avec un historique et un livret d'épargne, recréés au démarrage s'ils ont disparu. Rien n'est créé avec `DEMO_ACCOUNTS_ENABLED=false`. #75
- **Frontend, MVP 2** : détail d'un compte (dépôt, retrait, clôture, annulation par l'administrateur), « Mes opérations », virement en trois étapes, tableaux de bord du client et de l'administrateur, écran « Clients » de l'administrateur. #69 #70 #73
- **Frontend** : écrans « Mon profil » (avec nouvel essai si la fiche est encore en cours de création) et « Mes comptes », pipes de montants et de dates en français. #60
- **Contrats** : `CustomerCreated` v1, `CustomerUpdated` v1, et champ facultatif `demo` dans `UserRegistered`. #58

### Corrigé

- **Gateway** : elle transmet les en-têtes `X-Forwarded-*` des proxys de confiance (`TRUSTED_PROXIES`). L'adresse renvoyée après une création est donc l'adresse publique. #66

## [0.1.1] — 2026-11-23

### Corrigé

- Les images des services Spring se construisent à nouveau : sans `unzip`, le wrapper Maven téléchargeait le `.tar.gz`, dont l'empreinte ne correspond pas à celle vérifiée (`distributionSha256Sum`). La 0.1.0 n'a donc jamais été mise en ligne ; la 0.1.1 est la première version déployée.

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

- Le code de démonstration Kafka ne fait plus partie de l'artefact de production (il est passé dans `src/test`).
- Les méthodes sont nommées par un verbe, et les modèles d'auth du frontend sont passés de `shared/models` à `core/auth`.
- Passage à Spring Boot 4.2.0 et Spring Cloud 2026.0.0 en version GA (au lieu des RC2). #35
- Dans chaque couche, les classes sont rangées par fonctionnalité. #36
- Les icônes du frontend sont des fichiers SVG au lieu d'une police d'icônes. #30

### Corrigé

- Un refresh simultané depuis deux onglets ne perd plus le cookie de session. #26
- Les alertes de sécurité remontées par SonarQube sont traitées. #33
- Corrections issues de la revue de code de la release :
  - **Gateway** : la limite de débit s'applique au chemin décodé et normalisé. Un chemin encodé comme `/api/auth/%6Cogin` contournait la limite de la connexion, de l'inscription et des virements.
  - **Connexion** : chaque tentative est réservée par un `UPDATE` atomique avant la vérification du mot de passe. Des tentatives simultanées ne dépassent donc plus les 5 essais autorisés, et aucun verrou n'est tenu pendant le hachage.
  - **Sessions** : une session dure au plus 30 jours (`bankflow.refresh.max-session-age`), même si elle est renouvelée en continu ([ADR-003](docs/adr/ADR-003-sessions-refresh-token.md) §2.4).
  - **`/auth/me`** : la route refuse la session d'un compte supprimé ou désactivé, avec un message adapté.
  - **Comptes de démonstration** : ils suivent les mêmes règles que les autres comptes, mais ne peuvent jamais être désactivés définitivement (règle du domaine, contrainte en base `users_demo_always_enabled`, 409). Leurs adresses sont réservées : personne ne peut s'inscrire avec.
  - **Frontend** :
    - un visiteur anonyme ne fait plus qu'un appel de refresh au démarrage, grâce à un verrou partagé entre onglets ;
    - si l'inscription réussit mais que la connexion automatique échoue, l'utilisateur est renvoyé vers la page de connexion ;
    - les libellés de rôle sont centralisés.
  - **Déploiement et infra** :
    - le déploiement attend que la CI du commit soit verte ;
    - les images de base sont épinglées par digest ;
    - nginx ajoute HSTS et ne croit `CF-Connecting-IP` que depuis un proxy de confiance ;
    - les checkouts de la CI n'utilisent plus `persist-credentials`.
- Corrections issues de la revue globale avant mise en ligne :
  - **Jetons** : une clé de signature éphémère reçoit un `kid` unique, pour que la Gateway recharge le JWKS après un redémarrage de l'Auth Service. Un jeton expiré joint à une route publique (connexion, refresh, déconnexion) n'est plus refusé, ni par la Gateway ni par l'Auth Service.
  - **Refresh token** : le délai de grâce de 10 s ne vaut plus qu'après une rotation, pas après une déconnexion ou une révocation pour vol. Les jetons révoqués sont gardés tant que leur session peut durer, pour détecter leur réutilisation.
  - **Inscription** : les erreurs remontent sur leur champ (mot de passe de plus de 72 octets, adresse trop longue une fois normalisée) ; seul un email en double donne 409.
  - **Gateway** : le quota par IP compte un préfixe /64 en IPv6 ; un service injoignable ne remplit plus les logs de piles d'appels.
  - **Frontend** : les guards attendent un refresh en cours ; la déconnexion est propagée aux autres onglets et signale un échec du serveur ; plusieurs 401 simultanés ne redirigent qu'une fois ; accessibilité du menu (lien d'évitement, focus, `aria-expanded`).
  - **Infra** : Dependabot ne propose plus de montée majeure des images (une PostgreSQL 19 aurait démarré sur une base vide) ; healthcheck Kafka allégé ; images locales alignées sur la prod ; empreinte SHA-256 vérifiée pour Maven ; `/actuator/prometheus` n'est plus exposé.
  - **Déploiement** : le dépôt public ne détient plus de jeton vers deploy-platform, qui repère lui-même les nouvelles versions ; nginx relaie vers `bankflow-api-gateway`, un nom propre à l'application.

### Limites connues

- Comme tout compte, un compte de démonstration est bloqué 15 minutes après 5 mots de passe faux. Ses identifiants étant publics, n'importe qui peut provoquer ce blocage.
- Seuls le frontend, la Gateway, l'Auth Service, PostgreSQL et Kafka sont déployés : les routes des quatre autres services répondent 503.
- Le Customer Service, le Banking Service, le Notification Service et l'Analytics & Fraud Service ne sont que des squelettes sécurisés, sans fonctionnalité métier.

[Non publié]: https://github.com/AnthonyPereira/bankflow/compare/v0.1.1...develop
[0.1.1]: https://github.com/AnthonyPereira/bankflow/releases/tag/v0.1.1
[0.1.0]: https://github.com/AnthonyPereira/bankflow/releases/tag/v0.1.0
