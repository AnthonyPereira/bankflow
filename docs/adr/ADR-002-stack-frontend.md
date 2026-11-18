# ADR-002 — Stack technique frontend

| | |
|---|---|
| **Statut** | Accepté (2026-11-16). Les versions exactes restent à figer (voir §5) |
| **Date** | 2026-11-16 |
| **Lots concernés** | BF-016, BF-017, BF-018 |
| **Remplace / complète** | Section 3.2 du cahier des charges. **Cet ADR remplace PrimeNG par Angular Material** (voir 2.2). |

## 1. Contexte

Le cahier des charges impose Angular 22, TypeScript, Signals, Router, HttpClient, RxJS, PrimeNG et Vitest. Il interdit d'ajouter une bibliothèque globale d'état sans besoin concret, et rappelle que les contrôles frontend ne remplacent jamais les autorisations backend.

Plusieurs besoins de l'interface restent sans réponse dans le cahier des charges :
- graphiques de statistiques ;
- saisie de dates ;
- formatage des montants sans perte de précision ;
- stockage du token ;
- icônes ;
- tests de composants et tests de bout en bout ;
- lint et formatage.

### État au 2026-11-16

- **Angular 22** est disponible depuis juin 2026. OnPush est la stratégie par défaut, le mode zoneless est la norme, et le projet utilise TypeScript 6 et le runner de tests Vitest intégré.
- Angular 22 introduit le décorateur **`@Service()`**, raccourci de `@Injectable({ providedIn: 'root' })`, avec une migration officielle (`ng generate @angular/core:service`, disponible depuis la v22.1).
- **Angular Material** est publié par l'équipe Angular **en même temps que chaque version majeure d'Angular**.
- **PrimeNG 22** (juillet 2026) supporte Angular 22, mais **n'est plus publié sous licence MIT à partir de la v22**. Il passe sous le modèle « PrimeUI » (licence Community ou Commercial), et le dépôt GitHub public a été archivé en juin 2026. Les versions 21 et antérieures restent sous MIT.

## 2. Décision

### 2.1 Socle

| Élément | Choix | Justification |
|---|---|---|
| Framework | **Angular 22**, composants standalone, **zoneless**, OnPush | Imposé, avec les choix par défaut de la version 22. |
| Langage | **TypeScript 6** en mode `strict` (et `strictTemplates`) | Typage fort, y compris des templates. |
| Build | **`@angular/build`** (esbuild et Vite), avec le CLI Angular | C'est le builder officiel par défaut. |
| Runtime de build | **Node.js LTS** supporté par Angular 22, version fixée dans `.nvmrc` et dans la CI | Reproductibilité. |
| Gestionnaire de paquets | **npm** avec un `package-lock.json` commité | Simple et standard, sans outil supplémentaire. |
| Dev server | `ng serve` avec un `proxy.conf.json` vers la Gateway (`/api` → `http://localhost:8080`) | Évite CORS en développement. Le CORS de la Gateway reste nécessaire hors développement. |

### 2.2 Composants UI : Angular Material (au lieu de PrimeNG)

