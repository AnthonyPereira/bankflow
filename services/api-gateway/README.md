# API Gateway

Microservice BankFlow, projet Spring Boot **indépendant** (aucun code partagé avec les autres services).

| | |
|---|---|
| Port | `8080` |
| Base de données | aucune |
| Package racine | `com.bankflow.gateway` |

## Configuration

`TRUSTED_PROXIES` : regex des proxys dont les en-têtes `X-Forwarded-*` sont transmis aux services (plages privées par défaut).

`RATE_LIMIT_OPERATIONS_PER_MINUTE` : opérations bancaires (ouverture, dépôt, retrait, clôture, compensation) par minute et par IP, 60 par défaut.

`RATE_LIMIT_TRANSFERS_PER_MINUTE` : virements par minute et par IP, 30 par défaut.

`RATE_LIMIT_TRANSFERS_PER_USER_PER_MINUTE` : virements par minute et par utilisateur (sujet du JWT), 10 par défaut, quelle que soit l'adresse IP. Les virements refusés par le Banking Service comptent aussi : c'est ce qui empêche de sonder des IBAN.

## Commandes

```bash
./mvnw verify                      # build + tests (Docker requis pour Testcontainers)
./mvnw spring-boot:test-run        # démarrage local avec PostgreSQL/Kafka en conteneurs
```

Spécifications : voir le cahier des charges et le [backlog](../../BACKLOG.md).
