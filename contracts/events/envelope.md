# Enveloppe commune des événements

Chaque message Kafka de BankFlow est un document JSON qui respecte cette enveloppe. Le schéma de référence est [`envelope.v1.schema.json`](envelope.v1.schema.json).

| Champ | Type | Obligatoire | Description |
|---|---|---|---|
| `eventId` | UUID | Oui | Identifiant unique et stable de l'événement. Les consommateurs s'en servent pour dédupliquer. |
| `eventType` | Texte | Oui | Nom de l'événement en PascalCase, par exemple `UserRegistered`. |
| `eventVersion` | Entier ≥ 1 | Oui | Version du contrat du `payload`. |
| `occurredAt` | Date ISO 8601 UTC | Oui | Moment où le fait métier s'est produit. |
| `producer` | Texte | Oui | Nom du service émetteur, par exemple `auth-service`. |
| `payload` | Objet | Oui | Données propres à l'événement, décrites par `<EventType>.v<N>.json`. |

## Exemple

```json
{
  "eventId": "0b9e7f3a-3f6c-4c55-9d43-8a3f0a1c2b11",
  "eventType": "UserRegistered",
  "eventVersion": 1,
  "occurredAt": "2026-11-18T10:15:30Z",
  "producer": "auth-service",
  "payload": {
    "userId": "6f1c2d84-7a51-4e0b-9a36-0a7d5c1e9f22"
  }
}
```

## Règles

- Le `payload` reste minimal : jamais de mot de passe, de token ni de donnée personnelle inutile.
- Les messages sont sérialisés en texte JSON avec `StringSerializer`, sans en-tête de type Java (ADR-001, section 2.6).
- Ajouter un champ facultatif est compatible. Renommer ou supprimer un champ impose une nouvelle `eventVersion`.