| Élément | Choix |
|---|---|
| Bibliothèque UI | **Angular Material** (thème Material 3) |
| Primitives et comportements | **Angular CDK** : overlay, a11y, layout (`BreakpointObserver`), drag & drop si besoin, et harnesses de test |
| Thème | Thème M3 personnalisé en **SCSS** (palette BankFlow, mode clair et sombre via les design tokens Material) |
| Icônes | **Material Symbols**, auto-hébergées en **fichiers SVG** dans `public/icons` (la police complète pèse 4 Mo, pas d'appel à Google Fonts au runtime) |
| Notifications UI | `MatSnackBar` encapsulé dans un `NotificationUiService` |
| Tableaux | `MatTable`, `MatPaginator` et `MatSort`, avec pagination et tri **côté serveur** |

**Pourquoi remplacer PrimeNG :**
1. **Licence** : à partir de la v22, PrimeNG n'est plus sous licence MIT. Pour rester sur Angular 22 comme l'exige le cahier des charges, il faudrait accepter une licence propriétaire (Community ou Commercial) ou rester bloqué sur PrimeNG 21, qui n'est pas aligné sur Angular 22.
2. **Pérennité et alignement des versions** : Angular Material est maintenu par l'équipe Angular et sort le même jour que chaque version majeure d'Angular. PrimeNG a historiquement mis plusieurs semaines à supporter chaque nouvelle version majeure (c'était encore le cas pour la v22).
3. **Accessibilité** : le CDK fournit la gestion du focus, ARIA et la navigation clavier. Les **component harnesses** rendent les tests de composants plus robustes.
4. **Contrepartie assumée** : Material propose moins de composants « riches » que PrimeNG (pas de graphiques, pas de tableau avec filtres intégrés). Ces besoins sont couverts par 2.3 et par une pagination côté serveur, ce qui convient de toute façon à des historiques bancaires.

### 2.3 Graphiques

| Élément | Choix | Justification |
|---|---|---|
| Graphiques (écran Statistiques, indicateurs admin) | **Chart.js** et son wrapper Angular **ng2-charts** | Léger et sous licence MIT. Il couvre largement les besoins (lignes, barres, camemberts et anneaux). Material ne fournit pas de graphiques. |

### 2.4 État, données et formulaires

| Besoin | Choix | Justification |
|---|---|---|
| Services | Décorateur **`@Service()`** (Angular 22), fourni à la racine par défaut. Les dépendances sont injectées avec **`inject()`**, sans injection par constructeur. `@Injectable` est réservé aux cas que `@Service` ne couvre pas, comme un provider configuré par `useFactory` ou un service fourni au niveau d'un composant ou d'une route. Pour ce dernier cas, `@Service({ autoProvided: false })` est la première option | C'est le nouvel idiome d'Angular 22, qui remplace `@Injectable({ providedIn: 'root' })` en plus concis. `inject()` est déjà la convention des fonctions d'intercepteurs et de guards. |
| État local et partagé | **Signals** (`signal`, `computed`, `linkedSignal`) portés par des services `@Service()` | Imposé. Pas de bibliothèque globale d'état. |
| Appels HTTP | **HttpClient**. Pour les lectures simples, les **API `resource` / `httpResource`** (si elles sont stables en v22, sinon `toSignal`) | Une API réactive qui s'intègre naturellement aux Signals. |
| Flux asynchrones complexes | **RxJS** (recherche avec `debounceTime`, annulation, refresh token) | Imposé. On le réserve aux vrais flux asynchrones. |
| Formulaires | **Reactive Forms typés**. Les **Signal Forms** sont à réévaluer selon leur statut (stable ou expérimental) dans Angular 22 au démarrage de BF-016 | C'est l'option stable et éprouvée. On ne retient pas une API expérimentale pour des formulaires financiers. |
| Bibliothèque d'état (NgRx, NgRx SignalStore…) | **Non retenue** | Conformément au cahier des charges (section 7.2). On ne la reconsidérera qu'avec un besoin concret démontré. |

### 2.5 Montants, dates et localisation

| Besoin | Choix | Justification |
|---|---|---|
| Montants | Reçus et envoyés **en chaîne décimale** (`"500.00"`, voir ADR-001 §2.5). **Aucun calcul monétaire côté front.** Affichage avec `CurrencyPipe` et la locale `fr-FR` | `number` en JavaScript est un flottant IEEE 754 : `0.1 + 0.2 !== 0.3`. Le backend fait foi pour tous les calculs. |
| Saisie de montants | Validator maison (expression régulière : 2 décimales maximum, strictement positif) | Pas besoin de bibliothèque. On n'ajouterait `big.js` que si un calcul côté front devenait réellement nécessaire. |
| Dates | **date-fns** et l'adaptateur **`@angular/material-date-fns-adapter`** pour `MatDatepicker` | Fonctions pures, tree-shakables, avec une locale `fr` correcte. L'adaptateur natif gère mal le format de saisie `dd/MM/yyyy`. |
| Langue | Application en **français uniquement**, `LOCALE_ID = 'fr-FR'` | Il n'y a pas d'exigence multilingue. On passera à `@angular/localize` le jour où ce besoin apparaît. |

### 2.6 Authentification côté client

| Élément | Choix | Justification |
|---|---|---|
| Access token (JWT) | Conservé **en mémoire** (signal dans `AuthService`), jamais dans `localStorage` | Limite l'exposition au XSS. |
| Refresh token | **Cookie `HttpOnly`, `Secure`, `SameSite=Strict`**, posé par l'Auth Service sur `/api/auth/refresh` | Le refresh reste inaccessible au JavaScript. Une session survit au rechargement de la page grâce à un appel à `/refresh` au démarrage (`provideAppInitializer`). **À confirmer dans l'ADR de BF-005** (refresh retenu ou non). |
| Lecture du rôle et de l'expiration | **jwt-decode** | Décodage base64url fiable et minuscule. **Aucune vérification de signature côté front** : cette vérification revient au backend. |
| Intercepteurs | Fonctionnels (`HttpInterceptorFn`) : JWT, `X-Correlation-Id`, refresh sur 401 (une seule requête de refresh partagée), erreurs centralisées | Exigé par la section 7.2. |
| Guards | Fonctionnels (`CanActivateFn`) : `authGuard` et `adminGuard` | Confort de navigation uniquement. L'autorisation réelle reste côté backend. |
| Bibliothèques OAuth / OIDC (angular-oauth2-oidc…) | **Non retenues** | Ce n'est pas un flux OIDC. Le besoin se résume à quelques dizaines de lignes, dont l'écriture a une valeur pédagogique. |

### 2.7 Client d'API

| Élément | Choix | Justification |
|---|---|---|
| Services et modèles | **Écrits à la main** en V1 (`shared/models`, un service par feature) | Bonne compréhension des contrats et volume limité. |
| Génération depuis OpenAPI | À évaluer dans BF-023 (`openapi-typescript` pour les types seuls) | Elle limitera la dérive entre le front et le back quand les API seront stabilisées. |

### 2.8 Tests

| Outil | Utilisation |
|---|---|
| **Vitest** (runner `@angular/build:unit-test`) | Tests unitaires (imposé). |
| **Angular Testing Library** (`@testing-library/angular`) et `@testing-library/user-event` | Tests de composants centrés sur l'utilisateur : rôles, libellés et interactions plutôt que détails internes. |
| **Component harnesses** Angular Material (CDK testing) | Interactions fiables avec les composants Material dans les tests. |
| **`HttpTestingController`** (`provideHttpClientTesting`) | Tests des services HTTP et des intercepteurs. |
| **Playwright** | **Ajout par rapport au cahier des charges.** Quelques tests de bout en bout sur les parcours critiques (inscription → connexion → virement → historique, et accès admin refusé à un client), exécutés contre `docker compose`. Seuls ces tests prouvent que l'intégration front, Gateway et services fonctionne. |

### 2.9 Qualité et outillage

| Outil | Utilisation |
|---|---|
| **angular-eslint** (avec typescript-eslint) et les règles d'accessibilité des templates | Lint du code et des templates. |
| **Prettier** | Formatage, vérifié en CI. |
| **Dependabot** | Mises à jour des dépendances npm. |

### 2.10 Livraison

| Élément | Choix |
|---|---|
| Image Docker | Multi-stage : build avec Node, puis service par **`nginxinc/nginx-unprivileged`** (non-root) |
| Configuration Nginx | Repli SPA (`try_files … /index.html`), cache long sur les fichiers hashés, en-têtes de sécurité (CSP, `X-Content-Type-Options`, `Referrer-Policy`), et proxy `/api` vers la Gateway pour rester sur la même origine |
| Configuration d'environnement | URL d'API relative (`/api`), donc la même image sert dans tous les environnements |

## 3. Alternatives écartées

| Alternative | Raison du rejet |
|---|---|
| **PrimeNG 22** | Licence non MIT depuis la v22, dépôt public archivé, support des nouvelles versions majeures d'Angular historiquement plus tardif (voir 2.2). |
| PrimeNG 21 (MIT) | Il n'est pas aligné sur Angular 22, qui est imposé. Ce serait une impasse de maintenance. |
| ng-zorro-antd | Bonne bibliothèque, mais avec une communauté surtout sinophone et une documentation moins fournie en français ou en anglais. Aucun avantage décisif sur Material. |
| Spartan / Tailwind CSS | Approche « headless » intéressante, mais trop de composants seraient à construire soi-même. Tailwind entrerait aussi en concurrence avec le theming Material. |
| NgRx / NgRx SignalStore | Interdit sans besoin concret (section 7.2). Les Signals suffisent. |
| Jest / Karma | Vitest est imposé. Karma est déprécié. |
| Cypress | Playwright est plus rapide, multi-navigateurs en natif, avec un meilleur parallélisme. |
| ngx-echarts (Apache ECharts) | Plus puissant mais nettement plus lourd. Les besoins de graphiques sont simples. |
| Moment.js | Projet en mode maintenance et non tree-shakable. date-fns est préféré. |
| ngx-translate / Transloco | Il n'y a pas d'exigence multilingue. |
| Stocker le JWT dans `localStorage` | Exposé à toute faille XSS (voir 2.6). |

## 4. Conséquences

**Positives**
- Une stack **100 % open source sous licence permissive**, alignée sur les versions d'Angular, et dont l'accessibilité est prise en charge par le CDK.
- Peu de dépendances tierces : Chart.js et ng2-charts, date-fns, jwt-decode, les bibliothèques de test et Playwright.
- Des montants exacts de bout en bout, puisqu'ils transitent en chaîne et que le front ne fait aucun calcul.
- Les tests de bout en bout couvrent ce que les tests unitaires ne peuvent pas prouver.

**Négatives et risques**
- **Écart avec le cahier des charges** (PrimeNG remplacé par Angular Material). Il faut mettre à jour le backlog (BF-016, BF-018) et l'ADR a été validé et le cahier des charges est passé en v1.1.
- Material a une esthétique « Google » marquée. Il faudra un effort de theming pour une identité bancaire.
- Il y a moins de composants prêts à l'emploi que dans PrimeNG : les tableaux filtrables et le dashboard demandent un peu plus de code.
- Le refresh token en cookie `HttpOnly` impose une configuration CORS et cookie précise sur la Gateway et l'Auth Service (dépendance avec BF-004 et BF-005).

## 5. Actions de suivi
- [ ] Vérifier dans l'API Angular 22 les options exactes de `@Service` (`autoProvided`, `factory`).
- [ ] Vérifier dans BF-016 le statut (stable ou expérimental) des Signal Forms et de `httpResource` dans Angular 22.
- [x] Figer les versions dans `package-lock.json` : Angular 22.2, Angular Material 22.3, TypeScript 6.0 et Vitest 5.1 (2026-11-18). ng2-charts sera ajouté avec BF-018.
- [x] Mettre à jour BF-016-01, BF-016-06 et BF-018-02 dans le backlog.
- [x] Mettre à jour le cahier des charges (v1.1, section 3.2).
- [ ] Aligner l'ADR de BF-005 (refresh token) sur la stratégie du cookie `HttpOnly`.
