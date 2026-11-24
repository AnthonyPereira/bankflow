# ADR-006 — Comptabilité en partie double et cohérence des écritures

| | |
|---|---|
| **Statut** | Accepté (2026-11-23) |
| **Date** | 2026-11-23 |
| **Lots concernés** | BF-007 (comptes), BF-008 (dépôts et retraits), BF-009 (virements) |
| **Complète** | ADR-005 (traçabilité et compensation) |

## 1. Contexte

Le Banking Service déplace de l'argent fictif, mais doit se comporter comme un vrai ledger : jamais de solde négatif, jamais d'argent créé ou perdu, historique impossible à réécrire. Des requêtes concurrentes et des rejeux réseau sont normaux. Il faut une règle simple, vérifiable par des tests.

## 2. Décision

### Partie double

Chaque opération produit des écritures (`ledger_entries`) qui s'équilibrent. Un dépôt ou un retrait a besoin d'une contrepartie : un **compte technique `CASH`** représente l'argent hors de la banque.

- Dépôt : crédit du compte, débit de `CASH`.
- Retrait : débit du compte, crédit de `CASH`.
- Virement : débit du compte source, crédit du compte destination.
- Compensation : écritures inverses de l'opération compensée.

### Écritures immuables

Les tables `transactions` et `ledger_entries` refusent `UPDATE`, `DELETE` et `TRUNCATE` par des **triggers PostgreSQL** (migration V3). L'application ne peut donc pas modifier le passé, même par erreur. **Corriger, c'est compenser** : on ajoute une écriture inverse, on ne modifie jamais une écriture. La table `transfers` reste modifiable, car son statut évolue (`PENDING`, puis `COMPLETED` ou `REJECTED`).

### Concurrence

- Le compte est verrouillé par `SELECT … FOR UPDATE` avant toute modification du solde.
- Pour un virement, les **deux comptes sont verrouillés dans l'ordre croissant de leur UUID**. Deux virements croisés ne peuvent pas s'interbloquer.
- Un virement est **une seule transaction SQL** : le `Transfer`, ses deux `Transaction` et ses écritures réussissent ensemble ou pas du tout.

### Idempotence

L'en-tête `Idempotency-Key` (`^[A-Za-z0-9_-]{1,64}$`) est facultatif pour un dépôt ou un retrait, **obligatoire** pour un virement.

- La clé appartient à l'auteur : clé primaire `(actor_user_id, idempotency_key)`.
- L'empreinte est un SHA-256 de `méthode + chemin + corps JSON canonique` (champs triés, montant à 2 décimales).
- Dans **la même transaction** que l'opération : `INSERT … ON CONFLICT DO NOTHING`. Une requête concurrente avec la même clé attend le commit de la première.
- Même clé et même empreinte : la même réponse est renvoyée (en-tête `Idempotent-Replayed: true`), sans refaire l'opération. Même clé et empreinte différente : 422 `IDEMPOTENCY_KEY_REUSED`.
- Une erreur métier annule la transaction : la clé n'est pas conservée et peut être réessayée.
- Une clé est gardée 24 h (`bankflow.idempotency.retention`, variable `IDEMPOTENCY_RETENTION`). Une tâche planifiée supprime toutes les heures les clés plus anciennes : passé ce délai, la même clé exécute de nouveau l'opération. Une opération en cours n'est jamais concernée, car sa clé n'est pas encore commitée. Si la purge supprime une clé entre une réservation refusée et la lecture de sa réponse, la clé est réservée de nouveau. La purge est idempotente : plusieurs instances peuvent l'exécuter sans verrou.

### Invariants

Ils sont testés :
- le solde d'un compte est égal à Σ crédits − Σ débits de ses écritures ;
- sur toute la base, Σ débits = Σ crédits.

## 3. Alternatives écartées

| Alternative | Raison du rejet |
|---|---|
| Solde calculé à la volée, sans colonne `balance` | Lecture coûteuse, et impossible de verrouiller simplement une ligne pour sérialiser les opérations. La colonne est conservée, et l'invariant la contrôle. |
| Verrou optimiste seul (`version`) | Beaucoup d'échecs et de relances sous charge. `FOR UPDATE` sérialise proprement, et le test de 50 retraits concurrents est déterministe. |
| Modification ou suppression d'écritures | Détruit la piste d'audit. Interdit par les triggers, remplacé par la compensation. |

## 4. Conséquences

**Positives**
- Impossible de réécrire l'historique, même avec un bug applicatif.
- Pas d'interblocage entre virements croisés.
- Un rejeu réseau ne duplique jamais une opération.

**Négatives et risques**
- Les tests ne peuvent pas nettoyer ces tables avec `DELETE` : ils utilisent des données isolées par test.
- Les opérations d'un même compte sont sérialisées par le verrou. C'est acceptable à cette échelle.
- Le compte `CASH` concentre toutes les écritures de dépôt et de retrait. Il est exclu des statistiques de la banque.
- Les statuts `PENDING` et `REJECTED` d'un virement sont réservés à un traitement asynchrone futur (par exemple un contrôle antifraude) : aujourd'hui un virement est exécuté dans la requête et enregistré directement `COMPLETED`, ou refusé sans laisser de trace.
