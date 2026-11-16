# API Gateway

Microservice BankFlow, projet Spring Boot **indépendant** (aucun code partagé avec les autres services).

| | |
|---|---|
| Port | `8080` |
| Base de données | aucune |
| Package racine | `com.bankflow.gateway` |

## Commandes

```bash
./mvnw verify                      # build + tests (Docker requis pour Testcontainers)
./mvnw spring-boot:test-run        # démarrage local avec PostgreSQL/Kafka en conteneurs
```

Spécifications : voir le cahier des charges et le [backlog](../../BACKLOG.md).
