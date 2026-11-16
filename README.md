# BankFlow

Plateforme bancaire **fictive** et **pédagogique** construite en microservices événementiels.

- **Backend** : Java 27, Spring Boot 4.2, Spring Cloud 2026.0, Spring Kafka, PostgreSQL ([ADR-001](docs/adr/ADR-001-stack-backend.md))
- **Frontend** : Angular 22, Angular Material, Signals ([ADR-002](docs/adr/ADR-002-stack-frontend.md))
- **Infrastructure** : Docker Compose, Kafka (KRaft), Kafka UI, Mailpit, Prometheus, Grafana

## Documentation

| Document | Contenu |
|---|---|
| [Cahier des charges](Cahier_des_charges_technique_BankFlow.docx) | Spécifications techniques de référence (v1.1) |
| [BACKLOG.md](BACKLOG.md) | Lots BF-001 à BF-023 et leurs tâches |
| [docs/adr/](docs/adr/) | Décisions d'architecture (ADR) |
| [CONTRIBUTING.md](CONTRIBUTING.md) | Conventions Git, commits et code |

## Structure du dépôt (cible)

```
bankflow/
├── services/      # un projet Spring Boot indépendant par microservice
│   ├── api-gateway/
│   ├── auth-service/
│   ├── customer-service/
│   ├── banking-service/
│   ├── notification-service/
│   └── analytics-fraud-service/
├── frontend/      # application Angular (bankflow-web)
├── infra/         # Docker, Kafka, PostgreSQL, Prometheus, Grafana
├── contracts/     # contrats d'événements Kafka versionnés (JSON Schema)
└── docs/          # ADR, architecture, runbooks
```

## Prérequis

- JDK 27 (Eclipse Temurin)
- Node.js LTS supporté par Angular 22
- Docker Desktop (Docker Compose v2)
- Git

## Démarrage

> Disponible à partir du lot BF-002.

```bash
cp .env.example .env   # puis renseigner les valeurs
docker compose up -d
```

## Avertissement

Projet d'apprentissage : aucune connexion à de vraies banques, aucune donnée réelle.
