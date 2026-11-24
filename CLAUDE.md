# CLAUDE.md — BankFlow

Repères pour reprendre le projet d'une session à l'autre. La source de vérité reste le [BACKLOG](BACKLOG.md) (statut de chaque tâche) et les [conventions](CONTRIBUTING.md). Ce fichier résume l'essentiel et **l'état d'avancement**, à mettre à jour à chaque étape importante.

## Projet

Banque fictive pédagogique en microservices : 6 services Spring Boot indépendants, un front Angular, Kafka et PostgreSQL. Aucune donnée réelle. Le dépôt GitHub `AnthonyPereira/bankflow` est **public** et doit le rester.

| Élément | Version / choix |
|---|---|
| Backend | Java 27, Spring Boot 4.2.0, Spring Cloud 2026.0.0 (Gateway), Maven wrapper par service ([ADR-001](docs/adr/ADR-001-stack-backend.md)) |
| Frontend | Angular 22, Angular Material, Vitest ([ADR-002](docs/adr/ADR-002-stack-frontend.md)) |
| Session | JWT court en mémoire et refresh token en cookie HttpOnly, avec rotation ([ADR-003](docs/adr/ADR-003-sessions-refresh-token.md)) |
| Qualité | Spotless (google-java-format AOSP), ESLint et Prettier, SonarQube/SonarCloud, CI GitHub Actions |

## Arborescence

