# BankFlow — Backlog

> Tiré de `Cahier_des_charges_technique_BankFlow.docx` (v1.0).
> Le backlog suit les 23 lots (BF-001 à BF-023) de la section 14 du cahier des charges. Comme le demande ce document, chaque lot indique un **objectif fonctionnel**, un **objectif pédagogique**, les **tâches** (fichiers/classes, API, événements), les **tests** et les **critères de validation**.

**Statuts :** `[ ]` à faire · `[~]` en cours · `[x]` terminé
**Priorités :** P0 = indispensable au socle · P1 = cœur V1 · P2 = complément V1 · P3 = phase d'approfondissement

---

## Vue d'ensemble

| Lot | Domaine | Contenu | Priorité | Dépend de |
|---|---|---|---|---|
| BF-001 | Initialisation | Dépôt Git et conventions | P0 | — |
| BF-002 | Infrastructure | Docker Compose et PostgreSQL | P0 | BF-001 |
| BF-003 | Kafka | Broker, topics, premier Producer et Consumer | P0 | BF-002 |
| BF-004 | API Gateway | Routage et sécurité | P0 | BF-002 |
| BF-005 | Auth Service | Authentification et JWT | P0 | BF-002, BF-004 |
| BF-006 | Customer Service | Gestion des clients | P1 | BF-003, BF-005 |
| BF-007 | Banking Service | Comptes bancaires | P1 | BF-005, BF-006 |
| BF-008 | Banking Service | Dépôts et retraits | P1 | BF-007 |
| BF-009 | Banking Service | Virements | P1 | BF-008 |
| BF-010 | Kafka | Événements métier | P1 | BF-003, BF-009 |
| BF-011 | Kafka | Transactional Outbox | P1 | BF-010 |
| BF-012 | Notification | Consommateurs et emails | P1 | BF-011 |
| BF-013 | Kafka | Idempotence et DLQ | P1 | BF-012 |
| BF-014 | Analytics | Statistiques | P2 | BF-013 |
| BF-015 | Fraud | Détection de fraude | P2 | BF-014 |
| BF-016 | Angular | Interface utilisateur (socle, auth, dashboard) | P1 | BF-004, BF-005 |
| BF-017 | Angular | Comptes et virements | P1 | BF-016, BF-009 |
| BF-018 | Angular | Notifications, statistiques, administration | P2 | BF-017, BF-012, BF-015 |
| BF-019 | Fiabilité | Tests de résilience | P2 | BF-013 |
| BF-020 | Monitoring | Prometheus et Grafana | P2 | BF-013 |
| BF-021 | Architecture | Saga Pattern | P3 | BF-011 |
| BF-022 | Optimisation | Kafka Streams | P3 | BF-015 |
| BF-023 | Finalisation | Documentation, CI/CD et déploiement (déploiement avancé au jalon MVP 1) | P2 | tous |

> **Note :** la CI de base (build et tests) peut démarrer dès BF-001 et s'enrichir au fil des lots. BF-023 la finalise.

### Mise en ligne sur le serveur maison

L'application sera mise en ligne sur le serveur maison (portable Ubuntu avec Docker, Traefik et tunnel Cloudflare, déploiement par runner GitHub auto-hébergé) dès qu'une MVP existe. Procédure de référence : document « Serveur maison : connexion et hébergement d'applis web Docker ».

| Jalon | Release | Lots nécessaires | Ce qui est visible en ligne |
|---|---|---|---|
| **MVP 1 : socle** (cible de la première mise en ligne) | `0.1.0` | BF-002, BF-003, BF-004, BF-005, BF-016 | Inscription, connexion et dashboard Angular |
| **MVP 2 : bancaire** | `0.2.0` | MVP 1 + BF-006 à BF-009, BF-017 | Clients, comptes, dépôts, retraits et virements |

Au jalon MVP 1, on avance les tâches BF-023-05, BF-023-06 et BF-023-16 (images Docker, publication GHCR et déploiement). Ensuite, chaque release fusionnée dans `main` est déployée automatiquement : avec Gitflow, un lot terminé arrive d'abord sur `develop` et n'est mis en ligne qu'avec sa release (voir les jalons de `CONTRIBUTING.md`).

---

## BF-001 — Initialisation (dépôt Git et conventions)

**Objectif fonctionnel :** disposer d'un dépôt structuré prêt à accueillir tous les services.
**Objectif pédagogique :** organiser un mono-dépôt de microservices indépendants et vérifier la stack.

### Tâches
- [~] BF-001-01 — Vérifier la compatibilité officielle Java 27 ↔ Spring Boot ↔ Spring Cloud et figer les versions → voir [ADR-001](docs/adr/ADR-001-stack-backend.md) (versions exactes à figer après les GA). Compatibilité JDK 27 vérifiée le 2026-11-17 sur Spring Boot 4.2.0-RC2 et Spring Cloud 2026.0.0-RC2. Reste à faire : passer les six `pom.xml` en version GA après la sortie de Spring Boot 4.2 (annoncée le 2026-11-19), relancer la CI et mettre à jour ADR-001 §5
- [x] BF-001-02 — Vérifier la disponibilité d'Angular 22, d'Angular Material et de Vitest, puis figer les versions → voir [ADR-002](docs/adr/ADR-002-stack-frontend.md)
- [x] BF-001-03 — Initialiser le dépôt Git (`.gitignore` Java/Maven/Node/IDE, `.editorconfig`, `.gitattributes`)
- [x] BF-001-04 — Définir l'arborescence :
  ```
  bankflow/
  ├── services/ (api-gateway, auth-service, customer-service, banking-service, notification-service, analytics-fraud-service)
  ├── frontend/ (bankflow-web)
  ├── infra/ (docker, kafka, postgres, prometheus, grafana)
  ├── contracts/ (events/ — contrats JSON versionnés)
  └── docs/ (adr/, architecture/, runbooks/)
  ```
