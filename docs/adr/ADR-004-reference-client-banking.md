# ADR-004 — Référence client dans le Banking Service

| | |
|---|---|
| **Statut** | Accepté (2026-11-23) |
| **Date** | 2026-11-23 |
| **Lots concernés** | BF-006 (Customer Service), BF-007 (Banking Service) |
| **Complète** | ADR-001 (un service ne lit jamais la base d'un autre) |

## 1. Contexte

Un compte bancaire appartient à un `customerId`, géré par le Customer Service. Le JWT ne porte que `sub` (le `userId`) et `role` : il ne contient pas l'email. Aucun service ne lit la base d'un autre. Le Banking Service doit pourtant, à chaque requête, retrouver le client de l'utilisateur connecté et savoir s'il est suspendu.

## 2. Options

| | A. Appel REST synchrone | B. Projection locale `customer_refs` |
|---|---|---|
| **Principe** | Le Banking Service interroge le Customer Service à chaque requête. | Le Banking Service garde une copie minimale des clients, alimentée par `CustomerCreated` et `CustomerUpdated` sur `bank.customers`. |
| **Avantages** | Donnée toujours à jour, aucune duplication. | Aucun appel entre services au moment de la requête : le Banking Service reste disponible si le Customer Service tombe. |
| **Inconvénients** | Couplage temporel : si le Customer Service est indisponible, le Banking Service l'est aussi. Latence ajoutée à chaque requête. | Cohérence à terme (délai de propagation). Duplication minimale de l'email et du statut. |

## 3. Décision

**Option B.** Le Banking Service tient une table `customer_refs` (`customer_id`, `user_id`, `email`, `status`, `updated_at`), mise à jour par upsert à chaque `CustomerCreated` et `CustomerUpdated`. Le client est résolu par `user_id` = `sub` du JWT.

Les payloads sont minimaux (`customerId`, `userId`, `email`, `status`). Le nom, le téléphone et l'adresse ne quittent jamais le Customer Service. Le statut `SUSPENDED` est propagé : le Banking Service refuse alors toute création ou opération.

## 4. Rattrapage

Un événement peut manquer : compte seedé (admin et démo), compte inscrit avant la MVP 2, événement perdu pendant une panne de Kafka. Le mécanisme de rattrapage est le suivant.

- L'Auth Service republie `UserRegistered` pour **tous** les utilisateurs à chaque démarrage.
- Le Customer Service est idempotent sur `userId` : il crée le client s'il n'existe pas, et republie `CustomerCreated` pour un client existant. Cela rattrape aussi un `CustomerCreated` perdu.
- Le Banking Service fait un upsert de `customer_refs` : rejouer un événement est sans effet.

**Limite assumée** : le coût est linéaire au nombre d'utilisateurs à chaque démarrage de l'Auth Service. C'est acceptable pour une démonstration. L'Outbox (BF-011) remplacera ce mécanisme.

## 5. Alternatives écartées

| Alternative | Raison du rejet |
|---|---|
| Appel REST synchrone (option A) | Couplage temporel et perte de disponibilité du Banking Service. |
| Créer la fiche client à la première visite | Le JWT ne contient pas l'email, que la fiche doit porter. |
| Lire la base du Customer Service | Chaque service possède sa base : aucun service ne lit celle d'un autre. |

## 6. Conséquences

**Positives**
- Le Banking Service fonctionne même si le Customer Service est arrêté.
- La suspension d'un client est propagée sans appel synchrone.
- Peu de données personnelles circulent sur Kafka.

**Négatives et risques**
- Un délai sépare l'inscription de la disponibilité de la fiche : le Banking Service répond 409 `CUSTOMER_NOT_READY`, le Customer Service 404 `CUSTOMER_NOT_FOUND`, et le front réessaie.
- Une suspension n'est effective côté Banking Service qu'après propagation de `CustomerUpdated`.
- Chaque démarrage de l'Auth Service provoque un rejeu proportionnel au nombre d'utilisateurs.
- Tous les consommateurs doivent rester idempotents sur la clé métier.
