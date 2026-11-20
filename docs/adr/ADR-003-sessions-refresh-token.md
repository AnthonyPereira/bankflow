# ADR-003 — Sessions et refresh token

| | |
|---|---|
| **Statut** | Accepté (2026-11-18) |
| **Date** | 2026-11-18, complété le 2026-11-20 (durée de vie absolue, section 2.4) |
| **Lots concernés** | BF-005 (Auth Service), BF-004 (Gateway), BF-016 (frontend) |
| **Complète** | ADR-001 section 2.2 et ADR-002 section 2.6 |

## 1. Contexte

Le backlog laisse ouverte la question du refresh token. Un JWT d'accès doit rester court, car il ne peut pas être révoqué avant son expiration. Sans refresh token, l'utilisateur devrait se reconnecter à chaque rechargement de page, puisque l'ADR-002 interdit de stocker le JWT dans `localStorage`.

## 2. Décision

### 2.1 Deux jetons aux rôles distincts

| | Access token | Refresh token |
|---|---|---|
| **Format** | JWT signé en RS256 | 256 bits aléatoires, opaques |
| **Durée de vie** | 15 minutes | 7 jours, et 30 jours au plus pour la session (section 2.4) |
| **Stockage côté client** | Mémoire de l'application Angular | Cookie `HttpOnly` |
| **Stockage côté serveur** | Aucun | Empreinte SHA-256 dans `auth_db` |
| **Révocable** | Non, il expire seul | Oui |

### 2.2 Cookie du refresh token

| Attribut | Valeur | Raison |
|---|---|---|
| Nom | `bankflow_refresh` | |
| `HttpOnly` | Oui | JavaScript ne peut pas le lire, même en cas de faille XSS. |
| `Secure` | Oui, sauf profil `dev` | Le cookie ne circule qu'en HTTPS. |
| `SameSite` | `Strict` | Un autre site ne peut pas déclencher un refresh avec ce cookie. |
| `Path` | `/api/auth` | Le cookie n'est envoyé qu'aux routes d'authentification. |
| `Max-Age` | 7 jours | Identique à la durée de vie du jeton, plus court en fin de session (section 2.4). |

### 2.3 Rotation et détection de réutilisation

- Chaque appel à `/auth/refresh` révoque le jeton présenté et en émet un nouveau dans la même **famille**.
- Présenter un jeton déjà révoqué signale un vol possible : **toute la famille est révoquée** et l'utilisateur doit se reconnecter.
- Une **tolérance de 10 secondes** s'applique juste après la rotation : la requête est refusée sans révoquer la famille. Deux onglets qui se rafraîchissent en même temps ne déconnectent donc pas l'utilisateur.
- `/auth/logout` révoque la famille et efface le cookie.

### 2.4 Durée de vie absolue d'une session

Sans borne, chaque rotation repartirait sur 7 jours : un utilisateur (ou un voleur de cookie) qui rafraîchit au moins une fois par semaine garderait la même session indéfiniment.

- Chaque jeton porte la date d'ouverture de sa famille (`refresh_tokens.family_created_at`), fixée à la connexion et recopiée à chaque rotation.
- Une session vit au plus **30 jours** depuis la connexion, quel que soit le nombre de rotations (propriété `bankflow.refresh.max-session-age`, `P30D` par défaut).
- Au-delà, `/auth/refresh` répond comme pour un jeton expiré : 401 et cookie effacé. L'utilisateur doit se reconnecter.
- Un jeton émis près de la fin de la session expire à la fin de la session, et non 7 jours plus tard. Le `Max-Age` du cookie suit la même échéance.

### 2.5 Clé de signature

La clé privée RSA est fournie par la variable `JWT_PRIVATE_KEY`. Elle est obligatoire avec le profil `prod-sim`. En développement et en test, une clé éphémère est générée au démarrage si la variable est vide.

## 3. Alternatives écartées

| Alternative | Raison du rejet |
|---|---|
| Pas de refresh token | Une reconnexion à chaque rechargement de page dégrade trop l'expérience. |
| Refresh token dans `localStorage` | Il serait lisible par toute faille XSS. |
| JWT comme refresh token | Il faudrait quand même une table pour le révoquer, sans aucun gain. |
| Stocker le jeton en clair | Une fuite de la base donnerait accès à toutes les sessions. |
| Rotation sans tolérance | Deux onglets ouverts provoqueraient de fausses alertes de vol. |
| Rotation sans durée de vie absolue | Une session rafraîchie chaque semaine ne finirait jamais. |

## 4. Conséquences

**Positives**
- Une session survit au rechargement de la page sans exposer de jeton durable à JavaScript.
- Le vol d'un refresh token est détecté dès que le jeton volé ou l'original est réutilisé.

**Négatives et risques**
- Un access token volé reste valable jusqu'à 15 minutes.
- Un vol de refresh token non détecté donne accès au compte jusqu'à 30 jours au plus.
- Un utilisateur actif doit se reconnecter au moins tous les 30 jours.
- La table des refresh tokens grandit : les jetons expirés sont purgés chaque nuit (`bankflow.refresh.purge-cron`).
- Un redémarrage avec une clé éphémère invalide les access tokens en cours, ce qui reste acceptable hors `prod-sim`.
