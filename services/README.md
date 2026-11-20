# Microservices

Chaque dossier est un projet Spring Boot **indépendant** : son propre `pom.xml`, son wrapper Maven, sa base de données. Aucun module Java partagé ([ADR-001](../docs/adr/ADR-001-stack-backend.md)).

| Service | Port | Base | Package |
|---|---|---|---|
| [api-gateway](api-gateway) | 8080 | — | `com.bankflow.gateway` |
| [auth-service](auth-service) | 8081 | `auth_db` | `com.bankflow.auth` |
| [customer-service](customer-service) | 8082 | `customer_db` | `com.bankflow.customer` |
| [banking-service](banking-service) | 8083 | `banking_db` | `com.bankflow.banking` |
| [notification-service](notification-service) | 8084 | `notification_db` | `com.bankflow.notification` |
| [analytics-fraud-service](analytics-fraud-service) | 8085 | `analytics_db` | `com.bankflow.analytics` |

## Structure interne (services métier)

```
com.bankflow.<service>/
├── api/             contrôleurs REST, DTO, erreurs HTTP
├── application/     cas d'usage, transactions
├── domain/          entités, value objects, règles métier
└── infrastructure/  JPA, Kafka, clients REST, configuration
```

Dans chaque couche, les classes sont rangées par fonctionnalité dès qu'il y en a plusieurs, sur un seul niveau : un sous-package regroupe le cas d'usage, ses exceptions et ses DTO. Exemple avec l'Auth Service :

```
com.bankflow.auth/
├── api/
│   ├── auth/            AuthController, cookies, erreurs HTTP, dto/
│   └── jwks/            JwksController
├── application/
│   ├── registration/    inscription
│   ├── login/           connexion
│   └── token/           access token et refresh token
├── domain/
│   ├── user/            User, Role, UserRegistered
│   └── token/           RefreshToken
└── infrastructure/
    ├── bootstrap/       compte administrateur initial
    ├── config/          beans transverses (Clock, PasswordEncoder)
    ├── kafka/           publication des événements
    ├── openapi/         Swagger
    └── security/        SecurityConfig, jwt/
```

La Gateway, qui n'a pas de domaine, se range en `filter/`, `error/` et `security/`.

## Versions

Spring Boot **4.2.0** et Spring Cloud **2026.0.0** (Gateway), Java **27** ([ADR-001](../docs/adr/ADR-001-stack-backend.md)).
