# BankFlow Web

Application Angular 22 de BankFlow. Les choix techniques sont décrits dans l'[ADR-002](../../docs/adr/ADR-002-stack-frontend.md).

## Icônes

Les icônes sont des fichiers SVG [Material Symbols](https://github.com/google/material-design-icons) (licence Apache 2.0) copiés dans `public/icons`. Pour en ajouter une, copiez son fichier depuis le paquet `@material-symbols/svg-400`, puis utilisez `<mat-icon svgIcon="nom" />`.

## Commandes

```bash
npm ci                 # installe les dépendances
npm start              # lance l'application sur http://localhost:4200
npm test               # lance les tests Vitest en continu
npm run test:ci        # lance les tests une seule fois
npm run lint           # vérifie le code et les templates
npm run format:check   # vérifie le formatage
npm run build          # construit l'application de production
```

L'application complète est aussi servie par Nginx sur `http://localhost:8088` avec `docker compose --profile apps up -d --build`.

`npm start` relaie `/api` vers la Gateway sur `http://localhost:8080`. Il faut donc démarrer la pile avec `docker compose --profile apps up -d --build`.
