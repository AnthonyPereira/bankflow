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

## Connexion et brute force

Un compte est verrouillé 15 minutes après 5 échecs (`bankflow.login.*`), comptes de démonstration compris. Aucun verrou de ligne ni aucune connexion à la base n'est tenu pendant le hachage bcrypt :

1. L'utilisateur est lu sans verrou. S'il est inconnu, désactivé ou verrouillé, un hachage leurre égalise le temps de réponse, puis la réponse est un 401.
2. Sinon, un `UPDATE` atomique réserve la tentative : il la compte d'avance comme un échec, verrouille le compte à la 5ᵉ, et ne réserve rien si le compte vient d'être verrouillé (hachage leurre et 401).
3. Le mot de passe est vérifié hors transaction. Un échec n'a plus rien à écrire ; un succès remet le compteur à zéro.

Des tentatives simultanées ne vérifient donc jamais plus de 5 mots de passe avant le verrouillage. En contrepartie, un même compte accepte au plus « 5 moins les échecs déjà comptés » connexions simultanées : les suivantes reçoivent un 401 le temps que l'une d'elles aboutisse, même avec le bon mot de passe. Par exemple, avec 4 échecs comptés, une 2ᵉ connexion simultanée est refusée jusqu'à ce que la première réussisse.

Une connexion réussie remet toujours le compteur à zéro, y compris les échecs comptés en parallèle par d'autres tentatives : c'est le comportement standard, assumé.

Spécifications : voir le cahier des charges et le [backlog](../../BACKLOG.md).
