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

Dans chaque couche, les classes sont rangées par fonctionnalité (`login`, `account`, `transfer`…). À la racine d'une fonctionnalité ne restent que les classes qui portent la logique : contrôleurs, services, entités, dépôts, énumérations et value objects. Le reste va dans un sous-package selon sa nature :

| Sous-package | Contenu | Couche |
|---|---|---|
| `dto/` | records de requête et de réponse HTTP | `api` |
| `validation/` | annotations de validation et leurs validateurs | `api` |
| `error/` | `ApiExceptionHandler` et exceptions propres à l'API, au niveau de la couche (`api/error/`) | `api` |
| `model/` | records d'entrée ou de sortie des cas d'usage (vues, requêtes, résultats) | `application` |
| `config/` | records `@ConfigurationProperties` | `application` |
| `exception/` | exceptions métier ou de cas d'usage | `application`, `domain` |
| `event/` | événements du domaine publiés sur Kafka | `domain` |

Exemple avec l'Auth Service :

```
com.bankflow.auth/
├── api/
│   ├── auth/            AuthController, RefreshCookies, UserMapper, dto/, validation/
│   ├── demo/            DemoAccountsController
│   ├── error/           ApiExceptionHandler
│   └── jwks/            JwksController
├── application/
│   ├── demo/            DemoAccounts, model/
│   ├── login/           LoginService, exception/
│   ├── registration/    RegistrationService, exception/
│   ├── token/           AccessTokenService, RefreshTokenService, model/, exception/
│   └── user/            CurrentUserService, exception/
├── domain/
│   ├── user/            User, Role, UserRepository, event/, exception/
│   └── token/           RefreshToken, RefreshTokenRepository, RevocationReason
└── infrastructure/
    ├── bootstrap/       compte administrateur et comptes de démonstration
    ├── config/          beans transverses (Clock, PasswordEncoder)
    ├── kafka/           publication des événements
    ├── openapi/         Swagger
    └── security/        SecurityConfig, jwt/
```

La Gateway, qui n'a pas de domaine, se range en `filter/`, `error/` et `security/`.

## Versions

Spring Boot **4.2.0** et Spring Cloud **2026.0.0** (Gateway), Java **27** ([ADR-001](../docs/adr/ADR-001-stack-backend.md)).