- `services/<service>/` : un projet Maven autonome par service. **Aucune classe partagée** entre services.
- `frontend/bankflow-web/` : Angular (`core/` dont `core/auth` avec les modèles d'auth, `features/`, `testing/`).
- `infra/` : Dockerfile Spring commun, config Kafka et PostgreSQL. `docker-compose.yml` est à la racine.
- `docs/` : ADR, architecture, runbooks (`deploiement.md`, `sonarqube.md`).
- `contracts/` : contrats d'événements Kafka.

**Packages Java** : quatre couches (`api`, `application`, `domain`, `infrastructure`). Dans chaque couche, un sous-package par fonctionnalité (`application/login`, `domain/user`, `infrastructure/security/jwt`). Dans une fonctionnalité, seuls les contrôleurs, services, entités et dépôts restent à la racine : les records et exceptions vont dans `dto/`, `validation/`, `model/`, `config/`, `event/` ou `exception/`, et les `ApiExceptionHandler` dans `api/error/`. La Gateway se range en `filter/`, `error/` et `security/`. Le détail est dans [services/README.md](services/README.md).

## Commandes

```bash
# Backend : depuis services/<service>/ (JDK 27 requis, Docker lancé pour Testcontainers)
./mvnw spotless:apply verify

# Frontend : depuis frontend/bankflow-web/
npx prettier --write src && npm run lint && npx ng test --watch=false && npm run build

# Tout lancer en local (le .env est créé depuis .env.example et n'est jamais commité)
docker compose --profile apps up -d --build
docker compose --profile apps down
```

En local : application sur http://localhost:8088, Gateway sur http://localhost:8080/api, Kafka UI sur http://localhost:8090, Mailpit sur http://localhost:8025.

Profils Spring : `dev` (défaut), `prod-sim` (serveur maison) et `test` (tests automatisés).

## Façon de travailler

- **Gitflow** : une branche `feature/BF-XXX-description` par sujet, une PR vers `develop`, fusion en merge commit (`gh pr merge --merge`) **dès que la CI est verte**, sans attendre de validation. Seules les branches `release/*` et `hotfix/*` vont vers `main`.
- **Commits** : Conventional Commits en français, par exemple `feat(auth): …`, `refactor: …` ou `build: …`.
- **Actions externes** (passer le dépôt en privé, toucher au serveur maison, créer une release) : seulement à la demande explicite de l'utilisateur.
- **Après chaque tâche** : cocher le BACKLOG et mettre à jour la doc concernée (ADR, README).

## Pièges connus

- **Fins de ligne** : tout le dépôt est en LF (`.gitattributes`), sauf les `*.cmd`, `*.bat` et `*.ps1`, qui sont en CRLF. Un script Python sous Windows écrit en CRLF par défaut : il faut passer `newline="\n"`, sinon Spotless échoue. Il ne faut jamais convertir les `mvnw.cmd`.
- **Ports Windows** : le 5432, et parfois le 5433, sont pris sur le poste. En local, PostgreSQL est publié sur `POSTGRES_HOST_PORT=55432`.
- **Docker** doit être démarré avant `mvn verify`, sinon les tests Testcontainers échouent avec « Could not find a valid Docker environment ».
- Dans une boucle shell, `mvn … | tail` renvoie le code de retour de `tail`, pas celui de Maven. Il faut tester le code de retour de Maven lui-même.
- **CI** : un échec « artifact absent » sur Maven Central se règle en relançant le job (`gh run rerun <id> --failed`).

## Mise en ligne

- **Cible** : `https://bankflow.anthonypereira.dpdns.org`, sur le serveur maison (Traefik et tunnel Cloudflare).
- **Déclenchement** : une fusion dans `main` lance la CI et `.github/workflows/deploy.yml`, qui attend une CI verte puis publie les images sur GHCR. Le dépôt **privé** `AnthonyPereira/deploy-platform` interroge l'API toutes les 5 minutes depuis son runner auto-hébergé `serveur-maison-deploy-platform`, et met en ligne la dernière version publiée. bankflow ne détient aucun jeton vers deploy-platform.
- **Runner** : il ne doit **jamais** être enregistré sur `bankflow`, car le dépôt est public.
- **Procédure** : [docs/runbooks/deploiement.md](docs/runbooks/deploiement.md).

## État d'avancement (2026-11-24)

**MVP 2 en ligne en `0.2.1`** sur `https://bankflow.anthonypereira.dpdns.org` depuis le 2026-11-24 (voir [CHANGELOG](CHANGELOG.md)) : clients, comptes, dépôts, retraits et virements, en plus de la MVP 1. Le jalon MVP 2 du [BACKLOG](BACKLOG.md) est terminé, BF-023-17 compris.
- `0.1.0` (tag `v0.1.0`, #39) : contenu de la MVP 1, après trois revues de code complètes. Elle n'a jamais été mise en ligne, car le build des images Spring échouait.
- `0.1.1` (tag `v0.1.1`, hotfix #51) : `unzip` installé dans l'image de build, pour que le wrapper Maven vérifie la bonne archive.
- `0.2.0` (tag `v0.2.0`, #77) : MVP 2, avec les [ADR-005 à 007](docs/adr/). En ligne, aucun client n'avait de fiche : sur le serveur, la première lecture des métadonnées de Kafka dépassait `max.block.ms` (3 s) et le rejeu de `UserRegistered` s'arrêtait après 0 utilisateur.
- `0.2.1` (tag `v0.2.1`, hotfix #102) : le rejeu retente un envoi en échec pendant environ deux minutes. `max.block.ms` reste à 3 s pour ne pas bloquer une inscription.
- Décisions clés de la MVP 2 : l'administrateur agit en **conseiller au nom des clients** sans jamais avoir de compte à son nom ni de fiche client ; plafond de 10 000,00 par opération et au plus 5 comptes actifs par client ; `demo.client` reçoit des comptes et un historique recréés s'ils disparaissent ; `demo.admin` est restreint aux clients de démonstration par le claim `demo` ; la Gateway limite les opérations à 60 par minute et par IP, et les virements à 30.
- Comptes de démonstration (BF-016-15) : créés au démarrage de l'Auth Service, servis par `GET /auth/demo-accounts`. Ils suivent les mêmes règles que les autres comptes (verrouillage temporaire compris, `demo.admin` est un vrai administrateur), mais ne peuvent jamais être désactivés définitivement. `DEMO_ACCOUNTS_ENABLED=false` masque le tableau et ne les crée plus, sans les désactiver. Les mots de passe sont publics : `demo.client@bankflow.local` / `demo-client-bankflow` et `demo.admin@bankflow.local` / `demo-admin-bankflow`. Le vrai compte admin (`ADMIN_EMAIL` / `ADMIN_PASSWORD`, dans `~/apps/bankflow/.env` sur le serveur) reste secret.
- Déploiement : `deploy-platform` cherche lui-même les nouvelles versions (cron toutes les 5 min, `actions/github-script`, sans `gh`). L'état est gardé dans `~/apps/bankflow/.version` et `.derniere`. Seul `GHCR_PULL_TOKEN` (deploy-platform) est nécessaire.
- Protection de `main` : `require_last_push_approval` est désactivé (dépôt à une seule personne).

### Prochaines étapes

1. Suivis : BF-008-11 (purge des clés d'idempotence) et BF-009-10 (limite de débit des virements par utilisateur, en plus de la limite par IP).
2. MVP 3 : à cadrer avec l'utilisateur (voir le [BACKLOG](BACKLOG.md)).
3. La CI ne se lance pas sur les PR vers `release/*` : il faut la lancer à la main (`gh workflow run ci.yml --ref <branche>`) avant de fusionner.
4. Le run planifié de `deploy-platform` peut échouer sur une panne réseau du runner (téléchargement de `actions/checkout`) : relancer `Déploiement` à la main.
