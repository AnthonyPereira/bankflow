# API Gateway

Microservice BankFlow, projet Spring Boot **indépendant** (aucun code partagé avec les autres services).

| | |
|---|---|
| Port | `8080` |
| Base de données | aucune |
| Package racine | `com.bankflow.gateway` |

## Configuration

`TRUSTED_PROXIES` : regex des proxys dont les en-têtes `X-Forwarded-*` sont transmis aux services (plages privées par défaut).

## Commandes

```bash
./mvnw verify                      # build + tests (Docker requis pour Testcontainers)
./mvnw spring-boot:test-run        # démarrage local avec PostgreSQL/Kafka en conteneurs
```

Spécifications : voir le cahier des charges et le [backlog](../../BACKLOG.md).
