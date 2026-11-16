# Conventions BankFlow

## 1. Branches

| Branche | Rôle |
|---|---|
| `main` | Toujours stable : compile et les tests passent. Aucun commit direct dès que la CI existe. |
| `feature/BF-XXX-description-courte` | Développement d'un lot ou d'une tâche (`feature/BF-005-jwt-login`) |
| `fix/BF-XXX-description-courte` | Correction d'un bug |
| `docs/description-courte` | Documentation seule |
| `chore/description-courte` | Outillage, dépendances, CI |

Le modèle est le *trunk-based* simplifié : des branches courtes, fusionnées dans `main` par Pull Request (squash merge).

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

- Une PR correspond à une tâche ou un lot cohérent du backlog, et référence `BF-XXX`.
- La CI doit être au vert (build, tests, lint).
- La checklist des **exigences transverses** du backlog doit être vérifiée.
- Le backlog est mis à jour (cases cochées).

## 4. ADR (décisions d'architecture)

- Emplacement : `docs/adr/ADR-NNN-titre-court.md`
- Statuts : `Proposé` → `Accepté` / `Rejeté` → `Remplacé par ADR-XXX`
- Un ADR accepté n'est pas réécrit : on en crée un nouveau qui le remplace.

## 5. Code

- **Java** : packages `api`, `application`, `domain`, `infrastructure` ; formatage Spotless ; montants en `BigDecimal` uniquement.
- **Angular** : `@Service()` avec `inject()`, composants standalone, formatage ESLint et Prettier.
- **Encodage et fins de ligne** : UTF-8 et LF (voir `.editorconfig` et `.gitattributes`).

## 6. Secrets

- Ne **jamais** commiter de `.env`, de clé privée ou de mot de passe réel.
- `.env.example` documente les variables attendues, avec des valeurs factices.
