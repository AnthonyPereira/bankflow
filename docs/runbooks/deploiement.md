# Déploiement sur le serveur maison

BankFlow est publié sur `https://bankflow.anthonypereira.dpdns.org`. Chaque fusion dans `main` construit les images sur GitHub et les publie sur GHCR. Le dépôt privé `AnthonyPereira/deploy-platform` lance ensuite la mise en ligne depuis le runner du serveur.

Ce dépôt reste public : **n'y enregistrez jamais de runner**, car une PR de fork pourrait exécuter du code sur le serveur. Le compose de production se trouve dans `apps/bankflow/` du dépôt `deploy-platform`.

Seul le frontend est routé par Traefik. Il relaie `/api` vers la Gateway, et les autres conteneurs restent sur le réseau interne.

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
docker compose -p bankflow exec -T postgres pg_dump -U bankflow_admin auth_db > ~/sauvegardes/bankflow-auth-$(date +%F).sql
```

## Dépannage

| Symptôme | Cause probable |
|---|---|
| Le job `mise en ligne` reste en attente | Runner arrêté ou serveur éteint |
| `à définir dans ~/apps/bankflow/.env` | Secret manquant, voir l'étape 4 |
| `denied` sur `ghcr.io` | Secret `GHCR_PULL_TOKEN` absent ou expiré |
| `demande de mise en ligne` échoue | Secret `DEPLOY_DISPATCH_TOKEN` absent ou expiré |
| Adresse en 404 | Le conteneur `frontend` n'est pas sur le réseau `web` |
| Connexion refusée en 403 | `APP_HOST` différent de l'adresse publique |

Ne changez pas `JWT_PRIVATE_KEY` sans raison : toutes les sessions en cours seraient invalidées.
