# ADR-001 — Stack technique backend

| | |
|---|---|
| **Statut** | Accepté (2026-11-16). Les versions exactes restent à figer (voir §5) |
| **Date** | 2026-11-16 |
| **Lots concernés** | BF-001 (initialisation), puis tous les lots backend |
| **Remplace / complète** | Section 3.1 du cahier des charges |

## 1. Contexte

Le cahier des charges impose Java 27, Spring Boot, Spring Cloud, Spring Kafka, PostgreSQL, Maven, MapStruct, Flyway, JUnit/Mockito et Testcontainers. Il demande aussi de **vérifier, au démarrage du projet, que Spring Boot et Spring Cloud sont officiellement compatibles avec Java 27**.

Il ne précise pas plusieurs choix pourtant structurants :
- comment émettre les JWT ;
- comment sérialiser les événements ;
- quelle variante de Gateway utiliser ;
- comment mettre en place les logs structurés et le traçage ;
- quels outils de test utiliser en plus de JUnit et Mockito ;
- quel outillage qualité adopter.

Cet ADR fixe ces choix pour l'ensemble des microservices.

Il tient compte de quatre contraintes :
- les services sont indépendants et **aucune classe métier n'est partagée** ;
- chaque technologie doit répondre à un besoin réel ;
- le projet est pédagogique : il faut comprendre ce qu'on utilise plutôt que le masquer ;
- c'est un domaine financier : exactitude des montants, atomicité, traçabilité.

### État des versions au 2026-11-16

- **Java 27** est sorti le 15 septembre 2026. Ce n'est **pas une version LTS** : la LTS actuelle est Java 25, la prochaine est attendue en septembre 2027.
- **Spring Boot 4.2** est en RC1 ; sa GA est annoncée pour le **19 novembre 2026**. Spring Boot 4.1.x est la ligne stable actuelle, mais elle est antérieure à Java 27.
- **Spring Cloud 2026.0 (Paddington)** est en milestone et repose sur Boot 4.2. La dernière version stable est 2025.1.3 (Oakwood), compatible avec Boot 4.0 et 4.1.

## 2. Décision

### 2.1 Socle

| Élément | Choix | Justification |
|---|---|---|
| JDK | **Java 27**, distribution Eclipse Temurin | Imposé par le cahier des charges. Temurin est gratuite et disponible en image Docker officielle. |
| Framework | **Spring Boot 4.2.x** | C'est la première ligne susceptible de supporter officiellement Java 27. Version patch à figer dès la GA. |
| Spring Cloud | **2026.0.x (Paddington)** | C'est le train aligné sur Boot 4.2. |
| Build | **Maven** avec le wrapper (`mvnw`) et un `pom.xml` autonome par service | Imposé. Le wrapper garantit la même version de Maven partout. Chaque service a `spring-boot-starter-parent` pour parent, et il n'y a aucun module métier commun. |
| Modèle web | **Spring Web MVC** (servlet) avec les **threads virtuels** activés (`spring.threads.virtual.enabled=true`) | C'est un modèle impératif simple à apprendre et à tester. Les threads virtuels apportent la montée en charge sans complexité réactive. JPA est bloquant de toute façon. |
| Conteneur | Dockerfile multi-stage, base `eclipse-temurin:27-jre`, jar en couches, utilisateur non-root | On garde le contrôle et la lisibilité. On choisit le Dockerfile plutôt que les Buildpacks pour que l'image reste compréhensible. |

**Plan de repli pour les versions.** Au démarrage de BF-001, on vérifie que Spring Boot 4.2 GA et Spring Cloud 2026.0 GA sont publiés et que `./mvnw verify` passe sur JDK 27 (tâches BF-001-01 et BF-001-10).
- Si Spring Cloud 2026.0 n'est pas encore en GA quand BF-004 démarre, la **Gateway seule** peut tourner temporairement sur la dernière combinaison stable. Les services sont indépendants, ce qui rend ce décalage acceptable. On réaligne la Gateway dès la GA.
- Si Java 27 pose un blocage avéré (bibliothèque incompatible), on se replie sur **Java 25 LTS**, ce qui demande un ADR dédié.

### 2.2 Sécurité

