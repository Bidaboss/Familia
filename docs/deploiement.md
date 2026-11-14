# Déployer les règles et les Cloud Functions

Cette page sert à mettre en ligne la partie serveur de Familia. Il s'agit des règles de sécurité de Firestore et des Cloud Functions qui envoient les notifications (lot 8). On le fait à la main, ensemble, la première fois. GitHub n'a aucune clé pour le faire à ta place, et c'est voulu : rien ne part en ligne sans toi.

## Ce qu'il faut une fois

1. Installer Node.js 22 sur ton ordinateur (nodejs.org).
2. Ouvrir un terminal dans le dossier `firebase` du dépôt, puis lancer :
   ```
   npm ci
   npm ci --prefix functions
   npx firebase login
   ```
   Le projet Familia (`familia-93286`) est déjà choisi dans `firebase/.firebaserc`.
3. Vérifier que le projet est bien en **offre Blaze**. Les Cloud Functions l'exigent.

## Déployer

Toujours depuis le dossier `firebase` :

```
npx firebase deploy --only functions,firestore:rules
```

- Les fonctions tournent en **Europe (europe-west1)**. C'est réglé dans le code (`functions/src/index.ts`), donc tu n'as rien à choisir.
- Au premier déploiement, Firebase active lui-même les services nécessaires (Cloud Functions, Cloud Build, Cloud Scheduler pour le rappel de 18 h). Si un message demande d'accepter, réponds oui.
- Avant de déployer, la CI de la pull request doit être verte : elle a déjà testé les règles et les fonctions.

## Vérifier après le déploiement

1. Dans la console Firebase, ouvre **Functions** : cinq fonctions apparaissent, `onEventCreated`, `onEventUpdated`, `onEventDeleted`, `onMemberDeleted` et `dayBeforeReminder`.
2. Ajoute un événement depuis un téléphone. L'autre téléphone reçoit « Nouvel événement » en quelques secondes.
3. Le soir, à 18 h, ceux qui ont répondu « Je viens » ou « Peut-être » à un événement du lendemain reçoivent le rappel.

## Vérifier l'alerte de budget

Familia doit rester gratuite. L'alerte à 1 € te prévient bien avant tout coût réel.

1. Ouvre console.cloud.google.com, puis **Facturation** puis **Budgets et alertes**.
2. Vérifie qu'un budget « Familia » existe, avec un montant de 1 € et des alertes à 50 %, 90 % et 100 %, envoyées sur ton adresse.
3. S'il n'existe pas, crée-le avec ces valeurs.

Pour une famille de 13 personnes, les notifications et le rappel restent dans la part gratuite. Les fonctions sont limitées à une seule instance, ce qui plafonne le coût même en cas de bug.

## Revenir en arrière

Si une nouvelle version pose problème, redéploie la version précédente : reviens sur le commit d'avant dans `main` (`git checkout <commit>`), puis relance la commande de déploiement.
