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
- `frontend/bankflow-web/` : Angular (`core/`, `features/`, `shared/`).
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
- **Déclenchement** : une fusion dans `main` lance `.github/workflows/deploy.yml`. Il publie les images sur GHCR puis envoie un signal `deployer` au dépôt **privé** `AnthonyPereira/deploy-platform`, qui porte le runner auto-hébergé `serveur-maison-deploy-platform`.
- **Runner** : il ne doit **jamais** être enregistré sur `bankflow`, car le dépôt est public.
- **Procédure** : [docs/runbooks/deploiement.md](docs/runbooks/deploiement.md).

## État d'avancement (2026-11-20)

**MVP 1 publiée en `0.1.0`** (tag `v0.1.0`, fusionnée dans `main` le 2026-11-20, voir [CHANGELOG](CHANGELOG.md)) : inscription, connexion et tableau de bord. Les lots BF-002 à BF-005 et BF-016 sont terminés.

Les dernières étapes, toutes fusionnées dans `develop` avec une CI verte :
- **#35** : Spring Boot 4.2.0 et Spring Cloud 2026.0.0 en GA (BF-001-01 terminé).
- **#36** : packages rangés par fonctionnalité dans chaque couche.
- **#37** : comptes de démonstration sur la page de connexion (BF-016-15). Ils sont créés au démarrage de l'Auth Service, servis par `GET /auth/demo-accounts` et désactivables avec `DEMO_ACCOUNTS_ENABLED=false`. Les mots de passe sont publics : `demo.client@bankflow.local` / `demo-client-bankflow` et `demo.admin@bankflow.local` / `demo-admin-bankflow`. Le vrai compte admin (`ADMIN_PASSWORD`) reste secret.

Les secrets `DEPLOY_DISPATCH_TOKEN` (bankflow) et `GHCR_PULL_TOKEN` (deploy-platform) sont en place, et le runner est en ligne.

### Prochaines étapes

1. **Après la release `0.1.0`** : vérifier en ligne que seuls le front et `/api` répondent, puis cocher BF-023-16.
2. **Option proposée** : exclure les comptes de démo du verrouillage après 5 échecs de connexion, puisque n'importe qui peut aujourd'hui les bloquer 15 minutes.
3. **MVP 2 (`0.2.0`)** : BF-006 (Customer Service), puis BF-007 à BF-009 (comptes, dépôts et retraits, virements), puis BF-017 (écrans Angular).
