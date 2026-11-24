# ADR-004 — Référence client dans le Banking Service

| | |
|---|---|
| **Statut** | Accepté (2026-11-23) |
| **Date** | 2026-11-23 |
| **Lots concernés** | BF-006 (Customer Service), BF-007 (Banking Service) |
| **Complète** | ADR-001 (services autonomes, aucune classe partagée) ; règle transverse du BACKLOG « aucun accès d'un service à la base d'un autre » |

## 1. Contexte

Un compte bancaire appartient à un `customerId`, géré par le Customer Service. Le JWT ne porte que `sub` (le `userId`) et `role` : il ne contient pas l'email. Les services sont autonomes : aucun ne lit la base d'un autre (exigence transverse du BACKLOG, « aucun accès d'un service à la base d'un autre »). Le Banking Service doit pourtant, à chaque requête, retrouver le client de l'utilisateur connecté et savoir s'il est suspendu.

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

- L'Auth Service republie `UserRegistered` pour **tous** les utilisateurs à chaque démarrage. Un envoi en échec est retenté avec une attente croissante, pendant environ deux minutes : sur une machine lente, la première lecture des métadonnées de Kafka peut dépasser le délai d'envoi de 3 s (0.2.1). Au-delà, le rejeu s'arrête et le prochain démarrage recommencera.
- Le Customer Service est idempotent sur `userId` : il crée le client s'il n'existe pas, et republie `CustomerCreated` pour un client existant. Cela rattrape aussi un `CustomerCreated` perdu.
- Le Banking Service fait un upsert de `customer_refs` : rejouer un événement est sans effet.

### Ordre des événements

`occurredAt` de `CustomerCreated` et `CustomerUpdated` est la date de l'état publié (`updated_at` du client), y compris lors d'une republication. Le Banking Service ignore un événement plus ancien que le dernier appliqué (stocké dans `customer_refs.updated_at`) : un ancien `CustomerCreated(ACTIVE)` rejoué ne réactive donc jamais un client suspendu. La clé `customerId` garantit l'ordre dans une partition ; la comparaison de dates couvre les republications concurrentes et les rejeux.

Le Customer Service ne retire jamais l'indicateur `demo` : un `UserRegistered` sans `demo` ou avec `demo: false` ne le remet pas à faux.

**Limite assumée** : le coût est linéaire au nombre d'utilisateurs à chaque démarrage de l'Auth Service. C'est acceptable pour une démonstration. L'Outbox (BF-011) remplacera ce mécanisme.

## 5. Alternatives écartées

| Alternative | Raison du rejet |
|---|---|
| Appel REST synchrone (option A) | Couplage temporel et perte de disponibilité du Banking Service. |
| Créer la fiche client à la première visite | Le JWT ne contient pas l'email, que la fiche doit porter. |
| Lire la base du Customer Service | Chaque service possède sa base : règle transverse du BACKLOG, aucun accès d'un service à la base d'un autre. |

## 6. Conséquences

**Positives**
- Le Banking Service fonctionne même si le Customer Service est arrêté.
- La suspension d'un client est propagée sans appel synchrone.
- Seul l'email circule sur Kafka, jamais le nom, le téléphone ni l'adresse. Il sera aussi conservé dans la DLQ de BF-013 pour un message en échec.

**Négatives et risques**
- Un délai sépare l'inscription de la disponibilité de la fiche : le Banking Service répond 409 `CUSTOMER_NOT_READY`, le Customer Service 404 `CUSTOMER_NOT_FOUND`, et le front réessaie.
- Une suspension n'est effective côté Banking Service qu'après propagation de `CustomerUpdated`.
- Chaque démarrage de l'Auth Service provoque un rejeu proportionnel au nombre d'utilisateurs.
- Tous les consommateurs doivent rester idempotents sur la clé métier.

## 7. Mise à jour du 2026-11-23

- **Indicateur `demo`** : `CustomerCreated` v1 et `CustomerUpdated` v1 gagnent un champ facultatif `demo` (booléen), toujours renseigné par le Customer Service. Le Banking Service le range dans `customer_refs.demo` (migration `V4__customer_refs_demo.sql`, `FALSE` par défaut). Le synchroniseur l'applique quand le champ est présent et garde la valeur connue quand il est absent. Il sert à restreindre `demo.admin` aux clients de démonstration (ADR-005 et ADR-007).
- **Administrateur** : `UserRegistered` v1 gagne un champ facultatif `role`. Un `ROLE_ADMIN` ne crée aucune fiche client ni référence côté Banking Service.
- Les champs ajoutés sont facultatifs : un consommateur qui ne les connaît pas continue de fonctionner.