| Élément | Choix | Justification |
|---|---|---|
| Sécurité applicative | **Spring Security** | Imposé. |
| Émission des JWT (Auth Service) | **`JwtEncoder` de Spring Security**, avec Nimbus JOSE + JWT (déjà fourni par `spring-security-oauth2-jose`), en **RS256** et clé RSA 2048 bits ou plus | C'est natif dans Spring, sans dépendance de plus. La signature est asymétrique, comme l'exige la section 8.1. |
| Validation des JWT (services et Gateway) | **`spring-boot-starter-oauth2-resource-server`** avec `jwk-set-uri` pointant vers l'endpoint JWKS de l'Auth Service | Chaque service valide lui-même le token. La Gateway n'est donc pas l'unique frontière de sécurité. |
| Hachage des mots de passe | **`DelegatingPasswordEncoder`**, avec BCrypt par défaut | Imposé (BCrypt ou Argon2). Le préfixe `{bcrypt}` permet de migrer plus tard vers Argon2 sans casser les comptes existants. |
| Protection contre le brute force | Implémentation maison dans l'Auth Service (compteur d'échecs en base et verrouillage temporaire), complétée par le rate limiting de la Gateway | C'est un besoin simple : pas de bibliothèque dédiée. |

### 2.3 API Gateway

| Élément | Choix | Justification |
|---|---|---|
| Gateway | **Spring Cloud Gateway Server Web MVC** | On garde le même modèle (servlet et threads virtuels) que les autres services, donc un seul paradigme à apprendre. Les fonctions nécessaires (routage, filtres, CORS) sont disponibles. |
| Rate limiting | **Bucket4j** (filtre de rate limit de Gateway MVC), en mémoire | Une instance unique en V1 suffit. On passera à un stockage distribué (Redis) seulement si on déploie plusieurs instances de Gateway. Ajouter Redis aujourd'hui ne répondrait à aucun besoin réel. |

### 2.4 Persistance

| Élément | Choix | Justification |
|---|---|---|
| ORM | **Spring Data JPA / Hibernate** | Imposé. |
| Base | **PostgreSQL** (driver JDBC officiel), pool **HikariCP** (par défaut) | Imposé. |
| Migrations | **Flyway**, avec le module `flyway-database-postgresql` | Imposé. Migrations versionnées en SQL pur. |
| JSONB (outbox) | Support natif de Hibernate (`@JdbcTypeCode(SqlTypes.JSON)`) | Aucune bibliothèque tierce nécessaire. |
| Verrouillage | `@Lock(PESSIMISTIC_WRITE)` (`SELECT … FOR UPDATE`) sur les comptes lors des mouvements, avec `@Version` en complément | Il faut une garantie forte pour les débits concurrents (section 4.3). |
| Montants | `BigDecimal` mappé en `NUMERIC(19,4)`. Un test ArchUnit interdit `double` et `float` dans les packages du domaine | Imposé. La règle est vérifiée automatiquement. |

### 2.5 Mapping, validation et API REST

| Élément | Choix | Justification |
|---|---|---|
| DTO et événements | **Java records** | Ils sont immuables et concis, et ne demandent pas de génération de code. |
| Mapping | **MapStruct** | Imposé. Le mapping est vérifié à la compilation. |
| Lombok | **Non retenu** | Les records couvrent les DTO. Lombok s'appuie sur des API internes de javac et met régulièrement du temps à supporter un nouveau JDK, ce qui est un risque direct avec Java 27 (non-LTS). Il complique aussi la cohabitation avec MapStruct. |
| Validation | **Spring Validation** (Jakarta Bean Validation) | Imposé. |
| Erreurs HTTP | **`ProblemDetail`** (RFC 9457), avec un `@RestControllerAdvice` par service | C'est le format standard et natif de Spring. |
| Clients REST internes | **`RestClient`** avec les interfaces HTTP (`@HttpExchange`) | Natif et déclaratif, sans OpenFeign. |
| Résilience des appels REST | **`@Retryable` et `@ConcurrencyLimit` de Spring Framework 7** pour les cas simples ; **Resilience4j** (via Spring Cloud CircuitBreaker) seulement si un vrai besoin de circuit breaker apparaît | On commence par l'outil le plus simple. |
| Documentation API | **springdoc-openapi** (version compatible Boot 4.x), avec Swagger UI par service | C'est un critère de validation de la section 15. |
| Sérialisation des montants | Jackson configuré pour écrire les `BigDecimal` **en chaîne** (`"500.00"`) | Un nombre JSON est lu comme un flottant en JavaScript. Une chaîne évite toute perte de précision côté Angular (voir ADR-002). |

