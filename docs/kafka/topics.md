# Topics Kafka

Les topics sont créés par le conteneur `kafka-init`, qui exécute [`infra/kafka/create-topics.sh`](../../infra/kafka/create-topics.sh). La création automatique est désactivée sur le broker : un topic absent de ce script n'existe pas.

## Configuration

| Topic | Partitions | Réplication | Rétention | `cleanup.policy` | Clé de message | Producteur |
|---|---|---|---|---|---|---|
| `bank.users` | 3 | 1 | 30 jours | `delete` | `userId` | Auth Service |
| `bank.customers` | 3 | 1 | 30 jours | `delete` | `customerId` | Customer Service |
| `bank.accounts` | 6 | 1 | 30 jours | `delete` | `accountId` | Banking Service |
| `bank.transfers` | 6 | 1 | 30 jours | `delete` | `accountId` | Banking Service |
| `bank.transactions` | 6 | 1 | 30 jours | `delete` | `accountId` | Banking Service |
| `bank.fraud-alerts` | 3 | 1 | 90 jours | `delete` | `customerId` | Analytics & Fraud Service |

## Justification des choix

- **Partitions :** les topics bancaires ont 6 partitions, car ils portent le plus de trafic. Le nombre de partitions fixe le parallélisme maximal d'un consumer group.
- **Clé de message :** tous les messages d'une même clé vont dans la même partition. L'ordre est donc garanti par compte ou par client, et seulement à ce niveau.
- **Réplication à 1 :** l'environnement n'a qu'un seul broker. Un cluster répliqué est étudié dans BF-023-13.
- **`min.insync.replicas=1` :** cette valeur est imposée par la réplication à 1. Avec `acks=all`, le broker confirme l'écriture dès que son unique réplique l'a enregistrée.
- **`cleanup.policy=delete` :** les événements décrivent des faits passés. Ils ne sont pas compactés, pour que chaque événement reste consultable pendant la rétention.
- **Rétention :** 30 jours laissent le temps de rejouer un consommateur. Les alertes de fraude sont gardées 90 jours pour permettre leur analyse.

## Accès

| Depuis | Adresse |
|---|---|
| Un conteneur du réseau Compose | `kafka:29092` |
| Le poste de développement | `localhost:9092` |
| Kafka UI | <http://localhost:8090> |
