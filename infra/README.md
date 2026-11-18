# Infrastructure

Docker Compose, PostgreSQL, Kafka (KRaft), Kafka UI, Mailpit, Prometheus et Grafana.

Mis en place aux lots **BF-002** (PostgreSQL, Mailpit), **BF-003** (Kafka) et **BF-020** (monitoring).

## Démarrage

```bash
cp .env.example .env   # puis renseigner les mots de passe
docker compose up -d   # démarre l'environnement
docker compose ps      # les services doivent être « healthy »
docker compose --profile apps up -d --build   # construit et démarre aussi les services Spring Boot
```

Le profil Spring se choisit avec la variable `SPRING_PROFILE` : `dev` (par défaut) ou `prod-sim`. Le profil `test` est réservé aux tests automatisés.

## Commandes utiles

| Commande | Effet |
|---|---|
| `docker compose logs -f postgres` | Suit les logs d'un service |
| `docker compose down` | Arrête l'environnement et conserve les données |
| `docker compose down -v` | Arrête l'environnement et **supprime les données** |

Le script `postgres/init/01-create-databases.sh` ne s'exécute qu'à la création du volume. Après un changement de mot de passe dans `.env`, il faut recréer le volume avec `docker compose down -v`.

## Ports (publiés sur `127.0.0.1` uniquement)

| Service | Port | Variable | URL ou usage |
|---|---|---|---|
| PostgreSQL | 5432 | `POSTGRES_HOST_PORT` | `jdbc:postgresql://localhost:5432/<base>` |
| Mailpit SMTP | 1025 | `MAILPIT_SMTP_HOST_PORT` | Serveur SMTP de développement |
| Mailpit interface | 8025 | `MAILPIT_UI_HOST_PORT` | <http://localhost:8025> |
| Kafka | 9092 | `KAFKA_HOST_PORT` | `localhost:9092` depuis le poste, `kafka:29092` entre conteneurs |
| Kafka UI | 8090 | `KAFKA_UI_HOST_PORT` | <http://localhost:8090> |
| API Gateway | 8080 | `GATEWAY_HOST_PORT` | <http://localhost:8080/api> (profil `apps`) |

## Bases de données

Chaque service possède sa base et son utilisateur, qui ne peut pas se connecter aux autres bases.

| Service | Base | Utilisateur | Mot de passe (`.env`) |
|---|---|---|---|
| auth-service | `auth_db` | `auth_user` | `AUTH_DB_PASSWORD` |
| customer-service | `customer_db` | `customer_user` | `CUSTOMER_DB_PASSWORD` |
| banking-service | `banking_db` | `banking_user` | `BANKING_DB_PASSWORD` |
| notification-service | `notification_db` | `notification_user` | `NOTIFICATION_DB_PASSWORD` |
| analytics-fraud-service | `analytics_db` | `analytics_user` | `ANALYTICS_DB_PASSWORD` |

## Kafka

Les topics sont créés au démarrage par le conteneur `kafka-init`. Leur configuration est décrite dans [docs/kafka/topics.md](../docs/kafka/topics.md).