- [x] BF-001-05 — Générer les 6 projets Spring Boot indépendants (chacun avec son propre `pom.xml` et sans module Java métier partagé)
- [x] BF-001-06 — Rédiger les conventions (Gitflow, voir `CONTRIBUTING.md`) : nommage des branches, Conventional Commits, structure des packages (`api`, `application`, `domain`, `infrastructure`), style de code
- [x] BF-001-07 — Configurer le formatage et l'analyse statique : Spotless (google-java-format AOSP) dans chaque service. ESLint et Prettier sont reportés à BF-016-01, à la création du frontend
- [x] BF-001-08 — Rédiger le `README.md` racine (présentation, prérequis, démarrage)
- [x] BF-001-09 — Créer un modèle de `.env.example` et la règle « aucun secret commité »
- [x] BF-001-10 — Mettre en place une première CI GitHub Actions (compilation Maven de chaque service) → `.github/workflows/ci.yml` : `./mvnw verify` par service (matrice, JDK 27 Temurin, Spotless et Testcontainers), plus Dependabot (`.github/dependabot.yml`). Premier run au vert sur GitHub le 2026-11-17 ([run 44127150173](https://github.com/AnthonyPereira/bankflow/actions/runs/44127150173))

### Critères de validation
- [x] Chaque service compile seul avec `mvn verify` (vérifié le 2026-11-17 en local sur JDK 27.0.1 et en CI sur JDK 27 Temurin, avec Spring Boot 4.2.0-RC2 et Spring Cloud 2026.0.0-RC2)
- [~] Les versions retenues sont documentées et leur compatibilité est vérifiée (compatibilité JDK 27 vérifiée sur les RC ; versions GA à figer après la sortie de Spring Boot 4.2, annoncée le 2026-11-19)
- [x] Aucune classe métier n'est partagée entre services

---

## BF-002 — Infrastructure (Docker Compose et PostgreSQL)

**Objectif fonctionnel :** démarrer l'environnement local avec une seule commande.
**Objectif pédagogique :** conteneuriser l'environnement et isoler les données par service.

### Tâches
- [x] BF-002-01 — Créer `docker-compose.yml` avec un réseau Docker privé
- [x] BF-002-02 — Ajouter un conteneur PostgreSQL et un script d'initialisation qui crée `auth_db`, `customer_db`, `banking_db`, `notification_db` et `analytics_db`
- [x] BF-002-03 — Créer un utilisateur PostgreSQL dédié par base, avec des droits limités à sa propre base
- [x] BF-002-04 — Ajouter Mailpit (SMTP et interface web)
- [x] BF-002-05 — Ajouter des volumes persistants et des healthchecks
- [x] BF-002-06 — Configurer Flyway dans chaque service (dossier `db/migration`, migration V1 vide ou initiale)
- [x] BF-002-07 — Écrire un `Dockerfile` multi-stage générique pour les services Spring Boot
- [x] BF-002-08 — Externaliser la configuration (variables d'environnement et profils `dev`, `test`, `prod-sim`)
- [x] BF-002-09 — Configurer Actuator (`/actuator/health`) dans chaque service
- [x] BF-002-10 — Documenter les ports, les URL et les commandes (`docker compose up -d`, `down -v`, logs) → `infra/README.md`

### Critères de validation
- [x] `docker compose up -d` démarre PostgreSQL et Mailpit, et tous les healthchecks sont au vert
- [x] Chaque service se connecte uniquement à sa propre base

---

## BF-003 — Kafka (broker, topics, premier Producer et Consumer)

**Objectif fonctionnel :** disposer d'un bus d'événements opérationnel.
**Objectif pédagogique :** comprendre les notions de producer, consumer, topic, partition, offset, consumer group et clé.

### Tâches
- [x] BF-003-01 — Ajouter Kafka en mode KRaft (broker unique) dans Docker Compose
- [x] BF-003-02 — Ajouter Kafka UI
- [x] BF-003-03 — Créer les topics initiaux de façon déclarative (script d'initialisation ou `NewTopic`) : `bank.users`, `bank.customers`, `bank.accounts`, `bank.transfers`, `bank.transactions`, `bank.fraud-alerts`
- [x] BF-003-04 — Documenter la configuration de chaque topic (partitions, réplication, rétention, `cleanup.policy`) dans `docs/kafka/topics.md`
- [x] BF-003-05 — Définir l'enveloppe d'événement commune (`eventId`, `eventType`, `eventVersion`, `occurredAt`, `producer`, `payload`) dans `contracts/events/envelope.md` et son JSON Schema
- [x] BF-003-06 — Implémenter un Producer et un Consumer de démonstration (Spring Kafka, sérialisation JSON)
- [x] BF-003-07 — Faire des expériences guidées : clé et partition, deux consommateurs dans un même groupe, deux groupes distincts, reset d'offsets
- [x] BF-003-08 — Configurer le producer pour la fiabilité (`acks=all`, `enable.idempotence=true`)
- [x] BF-003-09 — Configurer le consumer (`enable.auto.commit=false`, mode d'acquittement maîtrisé, `auto.offset.reset`)
- [x] BF-003-10 — Documenter les concepts appris (notes pédagogiques)

### Tests
- [x] Test d'intégration Testcontainers Kafka : un message produit est bien consommé

### Critères de validation
- [x] Les topics sont visibles dans Kafka UI avec la configuration documentée
- [x] La répartition des partitions dans un consumer group est démontrée

---

## BF-004 — API Gateway (routage et sécurité)

**Objectif fonctionnel :** un point d'entrée HTTP unique pour le frontend.
**Objectif pédagogique :** routage, filtres et sécurité transverse avec Spring Cloud Gateway.

### Tâches
- [x] BF-004-01 — Créer le projet `api-gateway` (Spring Cloud Gateway) → squelette créé avec BF-001-05 (`spring-cloud-starter-gateway-server-webmvc`)
- [x] BF-004-02 — Configurer les routes :
  - `/api/auth/**` → Auth Service
  - `/api/customers/**` → Customer Service
  - `/api/accounts/**`, `/api/transfers/**`, `/api/transactions/**` → Banking Service
  - `/api/notifications/**` → Notification Service
  - `/api/analytics/**` → Analytics & Fraud Service
- [x] BF-004-03 — Valider le JWT (clé publique ou JWKS de l'Auth Service) et laisser passer les routes publiques (`/api/auth/register`, `/api/auth/login`, `/api/auth/refresh`)
- [x] BF-004-04 — Propager les informations utiles (en-têtes `X-User-Id`, `X-User-Role`, `X-Correlation-Id`) et supprimer les en-têtes usurpables venant du client
- [x] BF-004-05 — Configurer CORS pour le frontend Angular (utile en développement ; en ligne, le front et `/api` partagent la même adresse, donc pas de CORS)
- [x] BF-004-06 — Uniformiser les erreurs HTTP (format `ProblemDetail`, RFC 9457)
- [x] BF-004-07 — Limiter le débit (rate limiting) sur les routes sensibles (login, register, transfers)
- [x] BF-004-08 — Générer un `correlationId` quand il est absent
- [x] BF-004-09 — Ajouter le service au Docker Compose

### Tests
- [x] Tests de routage, de rejet sans JWT ou avec un JWT invalide ou expiré, de CORS et de rate limiting

### Critères de validation
- [x] Toutes les routes répondent via la Gateway
- [x] Les services internes protègent aussi leurs propres API (la Gateway n'est pas l'unique frontière de sécurité)

---

## BF-005 — Auth Service (authentification et JWT)

**Objectif fonctionnel :** inscription, connexion et gestion des rôles.
**Objectif pédagogique :** Spring Security, JWT asymétrique et hachage des mots de passe.

### Tâches
- [x] BF-005-01 — Migration Flyway : table `users` (`id UUID`, `email` unique, `password_hash`, `role`, `enabled`, `created_at`, `updated_at`)
- [x] BF-005-02 — Entité `User`, repository, DTO, mappers MapStruct
- [x] BF-005-03 — `POST /auth/register` : validation des entrées, unicité de l'email, hachage BCrypt ou Argon2, rôle `ROLE_CLIENT` par défaut
- [x] BF-005-04 — `POST /auth/login` : génération d'un JWT signé RSA ou EC avec une durée de vie courte
- [x] BF-005-05 — `POST /auth/refresh` : décider s'il est retenu (ADR), puis implémenter le refresh token (stockage, rotation, révocation)
- [x] BF-005-06 — `GET /auth/me`
- [x] BF-005-07 — Exposer la clé publique (endpoint JWKS) pour la Gateway et les services
- [x] BF-005-08 — Protéger contre le brute force (compteur d'échecs, verrouillage temporaire ou backoff)
- [x] BF-005-09 — Créer un compte administrateur initial (seed sécurisé via variable d'environnement)
- [x] BF-005-10 — Publier `UserRegistered` sur `bank.users` (clé `userId`), sans mot de passe ni token
- [x] BF-005-11 — Documenter le contrat `contracts/events/UserRegistered.v1.json`
- [x] BF-005-12 — Configurer la sécurité commune des services en Resource Server (validation JWT et extraction du rôle), en dupliquant la configuration par service sans module partagé
- [x] BF-005-13 — Ajouter la documentation OpenAPI/Swagger

### Tests
- [x] Tests unitaires : hachage, génération et validation du JWT, expiration, verrouillage après échecs
- [x] Tests d'intégration : register, login, me, email dupliqué, mot de passe invalide

### Critères de validation
- [x] Inscription et authentification fonctionnelles via la Gateway
- [x] `UserRegistered` est visible dans Kafka UI et ne contient aucune donnée sensible

---

## BF-006 — Customer Service (gestion des clients)

**Objectif fonctionnel :** profils clients créés automatiquement à l'inscription.
**Objectif pédagogique :** premier consommateur métier et référence inter-services sans relation SQL.

### Tâches
- [ ] BF-006-01 — Migration Flyway : table `customers` (`id`, `user_id` unique, `first_name`, `last_name`, `email`, `phone`, `address`, `status`, timestamps)
- [ ] BF-006-02 — Consumer `UserRegistered` qui crée le profil client (idempotent sur `user_id`)
- [ ] BF-006-03 — `GET /customers/me`
- [ ] BF-006-04 — `PUT /customers/me` : modification des coordonnées avec validation
- [ ] BF-006-05 — `GET /customers/{id}` : réservé au propriétaire ou à un administrateur
- [ ] BF-006-06 — `GET /customers` : liste paginée et filtrable, réservée à `ROLE_ADMIN`
- [ ] BF-006-07 — Gérer l'état du profil (`status` : ACTIVE, SUSPENDED…)
- [ ] BF-006-08 — Publier `CustomerCreated` et `CustomerUpdated` sur `bank.customers` (payload minimal)
- [ ] BF-006-09 — Documenter les contrats d'événements
- [ ] BF-006-10 — Ajouter Swagger et l'ajout au Docker Compose

### Tests
- [ ] Tests unitaires : droits d'accès et validations
- [ ] Test d'intégration : `UserRegistered` crée un `Customer`, et le même événement reçu deux fois ne crée pas de doublon

### Critères de validation
- [ ] Une inscription crée automatiquement un profil client
- [ ] Un client ne peut pas consulter le profil d'un autre client

---

## BF-007 — Banking Service : comptes bancaires

**Objectif fonctionnel :** créer, consulter et clôturer des comptes.
**Objectif pédagogique :** modélisation financière (BigDecimal, DECIMAL(19,4)) et contrôle d'appartenance.

### Tâches
- [ ] BF-007-01 — Migrations Flyway : `accounts`, `transfers`, `transactions`, `ledger_entries`, `idempotency_keys`
- [ ] BF-007-02 — Entité `Account` (`customer_id`, `account_number` unique, `currency`, `balance DECIMAL(19,4)`, `status`, `version` pour le verrouillage optimiste)
- [ ] BF-007-03 — Générer un numéro de compte unique (format IBAN fictif)
- [ ] BF-007-04 — Résoudre `customerId` depuis le JWT (`userId` vers `customerId` : appel REST au Customer Service ou projection locale, à décider dans un ADR)
- [ ] BF-007-05 — `POST /accounts` : création avec devise, solde initial à 0 et statut ACTIVE
- [ ] BF-007-06 — `GET /accounts` : seulement les comptes du client, ou tous pour un administrateur
- [ ] BF-007-07 — `GET /accounts/{id}` et `GET /accounts/{id}/balance` avec contrôle d'appartenance
- [ ] BF-007-08 — Clôturer un compte (endpoint ou action dédiée) : interdite si le solde est différent de 0
- [ ] BF-007-09 — Ajouter un value object `Money` (BigDecimal et devise), en interdisant `double` et `float`
- [ ] BF-007-10 — Ajouter un gestionnaire d'erreurs métier (`AccountNotFound`, `AccountClosed`…) au format `ProblemDetail`

### Tests
- [ ] Tests unitaires : création, clôture, règles de statut, `Money`
- [ ] Test d'intégration : isolation des comptes entre deux clients

### Critères de validation
- [ ] Création et consultation des comptes fonctionnelles
- [ ] Accès interdit au compte d'un autre client (403 ou 404)

---

## BF-008 — Banking Service : dépôts et retraits

**Objectif fonctionnel :** créditer et débiter un compte.
**Objectif pédagogique :** écritures comptables immuables, idempotence des commandes et transactions PostgreSQL.

### Tâches
- [ ] BF-008-01 — `POST /accounts/{id}/deposits` : montant strictement positif et compte actif
- [ ] BF-008-02 — `POST /accounts/{id}/withdrawals` : vérification du solde, sans découvert en V1
- [ ] BF-008-03 — Créer une `Transaction` (`type`, `amount`, `balance_after`) et une `LedgerEntry` (DEBIT ou CREDIT) dans la même transaction
- [ ] BF-008-04 — Rendre les écritures immuables (aucun UPDATE ni DELETE, contrainte applicative et éventuellement un trigger SQL)
- [ ] BF-008-05 — Gérer la clé d'idempotence (en-tête `Idempotency-Key`) : stocker la clé, l'empreinte de la requête et la réponse, puis rejouer la réponse identique si la même clé revient
- [ ] BF-008-06 — Verrouiller le compte (`SELECT … FOR UPDATE` ou verrou optimiste) lors de la modification du solde
- [ ] BF-008-07 — `GET /transactions` : historique paginé et filtrable (compte, période, type), limité au propriétaire
- [ ] BF-008-08 — Définir le mécanisme d'écriture compensatrice pour les corrections

### Tests
- [ ] Tests unitaires : montant nul ou négatif, solde insuffisant, compte clôturé, calcul de `balance_after`
- [ ] Test d'intégration : la même clé d'idempotence envoyée deux fois produit une seule opération
- [ ] Test de concurrence : des retraits simultanés ne produisent jamais de solde négatif

### Critères de validation
- [ ] Dépôts et retraits fonctionnels, avec un historique cohérent
- [ ] Le solde du compte est égal à la somme des écritures

---

## BF-009 — Banking Service : virements

**Objectif fonctionnel :** virements internes entre comptes.
**Objectif pédagogique :** atomicité, verrouillage concurrent et prévention des deadlocks.

### Tâches
- [ ] BF-009-01 — `POST /transfers` avec `Idempotency-Key` obligatoire
- [ ] BF-009-02 — Appliquer les règles : comptes existants et actifs, source appartenant au client, montant supérieur à 0, source différente de la destination, même devise, solde suffisant
- [ ] BF-009-03 — Exécuter le débit et le crédit dans une transaction PostgreSQL unique : `Transfer`, 2 `Transaction` et 2 `LedgerEntry` (débit et crédit de même montant)
- [ ] BF-009-04 — Verrouiller les deux comptes dans un ordre déterministe (par exemple tri par UUID) pour éviter les deadlocks
- [ ] BF-009-05 — Gérer les statuts de `Transfer` (PENDING, COMPLETED, REJECTED)
- [ ] BF-009-06 — `GET /transfers/{id}` avec contrôle d'appartenance (source ou destination)
- [ ] BF-009-07 — Ajouter Swagger pour l'ensemble du Banking Service

### Tests
- [ ] Tests unitaires de chaque règle métier
- [ ] Test d'intégration : virement de 500 EUR qui produit un débit de 500 et un crédit de 500, avec des écritures équilibrées
- [ ] Test de concurrence : N virements simultanés sans double débit, sans solde négatif ni deadlock
- [ ] Test : un échec au milieu de la transaction ne laisse aucun effet partiel (rollback)

### Critères de validation
- [ ] La somme des débits est égale à la somme des crédits sur l'ensemble du ledger
- [ ] Le rejeu d'une clé d'idempotence ne produit pas de second virement

---

## BF-010 — Kafka : événements métier

**Objectif fonctionnel :** le Banking Service diffuse ses événements.
**Objectif pédagogique :** contrats d'événements, choix des clés et ordre par partition.

### Tâches
- [ ] BF-010-01 — Définir et versionner les contrats : `AccountCreated`, `AccountClosed`, `TransactionCompleted`, `TransferCompleted`, `DepositCompleted`, `WithdrawalCompleted`
- [ ] BF-010-02 — Fixer le mapping événement → topic : comptes vers `bank.accounts`, virements vers `bank.transfers`, transactions, dépôts et retraits vers `bank.transactions` (à confirmer et documenter)
- [ ] BF-010-03 — Utiliser `accountId` comme clé de partition pour garantir l'ordre par compte
- [ ] BF-010-04 — Garder un payload minimal sans données personnelles inutiles, et ajouter `customerId` lorsque les consommateurs en ont besoin
- [ ] BF-010-05 — Publication directe dans un premier temps (pour illustrer le problème de double écriture qu'Outbox résout)
- [ ] BF-010-06 — Documenter la politique de compatibilité (ajout de champ = compatible ; renommage ou suppression = nouvelle version)

### Tests
- [ ] Test d'intégration : un virement publie `TransferCompleted` avec la bonne clé et la bonne enveloppe

### Critères de validation
- [ ] Les événements sont visibles dans Kafka UI et leurs contrats sont documentés dans `contracts/`

---

## BF-011 — Kafka : Transactional Outbox

**Objectif fonctionnel :** aucun événement perdu ni publié sans transaction validée.
**Objectif pédagogique :** résoudre le problème de double écriture (base de données et Kafka).

### Tâches
- [ ] BF-011-01 — Migration : table `outbox_events` (`id`, `aggregate_type`, `aggregate_id`, `event_type`, `event_version`, `payload JSONB`, `topic`, `message_key`, `status`, `attempts`, `created_at`, `published_at`)
- [ ] BF-011-02 — Écrire dans l'outbox au sein de la même transaction que l'opération métier (remplace la publication directe de BF-010)
- [ ] BF-011-03 — Publisher indépendant (scheduler) : lecture par lots avec `FOR UPDATE SKIP LOCKED`, envoi vers Kafka, marquage `PUBLISHED`
- [ ] BF-011-04 — Reprise après échec : compteur de tentatives, backoff et statut `FAILED` après N tentatives, avec alerte
- [ ] BF-011-05 — Gérer la publication répétée (at-least-once) : `eventId` stable pour que les consommateurs dédupliquent
- [ ] BF-011-06 — Purger ou archiver les événements publiés anciens
- [ ] BF-011-07 — Appliquer le pattern à l'Auth Service (`UserRegistered`) et au Customer Service si pertinent
- [ ] BF-011-08 — Documenter le pattern (schéma de séquence)

### Tests
- [ ] Test d'intégration : un rollback métier n'écrit aucun événement dans l'outbox
- [ ] Test : Kafka indisponible, les événements restent `PENDING` puis sont publiés au retour de Kafka
- [ ] Test : un crash après l'envoi mais avant le marquage provoque une republication avec le même `eventId`

### Critères de validation
- [ ] Aucun événement n'est perdu en cas d'indisponibilité de Kafka

---

## BF-012 — Notification Service (consommateurs et emails)

**Objectif fonctionnel :** le client est notifié de ses opérations par email.
**Objectif pédagogique :** consommateur multi-topics et gestion des échecs d'un système externe.

### Tâches
- [ ] BF-012-01 — Migration : table `notifications` (`customer_id`, `event_id`, `type`, `subject`, `content`, `status`, `attempts`, `created_at`, `sent_at`)
- [ ] BF-012-02 — Consumers : `TransferCompleted` (bank.transfers), `DepositCompleted` et `WithdrawalCompleted` (bank.transactions), `FraudAlertDetected` (bank.fraud-alerts)
- [ ] BF-012-03 — Enregistrer la notification (`PENDING`) avant l'envoi, pour qu'une panne SMTP ne la fasse pas perdre
- [ ] BF-012-04 — Envoyer les emails via Mailpit (Spring Mail) avec des templates (Thymeleaf ou équivalent)
- [ ] BF-012-05 — Retenter l'envoi (job planifié avec backoff, statuts `SENT` et `FAILED`)
- [ ] BF-012-06 — Obtenir l'adresse email du destinataire (projection locale alimentée par `bank.customers` ou appel REST, à décider dans un ADR en limitant les données personnelles)
- [ ] BF-012-07 — `GET /notifications` : historique paginé du client courant
- [ ] BF-012-08 — Ajouter Swagger et l'ajout au Docker Compose

### Tests
- [ ] Tests unitaires : construction du contenu et transitions de statut
- [ ] Test d'intégration : un événement produit un email visible dans Mailpit
- [ ] Test : SMTP coupé, la notification reste `PENDING` puis passe à `SENT` après le retour du SMTP

### Critères de validation
- [ ] Notifications automatiques reçues dans Mailpit pour chaque opération

---

## BF-013 — Kafka : idempotence et DLQ

**Objectif fonctionnel :** traitements fiables, sans doublon ni blocage.
**Objectif pédagogique :** retry, Dead Letter Topics, erreurs temporaires et permanentes, gestion des offsets.

### Tâches
- [ ] BF-013-01 — Créer la table `processed_events` (`event_id`, `consumer_name`, `processed_at`) avec une contrainte unique sur (`event_id`, `consumer_name`) dans chaque service consommateur
- [ ] BF-013-02 — Insérer dans `processed_events` et appliquer l'effet métier dans la même transaction locale
- [ ] BF-013-03 — Classifier les erreurs : temporaires (BDD, SMTP, réseau), qui sont retentées, et permanentes (désérialisation, validation, erreur métier), qui partent directement en DLQ
- [ ] BF-013-04 — Configurer `DefaultErrorHandler` avec un backoff exponentiel et un nombre de tentatives borné, ou des retry topics non bloquants (`@RetryableTopic`)
- [ ] BF-013-05 — Créer les Dead Letter Topics (`<topic>.DLT`) avec des en-têtes de diagnostic
- [ ] BF-013-06 — Gérer les messages illisibles (poison pills) avec `ErrorHandlingDeserializer`
- [ ] BF-013-07 — Valider l'offset seulement après l'effet métier (ack manuel ou `AckMode.RECORD`)
- [ ] BF-013-08 — Documenter une procédure de rejeu de la DLQ (runbook)
- [ ] BF-013-09 — Généraliser à tous les consommateurs (Customer, Notification, Analytics & Fraud)

### Tests
- [ ] Test : un événement reçu deux fois ne produit qu'un seul effet métier
- [ ] Test : un événement invalide part en DLQ sans bloquer la partition
- [ ] Test : une erreur temporaire est retentée puis réussit

### Critères de validation
- [ ] Retry, DLQ et idempotence démontrés pour chaque consommateur

---

## BF-014 — Analytics (statistiques)

**Objectif fonctionnel :** le client suit ses dépenses et revenus.
**Objectif pédagogique :** projections alimentées par événements et cohérence éventuelle.

### Tâches
- [ ] BF-014-01 — Créer le projet `analytics-fraud-service` (modules `analytics` et `fraud` séparés en packages)
- [ ] BF-014-02 — Migration : `daily_statistics` (`customer_id`, `date`, `total_income`, `total_expenses`, `transaction_count`, unique sur `customer_id` et `date`) et projections complémentaires (par compte ou par type)
- [ ] BF-014-03 — Consumers : `TransferCompleted`, `TransactionCompleted`, `DepositCompleted`, `WithdrawalCompleted`, et `AccountCreated` et `CustomerCreated` si nécessaire
- [ ] BF-014-04 — Agréger de façon idempotente (`processed_events`) avec des montants en BigDecimal
- [ ] BF-014-05 — API `/analytics/me/summary` : total des dépenses et revenus, nombre de transactions
- [ ] BF-014-06 — API `/analytics/me/expenses?period=&accountId=` : dépenses par période et par compte
- [ ] BF-014-07 — API `/analytics/me/trends` : évolution et répartition des opérations
- [ ] BF-014-08 — API admin `/analytics/global` : indicateurs globaux
- [ ] BF-014-09 — Ajouter Swagger et l'ajout au Docker Compose

### Tests
- [ ] Tests unitaires des agrégations
- [ ] Test d'intégration : une série d'événements produit les statistiques attendues, et un rejeu ne crée aucun doublon

### Critères de validation
- [ ] Statistiques financières correctes et cohérentes avec le Banking Service

---

## BF-015 — Fraud (détection de fraude)

**Objectif fonctionnel :** détecter et signaler les transactions suspectes.
**Objectif pédagogique :** règles sur fenêtres temporelles et publication d'événements dérivés.

### Tâches
- [ ] BF-015-01 — Migration : `fraud_alerts` (`transaction_id`, `customer_id`, `rule_code`, `severity`, `description`, `status`, `created_at`)
- [ ] BF-015-02 — Définir un moteur de règles extensible (interface `FraudRule`) avec des seuils configurables (`application.yml`)
- [ ] BF-015-03 — Règle `HIGH_AMOUNT` : virement supérieur à 5 000 EUR
- [ ] BF-015-04 — Règle `RAPID_SUCCESSION` : 5 opérations en moins de 2 minutes
- [ ] BF-015-05 — Règle `CUMULATIVE_AMOUNT` : plus de 10 000 EUR sur une période courte
- [ ] BF-015-06 — Stocker l'historique récent par client pour évaluer les fenêtres
- [ ] BF-015-07 — Publier `FraudAlertDetected` sur `bank.fraud-alerts` (via Outbox), sans annuler le virement
- [ ] BF-015-08 — API admin : `GET /analytics/fraud-alerts` (filtres) et `PATCH /analytics/fraud-alerts/{id}` (statut : OPEN, REVIEWED, DISMISSED)
- [ ] BF-015-09 — Ajouter le contrat `FraudAlertDetected.v1.json`

### Tests
- [ ] Tests unitaires de chaque règle (cas aux limites des seuils et des fenêtres)
- [ ] Test d'intégration : un virement de 6 000 EUR crée une alerte, publie l'événement et produit un email d'alerte

### Critères de validation
- [ ] Les transactions suspectes sont détectées et consultables par l'administrateur

---

## BF-016 — Angular : interface utilisateur (socle)

**Objectif fonctionnel :** application web avec authentification et tableau de bord.
**Objectif pédagogique :** architecture Angular, Signals, guards et intercepteurs.

### Tâches
- [x] BF-016-01 — Initialiser le projet Angular 22 (standalone, zoneless), Angular Material et CDK (thème M3), Vitest, angular-eslint et Prettier (ADR-002)
- [x] BF-016-02 — Créer l'arborescence `core/`, `shared/`, `features/` et `app.routes.ts` conformément au cahier des charges
- [x] BF-016-03 — `core/auth` : `AuthService` (login, register, logout, refresh) avec un état basé sur les Signals
- [x] BF-016-04 — Stocker le token de façon sécurisée (choix documenté) et gérer l'expiration
- [x] BF-016-05 — Intercepteur HTTP : ajout du JWT, en-tête `X-Correlation-Id`, refresh automatique
- [x] BF-016-06 — Intercepteur d'erreurs avec gestion centralisée (`MatSnackBar`, 401 qui redirige vers la page de connexion, 403)
- [x] BF-016-07 — Guards : `authGuard` et `adminGuard` (rôle)
- [x] BF-016-08 — Écrans de connexion et d'inscription (formulaires réactifs et validations)
- [x] BF-016-09 — Layout (menu client et admin, en-tête, déconnexion)
- [x] BF-016-10 — Dashboard réduit pour la MVP 1 : informations du compte (`/auth/me`) et sections « bientôt disponible », sans donnée fictive
- [ ] BF-016-10b — Dashboard complet (MVP 2) : synthèse des comptes, dernières opérations et notifications récentes
- [ ] BF-016-11 — Écran de profil (`/customers/me`) (reporté à la MVP 2, avec BF-006)
- [x] BF-016-12 — `shared/models` (interfaces TypeScript)
- [ ] BF-016-12b — `shared/pipes` (montant ou devise, date) (reporté à la MVP 2, avec les premiers montants affichés)
- [x] BF-016-13 — Configurer les environnements (URL de la Gateway)
- [x] BF-016-14 — Écrire le Dockerfile (build et Nginx) et l'ajouter au Docker Compose

### Tests
- [x] Tests Vitest : AuthService, intercepteurs, guards, formulaires de connexion et d'inscription

### Critères de validation
- [x] Parcours complet inscription → connexion → dashboard → déconnexion
- [ ] Routes admin inaccessibles à un client (et refusées aussi côté backend)

---

## BF-017 — Angular : comptes et virements

**Objectif fonctionnel :** gérer ses comptes et effectuer des virements depuis l'interface.

### Tâches
- [ ] BF-017-01 — Écran « Mes comptes » : liste, solde et création d'un compte
- [ ] BF-017-02 — Écran « Détail compte » : solde, historique paginé, dépôt, retrait et clôture
- [ ] BF-017-03 — Écran « Nouveau virement » : formulaire (source, destination, montant, description), validations et confirmation
- [ ] BF-017-04 — Générer une `Idempotency-Key` (UUID) par soumission et empêcher le double clic
- [ ] BF-017-05 — Écran « Mes transactions » : historique filtrable (compte, période, type)
- [ ] BF-017-06 — Afficher les erreurs métier (solde insuffisant, devise différente, compte clôturé)
- [ ] BF-017-07 — Pipe de formatage des montants sans erreur d'arrondi (montants transmis en chaîne ou décimal)

### Tests
- [ ] Tests Vitest : services comptes et virements, formulaire de virement, gestion des erreurs

### Critères de validation
- [ ] Création de compte, dépôt, retrait, virement et consultation de l'historique de bout en bout

---

## BF-018 — Angular : notifications, statistiques et administration

### Tâches
- [ ] BF-018-01 — Écran « Notifications » : historique paginé
- [ ] BF-018-02 — Écran « Statistiques » : graphiques des dépenses et revenus par période et par compte, et répartition (Chart.js / ng2-charts)
- [ ] BF-018-03 — Écran admin « Clients » : liste paginée et filtrable, détail d'un client et de ses comptes
- [ ] BF-018-04 — Écran admin « Alertes de fraude » : liste, filtres, changement de statut
- [ ] BF-018-05 — Écran admin « Indicateurs globaux » : volume de transactions, nombre de clients, alertes
- [ ] BF-018-06 — Écran admin « Transactions » : surveillance des transactions

### Tests
- [ ] Tests Vitest des composants et services associés

### Critères de validation
- [ ] Interface d'administration fonctionnelle et réservée à `ROLE_ADMIN`

---

## BF-019 — Fiabilité : tests de résilience

**Objectif pédagogique :** prouver la robustesse du système face aux pannes.

### Tâches (scénarios à automatiser ou à documenter)
- [ ] BF-019-01 — Kafka indisponible : les opérations bancaires continuent, l'outbox s'accumule puis est publiée
- [ ] BF-019-02 — Un consommateur s'arrête puis redémarre : il reprend à l'offset validé, sans perte
- [ ] BF-019-03 — Un événement est reçu plusieurs fois : un seul effet métier
- [ ] BF-019-04 — Un événement est invalide : il part en DLQ et la partition n'est pas bloquée
- [ ] BF-019-05 — PostgreSQL indisponible : erreurs propres, retry côté consommateurs, aucune corruption
- [ ] BF-019-06 — Un service redémarre après une transaction : l'état reste cohérent et l'outbox est reprise
- [ ] BF-019-07 — La publication Outbox échoue puis reprend
- [ ] BF-019-08 — SMTP indisponible : aucune notification perdue
- [ ] BF-019-09 — Tests financiers : équilibre global du ledger, absence de double débit et de double crédit
- [ ] BF-019-10 — Tests de virements simultanés en charge et de clés d'idempotence concurrentes
- [ ] BF-019-11 — Vérifier l'absence de solde négatif sous concurrence
- [ ] BF-019-12 — Écrire le rapport de résilience (`docs/resilience.md`) : scénario, résultat attendu, résultat obtenu

### Critères de validation
- [ ] Tous les scénarios de la section 11.3 du cahier des charges sont passés et documentés

---

## BF-020 — Monitoring : Prometheus et Grafana

### Tâches
- [ ] BF-020-01 — Actuator et Micrometer : exposer `/actuator/prometheus` (accès restreint)
- [ ] BF-020-02 — Ajouter les health indicators PostgreSQL et Kafka
- [ ] BF-020-03 — Définir les métriques métier : virements (nombre, montants, échecs), outbox en attente, messages DLQ, notifications en échec, alertes de fraude
- [ ] BF-020-04 — Exposer le lag des consumer groups (kafka-exporter ou métriques client)
- [ ] BF-020-05 — Ajouter Prometheus au Docker Compose (scrape config)
- [ ] BF-020-06 — Ajouter Grafana avec provisioning : dashboards « Santé des services », « Activité Kafka », « Transactions », « Erreurs et DLQ »
- [ ] BF-020-07 — Mettre en place des logs structurés JSON (date, niveau, service, `correlationId`, `transactionId`)
- [ ] BF-020-08 — Propager le `correlationId` en HTTP et dans les en-têtes Kafka (MDC)
- [ ] BF-020-09 — Masquer les données sensibles dans les logs (tokens, mots de passe, données bancaires)
- [ ] BF-020-10 — (Optionnel) Ajouter des règles d'alerte Prometheus (lag élevé, DLQ non vide, outbox bloquée)

### Critères de validation
- [ ] Les dashboards Grafana affichent la santé, l'activité Kafka, les transactions et les erreurs

---

## BF-021 — Architecture : Saga Pattern

**Objectif pédagogique :** coordonner un processus traversant plusieurs services.

### Tâches
- [ ] BF-021-01 — Étudier chorégraphie et orchestration, puis rédiger un ADR
- [ ] BF-021-02 — Cas d'usage : initialisation client (`UserRegistered` → `CustomerCreated` → ouverture automatique d'un compte → `AccountCreated`)
- [ ] BF-021-03 — Définir les états de la saga et sa persistance
- [ ] BF-021-04 — Implémenter les actions de compensation (par exemple désactiver le profil ou l'utilisateur si l'ouverture de compte échoue)
- [ ] BF-021-05 — Gérer les timeouts et les sagas bloquées
- [ ] BF-021-06 — Ajouter les événements supplémentaires et documenter leurs contrats
- [ ] BF-021-07 — Tests : chemin nominal, échec avec compensation, événements dupliqués

### Critères de validation
- [ ] La saga démontre un chemin nominal et une compensation
- [ ] Rappel du cahier des charges : un virement interne n'utilise pas de saga

---

## BF-022 — Optimisation : Kafka Streams

**Objectif pédagogique :** traitement de flux en temps réel.

### Tâches
- [ ] BF-022-01 — Introduire Kafka Streams dans l'Analytics & Fraud Service
- [ ] BF-022-02 — Réécrire la règle `RAPID_SUCCESSION` avec des fenêtres temporelles (windowed count par client)
- [ ] BF-022-03 — Réécrire la règle `CUMULATIVE_AMOUNT` avec une agrégation fenêtrée
- [ ] BF-022-04 — (Optionnel) Calculer des statistiques temps réel via KTable et state stores
- [ ] BF-022-05 — Utiliser `TopologyTestDriver` pour les tests
- [ ] BF-022-06 — Comparer avec l'approche consumer classique (documentation)

### Critères de validation
- [ ] Au moins une règle de fraude fonctionne via Kafka Streams et est testée

---

## BF-023 — Finalisation : documentation et CI/CD

### Tâches — CI/CD (GitHub Actions)
- [x] BF-023-01 — Compilation Maven de chaque service (matrice) → `.github/workflows/ci.yml`, mis en place avec BF-001-10
- [x] BF-023-02 — Exécution des tests backend (unitaires et Testcontainers) → `./mvnw verify` dans `.github/workflows/ci.yml`, mis en place avec BF-001-10
- [x] BF-023-03 — Compilation Angular
- [x] BF-023-04 — Exécution des tests frontend (Vitest)
- [x] BF-023-05 — Construction des images Docker (à avancer au jalon MVP 1)
- [x] BF-023-06 — Publication des images (GHCR) sur la branche principale ou sur les tags (à avancer au jalon MVP 1)
- [x] BF-023-07 — Rapports de couverture (JaCoCo, Vitest), cache Maven/npm et analyse SonarQube Cloud → `docs/runbooks/sonarqube.md`

### Tâches — Documentation
- [ ] BF-023-08 — Swagger/OpenAPI de chaque service, avec agrégation éventuelle via la Gateway
- [ ] BF-023-09 — Documentation d'architecture : schémas (C4 ou équivalent), flux d'événements, séquences (virement, Outbox, saga)
- [ ] BF-023-10 — Catalogue des événements (`contracts/`) et des topics
- [ ] BF-023-11 — ADR consolidés
- [ ] BF-023-12 — Guide de démarrage et runbooks (DLQ, outbox bloquée, reset d'offsets)
- [ ] BF-023-13 — Environnement « production simulée » : secrets externalisés, Kafka multi-broker et répliqué (étude), sauvegardes PostgreSQL
- [ ] BF-023-14 — (Étude) Sécurité Kafka renforcée : TLS, SASL, ACL, comptes techniques distincts
- [ ] BF-023-15 — (Étude) Avro et Schema Registry

### Tâches — Déploiement sur le serveur maison (à avancer au jalon MVP 1)
- [ ] BF-023-16 — Déployer `main` sur le serveur maison (`https://bankflow.anthonypereira.dpdns.org`) :
  - vérifier que le dépôt GitHub est **privé** (obligatoire avant de brancher un runner auto-hébergé). Au 2026-11-17, le dépôt est **public** : le passer en privé (Settings → General → Danger Zone → Change visibility) ;
  - écrire un `docker-compose.yml` de déploiement : seuls le front (port 80) et l'api-gateway (`/api`) sont routés par Traefik (réseau `web`) ; services, PostgreSQL et Kafka restent sur un réseau interne ; Kafka UI, Mailpit, Prometheus et Grafana ne sont jamais exposés ;
  - limiter la mémoire de chaque JVM (ex. `-Xmx256m`) et ne déployer que les services développés (le serveur a 8 Go de RAM) ;
  - écrire `.github/workflows/deploy.yml` : build et publication des images GHCR sur `ubuntu-latest` (matrice front et services), puis `deploy-app` sur le runner `[self-hosted, serveur-maison]`, déclenché uniquement sur `push` vers `main` et à la main (jamais sur `pull_request`) ;
  - configurer le profil `prod-sim` (BF-002-08) ; les secrets vivent dans `~/apps/bankflow/.env` sur le serveur, jamais dans Git ;
  - sur le serveur : retirer le sudo temporaire (`/etc/sudoers.d/claude-temp`), puis `ajouter-runner AnthonyPereira/bankflow <jeton>` ;
  - documenter le déploiement et le retour arrière dans `docs/runbooks/`.

### Critères de validation — Déploiement
- [ ] Un `git push` sur `main` met l'application à jour en ligne en moins de 5 minutes
- [ ] Seuls le front et `/api` répondent depuis Internet

---

## Exigences transverses (à vérifier à chaque lot)

- [ ] Montants en `BigDecimal` / `DECIMAL(19,4)`, jamais `double` ni `float`
- [ ] Aucun accès d'un service à la base d'un autre
- [ ] Aucun mot de passe ni token dans Kafka, et payloads minimaux
- [ ] Aucun secret dans Git, et aucun token ni donnée bancaire dans les logs
- [ ] Contrôle d'appartenance côté backend sur toutes les ressources client
- [ ] Validation des entrées (Spring Validation) et erreurs au format `ProblemDetail`
- [ ] Migrations Flyway pour tout changement de schéma
- [ ] Tests unitaires des règles métier et Swagger à jour
- [ ] Kafka utilisé uniquement pour des événements métier pertinents (les consultations restent en REST)

---

## Checklist de validation finale (section 15 du cahier des charges)

**Architecture**
- [ ] Tous les microservices sont indépendants
- [ ] Chaque service possède sa base
- [ ] Les communications REST et Kafka sont fonctionnelles
- [ ] L'API Gateway est opérationnelle

**Fonctionnalités bancaires**
- [ ] Inscription et authentification
- [ ] Création et consultation des comptes
- [ ] Dépôts, retraits et virements
- [ ] Historique des transactions
- [ ] Isolation des données clients

**Kafka**
- [ ] Producteurs et consommateurs fonctionnels
- [ ] Topics, partitions et Consumer Groups configurés
- [ ] Gestion des offsets
- [ ] Retry et DLQ
- [ ] Idempotence
- [ ] Transactional Outbox
- [ ] Reprise après indisponibilité
- [ ] Introduction à Kafka Streams

**Fonctionnalités complémentaires**
- [ ] Notifications automatiques
- [ ] Détection des transactions suspectes
- [ ] Statistiques financières
- [ ] Interface d'administration

**Qualité et exploitation**
- [ ] Tests unitaires et tests d'intégration ciblés
- [ ] Tests de résilience
- [ ] Documentation Swagger et technique
- [ ] Monitoring
- [ ] Infrastructure Docker reproductible
- [ ] Pipeline CI/CD

---

## Hors périmètre V1 (pour mémoire)
Connexion à de vraies banques ou paiements SEPA réels · cartes bancaires et crédits réels · KYC et conformité réglementaire · Kubernetes, Service Mesh, multi-région · Event Sourcing complet.

## Décisions à prendre (ADR)
1. ~~Versions et stack backend et frontend (BF-001)~~ → ADR-001 et ADR-002 (acceptés le 2026-11-16)
2. ~~Refresh token retenu ou non, et stratégie de stockage du token côté frontend (BF-005, BF-016)~~ → ADR-003 (accepté le 2026-11-18)
3. Résolution `userId` → `customerId` dans le Banking Service (BF-007)
4. Mapping événement → topic pour `DepositCompleted` et `WithdrawalCompleted` (BF-010)
5. Source de l'email du destinataire dans le Notification Service (BF-012)
6. Retry bloquant ou non bloquant (`@RetryableTopic`) (BF-013)
7. Saga : chorégraphie ou orchestration (BF-021)
