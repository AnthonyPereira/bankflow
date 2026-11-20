# Déploiement sur le serveur maison

BankFlow est publié sur `https://bankflow.anthonypereira.dpdns.org`. Chaque fusion dans `main` déclenche la CI et le workflow `Déploiement`. Celui-ci attend la fin de la CI du même commit, puis, **seulement si elle est verte**, construit les images et les publie sur GHCR. Le dépôt privé `AnthonyPereira/deploy-platform` lance ensuite la mise en ligne depuis le runner du serveur. Un lancement manuel de `Déploiement` (Run workflow, sur `main`) reconstruit le dernier commit de `main`, avec la même attente de sa CI.

### Conditions de départ

- **Toute la CI doit réussir**, y compris le job `sonarqube` : il attend la Quality Gate SonarCloud (`sonar.qualitygate.wait=true`) et échoue si elle est rouge. Une Quality Gate rouge ou une panne de SonarCloud sur `main` bloque donc la mise en ligne, même quand les tests sont verts (marche à suivre dans le [dépannage](#dépannage)). Ce job ne tourne que si la variable `SONAR_CI_ENABLED` vaut `true` (voir `sonarqube.md`).
- **Le job `CI verte sur main` attend la CI du commit poussé** (40 minutes au plus) en interrogeant l'API GitHub. Si la CI échoue ou n'a pas fini à temps, le run s'arrête sans rien publier. Il n'utilise pas `workflow_run`, qui s'exécute avec les secrets dans le contexte d'un run pouvant venir d'une PR : le checkout ne récupère que le commit poussé sur `main`.
- **Les déploiements passent l'un après l'autre** (`concurrency: deploiement`). Si plusieurs commits arrivent sur `main` coup sur coup, GitHub ne garde qu'un run en attente : les runs intermédiaires sont annulés et le dernier commit l'emporte. Comme la CI annule aussi son run précédent sur `main`, le run de déploiement d'un commit vite remplacé finit en rouge (« La CI de <sha> a échoué (cancelled) ») : c'est attendu.
- Le lancement manuel (`workflow_dispatch`) obéit à la même règle : il attend une CI verte sur push pour le dernier commit de `main`, job `sonarqube` compris.

Ce dépôt reste public : **n'y enregistrez jamais de runner**, car une PR de fork pourrait exécuter du code sur le serveur. Le compose de production se trouve dans `apps/bankflow/` du dépôt `deploy-platform`.

Seul le frontend est routé par Traefik. Il relaie `/api` vers la Gateway, et les autres conteneurs restent sur le réseau interne.

La MVP 1 ne déploie que le frontend, la Gateway, l'Auth Service, PostgreSQL et Kafka (avec `kafka-init`) : le serveur n'a que 8 Go de mémoire. Customer, Banking, Notification et Analytics ne sont ni construits ni lancés, et la Gateway répond 503 sur leurs routes. Chaque MVP suivante ajoutera son service à la matrice de `deploy.yml` et au compose de `deploy-platform`.

L'adresse du visiteur (quotas de la Gateway) vient de l'en-tête `CF-Connecting-IP`. Nginx ne le croit que s'il est relayé depuis un réseau privé (Traefik) : Traefik ne doit donc être joignable que par le tunnel Cloudflare.

## Première activation

Ces étapes se font une seule fois, dans cet ordre.

### 1. Sur GitHub

1. Créer un jeton **classic** avec le seul droit `read:packages`, puis l'ajouter au dépôt `deploy-platform` dans le secret `GHCR_PULL_TOKEN`.
2. Créer un jeton **fine-grained** limité au dépôt `deploy-platform`, avec le droit `Contents: Read and write`, puis l'ajouter au dépôt `bankflow` dans le secret `DEPLOY_DISPATCH_TOKEN`.
3. Copier le jeton du runner : `deploy-platform` → Settings → Actions → Runners → New self-hosted runner → Linux x64, valeur après `--token`. Il reste valable une heure.

### 2. Sur le serveur

```bash
ssh anthony@192.168.1.25
sudo rm -f /etc/sudoers.d/claude-temp
ajouter-runner AnthonyPereira/deploy-platform <jeton>
```

Le runner doit apparaître en vert dans Settings → Actions → Runners du dépôt `deploy-platform`.

### 3. Premier déploiement

Fusionner la release dans `main`. **Ce premier déploiement échoue normalement** dans `deploy-platform` avec un message `à définir dans ~/apps/bankflow/.env` : `deploy-app` vient de créer le fichier `.env`, mais les secrets n'y sont pas encore.

### 4. Secrets

Sur le serveur, remplacer l'adresse email puis exécuter ce bloc. Il n'écrase jamais une valeur existante.

```bash
cd ~/apps/bankflow
ADMIN_EMAIL_VOULU="admin@exemple.fr"

for nom in POSTGRES_PASSWORD AUTH_DB_PASSWORD CUSTOMER_DB_PASSWORD BANKING_DB_PASSWORD \
           NOTIFICATION_DB_PASSWORD ANALYTICS_DB_PASSWORD ADMIN_PASSWORD; do
  grep -q "^$nom=" .env || echo "$nom=$(openssl rand -hex 24)" >> .env
done
grep -q '^ADMIN_EMAIL=' .env || echo "ADMIN_EMAIL=$ADMIN_EMAIL_VOULU" >> .env
grep -q '^JWT_PRIVATE_KEY=' .env || echo "JWT_PRIVATE_KEY=$(openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 | openssl pkcs8 -topk8 -nocrypt -outform DER | base64 -w0)" >> .env
chmod 600 .env
grep '^ADMIN_' .env
```

Noter le mot de passe administrateur affiché dans un gestionnaire de mots de passe.

### 5. Relancer

`deploy-platform` → Actions → Déploiement → Run workflow, sur `main`, avec l'application `bankflow` et le SHA complet du commit de `main`.

### 6. Vérifier

| Vérification | Résultat attendu |
|---|---|
| `https://bankflow.anthonypereira.dpdns.org` | Page de connexion |
| Inscription puis connexion | Tableau de bord |
| `https://bankflow.anthonypereira.dpdns.org/actuator/health` | Page de l'application, sans données JSON |
| `cd ~/apps/bankflow && docker compose -p bankflow ps` | Tous les services `healthy`, `kafka-init` terminé |

## Au quotidien

| Besoin | Commande |
|---|---|
| Voir l'état | `cd ~/apps/bankflow && docker compose -p bankflow ps` |
| Lire les logs | `cd ~/apps/bankflow && docker compose -p bankflow logs -f auth-service` |
| Consommation mémoire | `docker stats --no-stream` |
| Redéployer sans changer le code | `deploy-platform` → Actions → Déploiement → Run workflow |

Ne modifiez pas `~/apps/bankflow/docker-compose.yml` sur le serveur : il est remplacé à chaque déploiement. Seul `.env` y est conservé.

## Retour arrière

Dans `deploy-platform` → Actions → Déploiement → Run workflow, indiquer `bankflow` et le SHA complet de la dernière version saine. Corrigez ensuite le problème avec un hotfix selon `CONTRIBUTING.md`, pour que `main` ne redéploie pas la version fautive.

Une migration Flyway déjà appliquée n'est pas annulée par ce retour arrière. Sauvegardez la base avant une release qui modifie le schéma :

```bash
cd ~/apps/bankflow
mkdir -p ~/sauvegardes
docker compose -p bankflow exec -T postgres pg_dump -U bankflow_admin auth_db > ~/sauvegardes/bankflow-auth-$(date +%F).sql
```

## Dépannage

| Symptôme | Cause probable |
|---|---|
| Le job `mise en ligne` reste en attente | Runner arrêté ou serveur éteint |
| `à définir dans ~/apps/bankflow/.env` | Secret manquant, voir l'étape 4 |
| `denied` sur `ghcr.io` | Secret `GHCR_PULL_TOKEN` absent ou expiré |
| `demande de mise en ligne` échoue | Secret `DEPLOY_DISPATCH_TOKEN` absent ou expiré |
| Job `sonarqube` rouge sur `main`, tests verts | Quality Gate rouge : ouvrir le lien SonarCloud du job, corriger le code par un hotfix (`CONTRIBUTING.md`) ; la fusion du correctif relance le déploiement |
| Job `sonarqube` en erreur ou en délai dépassé sans Quality Gate | Panne de SonarCloud : consulter la page de statut de SonarCloud, puis relancer le job depuis le run de CI (Re-run failed jobs). Une fois la CI verte, relancer aussi `Déploiement` (Re-run ou lancement manuel) : son job `CI verte sur main` a déjà échoué et ne repart pas seul |
| `La CI de <sha> a échoué` | Le commit de `main` n'a pas de CI verte (job `sonarqube` compris) : la rendre verte, puis relancer `Déploiement` |
| `La CI de <sha> n'est pas terminée après 40 minutes` | CI bloquée ou très lente : vérifier le run de CI, puis relancer `Déploiement` une fois qu'elle est verte |
| Adresse en 404 | Le conteneur `frontend` n'est pas sur le réseau `web` |
| Connexion refusée en 403 | `APP_HOST` différent de l'adresse publique |

Ne changez pas `JWT_PRIVATE_KEY` sans raison : toutes les sessions en cours seraient invalidées.
