# Conventions BankFlow

## 1. Branches : Gitflow

Le dépôt suit le modèle **Gitflow** ([Atlassian](https://www.atlassian.com/git/tutorials/comparing-workflows/gitflow-workflow)).

### Branches permanentes

| Branche | Rôle |
|---|---|
| `main` | Historique des **versions publiées**. Chaque commit sur `main` correspond à une version et porte un tag. Aucun commit direct. |
| `develop` | **Branche d'intégration** : elle reçoit les fonctionnalités terminées. C'est la branche par défaut des Pull Requests. |

### Branches temporaires

| Type | Part de | Fusionne dans | Nommage | Usage |
|---|---|---|---|---|
| Feature | `develop` | `develop` | `feature/BF-XXX-description-courte` | Une tâche ou un lot du backlog (`feature/BF-005-jwt-login`). Ne touche jamais `main` directement. |
| Release | `develop` | `main` **et** `develop` | `release/X.Y.0` | Préparation d'une version. On n'y fait que des corrections, de la documentation et la montée de version, aucune nouvelle fonctionnalité. |
| Hotfix | `main` | `main` **et** `develop` (ou la `release/` en cours) | `hotfix/X.Y.Z` | Correction urgente d'une version publiée. |

### Règles

- Les fusions se font **sans fast-forward** (`git merge --no-ff`), pour garder la trace de chaque branche dans l'historique.
- Chaque fusion dans `main` est **taguée** `vX.Y.Z` (tag annoté) :
  - en [SemVer](https://semver.org/lang/fr/) : MINEUR pour une release, CORRECTIF pour un hotfix ;
  - en `0.x` tant que la V1 n'est pas complète.
- Une branche feature, release ou hotfix est supprimée (en local et sur le dépôt distant) une fois fusionnée.
- Il faut tenir sa branche feature à jour avec `develop` (`git merge develop`) avant d'ouvrir la PR.

### Cycle d'une fonctionnalité

```bash
git checkout develop && git pull
git checkout -b feature/BF-XXX-description
# ... commits ...
git push -u origin feature/BF-XXX-description
# Pull Request vers develop, revue, CI au vert, merge (--no-ff)
```

### Cycle d'une release

```bash
git checkout -b release/0.1.0 develop
# corrections, mise à jour des versions (pom.xml, package.json), CHANGELOG
git checkout main && git merge --no-ff release/0.1.0
git tag -a v0.1.0 -m "BankFlow 0.1.0"
git checkout develop && git merge --no-ff release/0.1.0
git branch -d release/0.1.0
git push origin main develop --tags
```

### Cycle d'un hotfix

```bash
git checkout -b hotfix/0.1.1 main
# correction et montée de version
git checkout main && git merge --no-ff hotfix/0.1.1 && git tag -a v0.1.1 -m "BankFlow 0.1.1"
git checkout develop && git merge --no-ff hotfix/0.1.1
git branch -d hotfix/0.1.1
git push origin main develop --tags
```

### Jalons de release proposés

| Version | Contenu (lots) |
|---|---|
| `0.1.0` | Socle : BF-001 à BF-005 (dépôt, infrastructure, Kafka, Gateway, Auth) |
| `0.2.0` | Cœur bancaire : BF-006 à BF-009 |
| `0.3.0` | Événements fiables : BF-010 à BF-013 |
| `0.4.0` | Analytics et fraude : BF-014, BF-015 |
| `0.5.0` | Frontend : BF-016 à BF-018 |
| `0.6.0` | Fiabilité et monitoring : BF-019, BF-020 |
| `1.0.0` | Saga, Kafka Streams, documentation et CI/CD : BF-021 à BF-023, et validation finale |

## 2. Messages de commit : Conventional Commits

```
<type>(<portée>): <description à l'impératif, en minuscule>

[corps facultatif : le pourquoi]

Refs: BF-XXX
```

**Types :** `feat`, `fix`, `docs`, `test`, `refactor`, `perf`, `build`, `ci`, `chore`.

**Portées :** `gateway`, `auth`, `customer`, `banking`, `notification`, `analytics`, `frontend`, `infra`, `contracts`, `docs`.

Exemples :
```
feat(banking): ajoute les virements internes avec verrouillage ordonné
fix(notification): conserve la notification en cas de panne SMTP
docs(adr): ajoute ADR-003 sur le refresh token
```

Un changement incompatible (d'API ou de contrat d'événement) est signalé par `!` (`feat(contracts)!: …`) avec une ligne `BREAKING CHANGE:` dans le corps.

## 3. Pull Requests

- Une PR `feature/*` cible **`develop`**. Seules les PR `release/*` et `hotfix/*` ciblent `main` (puis sont reportées dans `develop`).
- Le merge se fait en **merge commit** (pas de squash ni de rebase) pour respecter le `--no-ff` de Gitflow.
- Une PR correspond à une tâche ou un lot cohérent du backlog, et référence `BF-XXX`.
- La CI doit être au vert (build, tests, lint).
- La checklist des **exigences transverses** du backlog doit être vérifiée.
- Le backlog est mis à jour (cases cochées).

## 4. ADR (décisions d'architecture)

- Emplacement : `docs/adr/ADR-NNN-titre-court.md`
- Statuts : `Proposé` → `Accepté` / `Rejeté` → `Remplacé par ADR-XXX`
- Un ADR accepté n'est pas réécrit : on en crée un nouveau qui le remplace.

## 5. Code

- **Java** : packages `api`, `application`, `domain`, `infrastructure` ; montants en `BigDecimal` uniquement.
- **Formatage Java** : Spotless avec google-java-format, style AOSP (4 espaces, 100 colonnes). `./mvnw verify` échoue si le code n'est pas formaté, et `./mvnw spotless:apply` corrige automatiquement.
- **Angular** : `@Service()` avec `inject()`, composants standalone, formatage ESLint et Prettier.
- **Encodage et fins de ligne** : UTF-8 et LF (voir `.editorconfig` et `.gitattributes`).

## 6. Secrets

- Ne **jamais** commiter de `.env`, de clé privée ou de mot de passe réel.
- `.env.example` documente les variables attendues, avec des valeurs factices.
