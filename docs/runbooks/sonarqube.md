# Runbook : analyse SonarQube Cloud

La CI envoie à SonarQube Cloud le code Java et TypeScript, les rapports JaCoCo et la couverture Vitest. Le job `sonarqube` reste ignoré tant que la variable `SONAR_CI_ENABLED` ne vaut pas `true`.

## Activation

1. Dans SonarQube Cloud, ouvrir le projet puis **Administration → Analysis Method** et désactiver **Automatic Analysis**. Les deux modes ne peuvent pas fonctionner ensemble.
2. Créer un jeton dans **My Account → Security**.
3. Dans GitHub, ouvrir **Settings → Secrets and variables → Actions** :
   - onglet **Secrets** : ajouter `SONAR_TOKEN` avec ce jeton ;
   - onglet **Variables** : ajouter `SONAR_CI_ENABLED` avec la valeur `true`.
4. Relancer la CI, puis vérifier que le job `sonarqube` est vert et que le projet affiche du Java et une couverture.

## Bloquer une fusion

Dans **Settings → Branches**, protéger `develop` et `main` en exigeant le contrôle `SonarCloud Code Analysis`.

## Limites

- Les PR de forks et de Dependabot ne lancent pas l'analyse, car elles n'ont pas accès au jeton.
- Le support de Java 27 par JaCoCo peut rester expérimental : contrôler la couverture après chaque montée de version.
