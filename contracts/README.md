# Contrats d'événements

Contrats Kafka **versionnés indépendamment** des services (section 5.3 du cahier des charges).

- `events/envelope.md` : enveloppe commune (BF-003)
- `events/<EventType>.v<N>.json` : JSON Schema du payload de chaque événement

Ces contrats sont la seule source de vérité : aucune classe Java n'est partagée entre services.
