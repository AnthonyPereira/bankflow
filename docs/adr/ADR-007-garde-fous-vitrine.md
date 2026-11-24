# ADR-007 — Garde-fous de la vitrine publique

| | |
|---|---|
| **Statut** | Accepté (2026-11-23) |
| **Date** | 2026-11-23 |
| **Lots concernés** | BF-007 (comptes), BF-008 (opérations), BF-009 (virements, données de démonstration), BF-016-15 (comptes de démo) |
| **Complète** | ADR-005 (rôle de l'administrateur) ; BF-016-15 (comptes de démo de l'Auth Service) |

## 1. Contexte

BankFlow est en ligne sur `https://bankflow.anthonypereira.dpdns.org`, et les identifiants de démonstration sont **publics** : n'importe qui peut se connecter en `demo.client` ou `demo.admin`. L'application doit rester utilisable et ne pas se remplir de données absurdes, sans que l'on surveille la base.

## 2. Décision

- **Plafond de 10 000,00 par opération** (dépôt, retrait, virement), avec au plus 2 décimales.
- **Au plus 5 comptes actifs par client** (409 `ACCOUNT_LIMIT_REACHED`). La limite s'applique aussi à l'ouverture par un administrateur.
- **Les comptes de démo ne sont jamais suspendus ni désactivés** : un visiteur ne peut pas bloquer la vitrine pour les suivants.
- **Données de `demo.client` recréées** au démarrage du Banking Service si `demo.client` n'a plus aucun compte actif : un « Compte courant » avec un historique (dépôts, retraits, virement) et un « Livret épargne ». Elles passent par les mêmes règles métier que les autres opérations.
- Si `DEMO_ACCOUNTS_ENABLED=false`, **rien n'est créé**.

Le mécanisme est idempotent : tant que `demo.client` a au moins un compte actif, rien n'est recréé.

## 3. Alternatives écartées

| Alternative | Raison du rejet |
|---|---|
| Réinitialisation périodique de toute la base | Détruit aussi les comptes réels de l'utilisateur, et contredit l'immuabilité du ledger (ADR-006). |
| Comptes de démo en lecture seule | La vitrine ne montrerait ni dépôt, ni retrait, ni virement : l'intérêt de la démonstration disparaît. |

## 4. Conséquences

**Positives**
- La vitrine reste utilisable sans intervention : un visiteur qui clôture tout retrouve des données au redémarrage suivant.
- Les montants et le nombre de comptes restent bornés.

**Négatives et risques**
- Les données de démo ne sont restaurées qu'au redémarrage du Banking Service, pas en continu.
- Le solde de `demo.client` peut dériver entre deux redémarrages, puisque tout le monde peut y agir.
- Le plafond s'applique aussi aux vrais comptes.

## 5. Mise à jour du 2026-11-23 : garde-fou de `demo.admin`

Le mot de passe de `demo.admin` est public : n'importe quel visiteur peut s'y connecter. Un administrateur ordinaire verrait les données personnelles des vrais inscrits (nom, email, téléphone, adresse) et pourrait suspendre leur compte ou agir sur leurs comptes bancaires.

**Garde-fou** : `demo.admin` est un administrateur **restreint aux clients de démonstration** (voir ADR-005, section 5). Le jeton porte `demo = true` ; le Customer Service et le Banking Service ne lui montrent que les clients marqués `demo`, et répondent 404 pour tout le reste. Le vrai administrateur, dont le mot de passe reste secret, n'est pas limité.

**Limites** : `demo.admin` voit les clients de démonstration en entier, ce qui est voulu. Le résumé global de la banque ne contient que des totaux. La limitation de débit de la Gateway s'applique aussi aux opérations (dépôt, retrait, ouverture, clôture, compensation) : 60 par minute et par IP.

**Virements de `demo.client`** (BF-009-10) : les virements sont limités à 10 par minute et par utilisateur. Tous les visiteurs connectés en `demo.client` partagent donc ce quota, et un seul visiteur peut le consommer pour les autres pendant une minute. Ce compromis est assumé : exempter les comptes de démonstration, dont le mot de passe est public, rouvrirait le sondage d'IBAN que cette limite doit empêcher.
