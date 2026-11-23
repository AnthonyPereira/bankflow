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

**Packages Java** : quatre couches (`api`, `application`, `domain`, `infrastructure`). Dans chaque couche, un seul niveau de sous-packages par fonctionnalité, par exemple `application/login`, `domain/user` ou `infrastructure/security/jwt`. La Gateway se range en `filter/`, `error/` et `security/`. Le détail est dans [services/README.md](services/README.md).

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

## État d'avancement (2026-11-23)

**MVP 1 en ligne en `0.1.1`** sur `https://bankflow.anthonypereira.dpdns.org`, avec les comptes de démonstration (voir [CHANGELOG](CHANGELOG.md)) : inscription, connexion et tableau de bord. Les lots BF-002 à BF-005, BF-016 et BF-023-16 sont terminés.
- `0.1.0` (tag `v0.1.0`, #39) : contenu de la MVP 1, après trois revues de code complètes. Elle n'a jamais été mise en ligne, car le build des images Spring échouait.
- `0.1.1` (tag `v0.1.1`, hotfix #51) : `unzip` installé dans l'image de build, pour que le wrapper Maven vérifie la bonne archive.
- Comptes de démonstration (BF-016-15) : créés au démarrage de l'Auth Service, servis par `GET /auth/demo-accounts`. Ils suivent les mêmes règles que les autres comptes (verrouillage temporaire compris, `demo.admin` est un vrai administrateur), mais ne peuvent jamais être désactivés définitivement. `DEMO_ACCOUNTS_ENABLED=false` masque le tableau et ne les crée plus, sans les désactiver. Les mots de passe sont publics : `demo.client@bankflow.local` / `demo-client-bankflow` et `demo.admin@bankflow.local` / `demo-admin-bankflow`. Le vrai compte admin (`ADMIN_EMAIL` / `ADMIN_PASSWORD`, dans `~/apps/bankflow/.env` sur le serveur) reste secret.
- Déploiement : `deploy-platform` cherche lui-même les nouvelles versions (cron toutes les 5 min, `actions/github-script`, sans `gh`). L'état est gardé dans `~/apps/bankflow/.version` et `.derniere`. Seul `GHCR_PULL_TOKEN` (deploy-platform) est nécessaire.
- Protection de `main` : `require_last_push_approval` est désactivé (dépôt à une seule personne).

### Prochaines étapes

**MVP 2 (`0.2.0`)** : le contenu est listé dans le jalon du [BACKLOG](BACKLOG.md#mise-en-ligne-sur-le-serveur-maison).
1. ADR-004 (résolution du `customerId`), puis BF-006, rattrapage des profils compris (BF-006-11).
2. BF-007, BF-008 et BF-009, avec les données de démonstration (BF-009-08).
3. BF-017 et le reste de BF-016 (10b, 11, 12b). Les écrans des comptes peuvent démarrer dès que BF-007 est fusionné.
4. BF-023-17 (mise en ligne de customer-service et banking-service), puis `release/0.2.0` avec une revue de code complète.