### 2.6 Kafka

| Élément | Choix | Justification |
|---|---|---|
| Client | **Spring Kafka** (version gérée par Boot) | Imposé. |
| Sérialisation | `StringSerializer` et `StringDeserializer` (le JSON est produit et lu explicitement avec Jackson dans notre code), enveloppés dans `ErrorHandlingDeserializer` | Le `JsonSerializer` de Spring ajoute des en-têtes de type (`__TypeId__`) qui portent le nom de classes Java. Cela couple producteurs et consommateurs, alors que le cahier des charges interdit le partage de classes. Les contrats JSON versionnés de `contracts/` restent ainsi la seule source de vérité. Cela convient aussi à l'outbox, qui stocke déjà le JSON. |
| Producteur | `acks=all` et `enable.idempotence=true` | Fiabilité de publication. |
| Consommateur | `enable.auto.commit=false`, ack contrôlé par le container (`AckMode.RECORD` ou manuel) | L'offset n'est validé qu'après l'effet métier (section 5.5). |
| Erreurs | `DefaultErrorHandler` avec backoff exponentiel borné et `DeadLetterPublishingRecoverer` vers `<topic>.DLT`. Les exceptions métier permanentes sont déclarées non retentables | Couvre la section 5.5. Le choix entre retry bloquant et retry non bloquant (`@RetryableTopic`) sera tranché dans BF-013. |
| Outbox | Publisher maison (`@Scheduled`, `SELECT … FOR UPDATE SKIP LOCKED`) | C'est l'objectif pédagogique de BF-011. `SKIP LOCKED` rend inutile un verrou distribué de type ShedLock. Debezium/CDC est écarté en V1 parce qu'il masquerait le mécanisme. Il pourra être étudié ensuite. |
| Streams (BF-022) | **Kafka Streams** (`spring-kafka` et `kafka-streams`) | Imposé, en phase ultérieure. |
| Avro / Schema Registry | Reporté (section 5.3) | JSON d'abord. |

### 2.7 Notifications

| Élément | Choix |
|---|---|
| Envoi | `spring-boot-starter-mail` vers Mailpit (SMTP) |
| Templates | **Thymeleaf**, utilisé uniquement comme moteur de templates d'email (pas de rendu web) |

### 2.8 Observabilité

| Élément | Choix | Justification |
|---|---|---|
| Santé et métriques | **Spring Boot Actuator** et **Micrometer**, avec le registre Prometheus | Imposé. |
| Traçage et correlationId | **Micrometer Tracing** (bridge OpenTelemetry). L'observation Spring Kafka est activée pour propager le contexte dans les en-têtes Kafka | Le `traceId` sert de `correlationId` de bout en bout (HTTP vers Kafka puis vers le consommateur) sans code maison. Un exporteur de traces (Tempo ou Zipkin) est facultatif et pourra être ajouté dans BF-020. |
| Logs structurés | **Logging structuré natif de Spring Boot** (`logging.structured.format.console=ecs`) | Natif, sans logstash-encoder. On ajoute `transactionId` au MDC. |
| Métriques Kafka | Métriques client Micrometer, et `kafka-exporter` côté infrastructure pour le lag des consumer groups | Visualiser le lag est demandé (section 10). |

### 2.9 Tests

| Outil | Utilisation |
|---|---|
| **JUnit Jupiter** (version gérée par Boot 4 : JUnit 6, successeur direct de JUnit 5 avec la même API Jupiter) | Tests unitaires. Le cahier des charges cite « JUnit 5 » ; la version 6 est la version alignée sur Boot 4. |
| **Mockito** | Mocks (imposé). |
| **AssertJ** | Assertions lisibles (fourni par `spring-boot-starter-test`). |
| **Testcontainers** (PostgreSQL, Kafka) avec `@ServiceConnection` | Tests d'intégration ciblés (imposé). |
| **Awaitility** | Assertions sur les traitements asynchrones (consommateurs Kafka, outbox). |
| **ArchUnit** | Règles d'architecture : couches, pas de `double` ou `float` dans le domaine, pas de dépendance du domaine vers l'infrastructure. |
| **WireMock** | Simulation des appels REST entre services. |
| **spring-kafka-test** | Utilitaires Kafka. Kafka Streams est testé avec `TopologyTestDriver`. |
| **JaCoCo** | Couverture de code, publiée en CI. |

