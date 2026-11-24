# Contrats d'événements

Contrats Kafka **versionnés indépendamment** des services (section 5.3 du cahier des charges).

- `events/envelope.md` : enveloppe commune (BF-003)
- `events/<EventType>.v<N>.json` : JSON Schema du payload de chaque événement

Ces contrats sont la seule source de vérité : aucune classe Java n'est partagée entre services.

## Topics et événements

| Topic | Producteur | Clé | Événements | Consommateur (group id) |
|---|---|---|---|---|
| `bank.users` | auth-service | `userId` | `UserRegistered` v1 | customer-service (`customer-service`) |
| `bank.customers` | customer-service | `customerId` | `CustomerCreated` v1, `CustomerUpdated` v1 | banking-service (`banking-service`) |

**Idempotence** : l'Auth Service republie `UserRegistered` pour tous les utilisateurs à chaque démarrage, et le Customer Service peut republier `CustomerCreated`. Tout consommateur est donc idempotent sur la clé métier (`userId`, `customerId`), jamais sur `eventId`. Voir [ADR-004](../docs/adr/ADR-004-reference-client-banking.md).

Règles de consommation :

- Un consommateur ignore les champs inconnus du `payload`.
- Un message invalide (illisible, `eventType` ou `eventVersion` inconnu) est journalisé sans son contenu, puis ignoré.
- Deux messages concurrents pour la même clé : la contrainte d'unicité tranche.
- Ordre : un événement client plus ancien que l'état connu est ignoré (`occurredAt`).
