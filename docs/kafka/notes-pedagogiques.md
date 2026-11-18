# Kafka : notes pédagogiques

Ces notes résument les concepts de BF-003. Les résultats des expériences ont été observés le 2026-11-18 sur Kafka 4.4.0, avec un topic de 3 partitions et 12 messages.

## Concepts

| Concept | À retenir |
|---|---|
| **Topic** | Journal d'événements nommé, par exemple `bank.transfers`. Lire un message ne le supprime pas. |
| **Partition** | Morceau ordonné d'un topic. L'ordre n'est garanti qu'à l'intérieur d'une partition. |
| **Offset** | Position d'un message dans sa partition. |
| **Clé** | Valeur dont le hachage choisit la partition. Une même clé va toujours dans la même partition. |
| **Producer** | Application qui écrit des messages. |
| **Consumer** | Application qui lit des messages et retient sa position. |
| **Consumer group** | Consommateurs qui se partagent le travail. Une partition n'est lue que par un seul membre du groupe. |

## Préparer les expériences

```bash
docker compose up -d kafka
docker exec -it bankflow-kafka bash
cd /opt/kafka/bin
export BS="--bootstrap-server kafka:29092"
./kafka-topics.sh $BS --create --topic demo.experiences --partitions 3 --replication-factor 1
```

## Expérience 1 : clé et partition

```bash
./kafka-console-producer.sh $BS --topic demo.experiences \
  --reader-property parse.key=true --reader-property key.separator=:
# Saisir par exemple : compte-A:operation-1

./kafka-console-consumer.sh $BS --topic demo.experiences --from-beginning \
  --formatter-property print.partition=true --formatter-property print.key=true
```

| Clé | Messages | Partition observée |
|---|---|---|
| `compte-A` | 4 | 1 |
| `compte-B` | 4 | 1 |
| `compte-C` | 4 | 0 |

**Conclusion :** chaque clé reste dans une seule partition, ce qui garantit l'ordre par compte. Deux clés peuvent partager une partition, comme `compte-A` et `compte-B`.

## Expérience 2 : deux consommateurs dans un même groupe

Lancer cette commande dans deux terminaux :

```bash
./kafka-console-consumer.sh $BS --topic demo.experiences --group g-partage
```

Puis observer la répartition :

```bash
./kafka-consumer-groups.sh $BS --describe --group g-partage --members --verbose
```

| Membre | Partitions attribuées |
|---|---|
| Consommateur 1 | 2 |
| Consommateur 2 | 0 et 1 |

**Conclusion :** le groupe se partage les 3 partitions sans recouvrement. Ajouter un consommateur augmente le parallélisme, dans la limite du nombre de partitions.

## Expérience 3 : deux groupes distincts

```bash
./kafka-console-consumer.sh $BS --topic demo.experiences --group g-a --from-beginning
./kafka-console-consumer.sh $BS --topic demo.experiences --group g-b --from-beginning
```

| Groupe | Messages lus |
|---|---|
| `g-a` | 12 |
| `g-b` | 12 |

**Conclusion :** chaque groupe reçoit tous les messages et garde sa propre position. C'est ce qui permet à plusieurs services de réagir au même événement.

## Expérience 4 : reset des offsets

Les consommateurs du groupe doivent être arrêtés avant le reset.

```bash
./kafka-consumer-groups.sh $BS --group g-a --topic demo.experiences --reset-offsets --to-earliest --execute
./kafka-console-consumer.sh $BS --topic demo.experiences --group g-a
```

| Étape | Messages lus par `g-a` |
|---|---|
| Nouvelle lecture sans reset | 0 |
| Nouvelle lecture après reset | 12 |

**Conclusion :** rejouer des messages revient à déplacer l'offset. Les consommateurs doivent donc être idempotents (BF-013).

## Configuration retenue dans les services

| Réglage | Valeur | Raison |
|---|---|---|
| `acks` | `all` | Le broker confirme l'écriture une fois le message enregistré sur les répliques synchronisées. |
| `enable.idempotence` | `true` | Un renvoi automatique du producer ne crée pas de doublon. |
| `enable.auto.commit` | `false` | Un offset ne doit pas être validé avant la fin du traitement. |
| `ack-mode` | `record` | Spring valide l'offset après le traitement réussi de chaque message. |
| `auto.offset.reset` | `earliest` | Un nouveau groupe lit l'historique disponible plutôt que de le manquer. |
| Désérialisation | `ErrorHandlingDeserializer` et `StringDeserializer` | Un message illisible ne fait pas planter le consommateur en boucle. |

## Démonstration dans le code

Le producer et le consumer de démonstration se trouvent dans `services/auth-service`, package `infrastructure.kafka.demo`. Ils ne sont actifs qu'avec le profil Spring `kafka-demo`. Le test `KafkaDemoIntegrationTests` vérifie, avec un vrai broker lancé par Testcontainers, qu'un message produit est bien consommé.
