# Auth Service

Microservice BankFlow, projet Spring Boot **indépendant** (aucun code partagé avec les autres services).

| | |
|---|---|
| Port | `8081` |
| Base de données | `auth_db` |
| Package racine | `com.bankflow.auth` |

## Commandes

```bash
./mvnw verify                      # build + tests (Docker requis pour Testcontainers)
./mvnw spring-boot:test-run        # démarrage local avec PostgreSQL/Kafka en conteneurs
```

Spécifications : voir le cahier des charges et le [backlog](../../BACKLOG.md).
