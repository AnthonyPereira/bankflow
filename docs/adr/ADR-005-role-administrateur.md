# ADR-005 — Rôle de l'administrateur : un conseiller qui agit au nom des clients

| | |
|---|---|
| **Statut** | Accepté (2026-11-23) |
| **Date** | 2026-11-23 |
| **Lots concernés** | BF-007 (comptes), BF-008 (opérations), BF-009 (virements), BF-016 (tableau de bord), BF-017 (écrans) |
| **Complète** | ADR-004 (résolution du client) |
| **Remplace** | La décision de la PR #65, qui interdisait à l'administrateur d'ouvrir un compte |

## 1. Contexte

Le Banking Service connaît deux rôles : `ROLE_CLIENT` et `ROLE_ADMIN`. La PR #65 avait réservé l'ouverture de compte au client. L'administrateur ne pouvait alors que consulter. Or l'application sert de vitrine : un visiteur qui se connecte avec `demo.admin` doit pouvoir montrer ce que fait un conseiller. Il faut aussi une façon de corriger une erreur sans toucher aux écritures (voir ADR-006).

## 2. Décision

L'administrateur agit **comme un conseiller, au nom des clients**.

- Il ouvre un compte pour un client : `customerId` est **obligatoire**.
- Il clôture un compte, y dépose, y retire, et fait un virement depuis le compte d'un client.
- Il n'a **jamais de compte à son nom**. Sans `customerId`, l'ouverture est refusée (400).
- Un client ne fournit pas `customerId` : son compte s'ouvre pour lui-même (400 s'il le fournit).
- Seul l'administrateur fait une **écriture compensatrice**. Elle exige un motif de 10 à 200 caractères, ne s'applique qu'à un dépôt ou un retrait, et une seule fois par opération.

### Mêmes règles métier que le client

L'administrateur ne contourne aucune règle : plafond par opération, solde suffisant, compte actif, limite de comptes. Le **client ne doit pas être suspendu** (403 `CUSTOMER_SUSPENDED`) pour toute action qui crée ou déplace de l'argent. Deux exceptions : la **clôture** reste permise sur un client suspendu, et la **consultation** l'est toujours.

### Traçabilité

Les tables `transactions` et `transfers` enregistrent `performed_by_user_id` (le `sub` du JWT) et `performed_by_role` (`CLIENT` ou `ADMIN`). L'API expose seulement `performedBy`, jamais l'identifiant de l'administrateur. L'historique affiche « Effectuée par un conseiller » pour `ADMIN`.

### Idempotence

La clé d'idempotence est rattachée à la **personne qui agit** (`actor_user_id`), pas au propriétaire du compte. Un client et un administrateur peuvent donc utiliser la même clé sans collision (voir ADR-006).

### Écrans et tableau de bord

- Écran « Clients » pour l'administrateur : liste, recherche, fiche d'un client, ouverture d'un compte, suspension (sauf comptes de démo). Il est avancé depuis BF-018 vers BF-017-08.
- Le tableau de bord de l'administrateur est une **vue de la banque** : `GET /accounts/summary` (clients actifs et suspendus, comptes ouverts et clos, total par devise). Cet endpoint est réservé à l'administrateur.
- `GET /accounts?customerId=` est un filtre réservé à l'administrateur.

## 3. Alternatives écartées

| Alternative | Raison du rejet |
|---|---|
| Administrateur sans aucun droit sur les comptes | Aucune démonstration du rôle de conseiller, et aucun moyen de corriger une erreur. |
| Administrateur avec ses propres comptes | Mélange le personnel de la banque et ses clients. Un administrateur n'a pas de fiche client, et les contrôles de propriété deviennent ambigus. |

## 4. Conséquences

**Positives**
- Chaque action sensible porte l'auteur et son rôle : l'audit est possible.
- Les règles métier sont écrites une seule fois et valent pour les deux rôles.
- La vitrine montre un vrai parcours de conseiller.

**Négatives et risques**
- Les contrôles d'accès se complexifient : titulaire ou administrateur, selon l'endpoint.
- Un administrateur peut déplacer de l'argent au nom d'un client. C'est voulu, tracé, et limité par les mêmes plafonds.
- L'identifiant de l'administrateur est stocké en base mais jamais exposé.