### 2.10 Qualité et outillage

| Outil | Utilisation |
|---|---|
| **Spotless** (format google-java-format, style AOSP) | Formatage vérifié en CI. Il faut vérifier sa compatibilité avec le JDK 27 dès l'installation, puisqu'il dépend des API de javac. **Précision (BF-001-07)** : palantir-java-format 2.103.0 est incompatible avec le JDK 27 (`NoSuchFieldError` sur `JCCompilationUnit.endPositions`, voir palantir/palantir-java-format#1785). On retient donc google-java-format 2.0.0 en style AOSP (indentation de 4 espaces, 100 colonnes). |
| **Dependabot** (GitHub) | Mises à jour et alertes de sécurité sur les dépendances. |
| **GitHub Actions** | CI/CD (voir BF-023). |

## 3. Alternatives écartées

| Alternative | Raison du rejet |
|---|---|
| Java 25 LTS | C'est plus stable, mais le cahier des charges impose Java 27. Cette option reste le repli documenté. |
| Spring WebFlux pour les services | Il ajoute une complexité réactive sans gain réel avec JPA bloquant et les threads virtuels. |
| Spring Cloud Gateway WebFlux et RequestRateLimiter Redis | Cela oblige à introduire Redis sans besoin réel en V1. Ce serait aussi un second paradigme à apprendre. |
| Keycloak / Spring Authorization Server | Disproportionné pour le besoin (`/auth/login` simple), et le cahier des charges demande d'implémenter l'Auth Service. |
| JJWT | C'est une dépendance redondante, puisque Nimbus est déjà fourni par Spring Security. |
| Lombok | Voir 2.5 : risque de compatibilité avec un JDK récent, et les records suffisent. |
| Liquibase | Flyway est imposé et plus simple (SQL pur). |
| ModelMapper | Mapping par réflexion, erreurs détectées seulement à l'exécution. MapStruct est imposé. |
| OpenFeign | `RestClient` et `@HttpExchange` sont natifs et suffisants. |
| `JsonSerializer` de Spring Kafka avec en-têtes de type | Il couple les services par les noms de classes Java (voir 2.6). |
| Debezium (CDC) pour l'outbox | Il masque le mécanisme pédagogique et alourdit l'infrastructure. Il pourra être étudié plus tard. |
| H2 pour les tests | Son comportement diffère de PostgreSQL (verrous, JSONB, `SKIP LOCKED`). On utilise Testcontainers. |

## 4. Conséquences

**Positives**
- Une stack homogène : servlet et threads virtuels partout, Gateway comprise.
- Un minimum de dépendances tierces, puisque la plupart des besoins sont couverts nativement par Spring.
- Des contrats d'événements découplés des classes Java.
- Les règles financières (pas de `double`, couches) sont vérifiées automatiquement par ArchUnit.

**Négatives et risques**
- **Java 27 n'est pas une LTS** : le support communautaire sera court. Il faudra monter en version (Java 29 LTS) dans la durée du projet.
- **Spring Boot 4.2 et Spring Cloud 2026.0 sont très récents** : il faut s'attendre à des correctifs dans les premières versions patch, et la documentation communautaire sera rare au début.
- Certains outils qui dépendent du compilateur (Spotless, MapStruct, JaCoCo) peuvent avoir besoin d'une version récente pour Java 27. On le vérifie dans BF-001.
- Sans Lombok, il faut écrire à la main les getters et setters des entités JPA, ce qui reste acceptable.
- Le JSON est sérialisé et désérialisé à la main pour Kafka : un peu plus de code, en contrepartie d'un découplage explicite.

## 5. Actions de suivi
- [ ] BF-001-01 : figer les versions exactes après les GA de Boot 4.2 et Spring Cloud 2026.0, puis mettre à jour cet ADR (statut « Accepté »).
- [ ] Vérifier la compatibilité JDK 27 de MapStruct, Spotless, JaCoCo, springdoc-openapi et ArchUnit.
- [ ] Créer un squelette de service de référence qui applique cet ADR, et le dupliquer (sans le partager) pour chaque service.
