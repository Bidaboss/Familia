# Déployer les règles et les Cloud Functions

Cette page sert à mettre en ligne la partie serveur de Familia. Il s'agit des règles de sécurité de Firestore et des Cloud Functions qui envoient les notifications (lot 8). Depuis la carte DEV-7, on publie avec le workflow GitHub « Publier Firebase », lancé à la main. Rien ne part en ligne sans toi.

## Ce qu'il faut une fois : la clé de publication

1. Le projet doit être en **offre Blaze**, car les Cloud Functions l'exigent.
2. Ouvre console.cloud.google.com sur le projet Familia, puis **IAM et administration** et **Comptes de service**. Touche **Créer un compte de service**.
   - Nom : `github-deploy`.
   - Rôles (bouton « Ajouter un autre rôle » entre chaque) : **Administrateur Firebase**, **Utilisateur du compte de service**, **Administrateur Cloud Scheduler**, **Administrateur Service Usage** (pour activer les services Google au premier lancement).
   - Touche **OK**.
3. Ouvre le compte `github-deploy`, onglet **Clés**, puis **Ajouter une clé**, **Créer une clé** et **JSON**. Un fichier se télécharge.
4. Dans GitHub, ouvre `Settings`, `Secrets and variables`, `Actions`, puis **New repository secret**. Le nom est `FIREBASE_SECRET_ACCOUNT`, et la valeur est le contenu complet du fichier.
5. Supprime ensuite le fichier de ton téléphone.

Cette clé peut modifier le projet Firebase. Elle ne va que dans ce secret GitHub, jamais dans le dépôt ni dans une discussion. Si elle fuit, supprime-la dans l'onglet **Clés** du compte et crée-en une autre.

## Une fois, au tout premier lancement

Ces étapes ont été faites pour le projet `familia-93286` le 14/11/2026. Il faut les refaire seulement sur un nouveau projet.

1. Active l'API de facturation : https://console.cloud.google.com/apis/library/cloudbilling.googleapis.com, puis **Activer**.
2. Dans **IAM**, puis **Accorder l'accès**, donne ces rôles aux comptes internes de Google (remplace `<numéro>` par le numéro du projet, visible dans les paramètres Firebase) :
   - `service-<numéro>@gcp-sa-pubsub.iam.gserviceaccount.com` : **Créateur de jetons du compte de service** ;
   - `<numéro>-compute@developer.gserviceaccount.com` : **Demandeur Cloud Run** et **Destinataire d'événements Eventarc**.
3. Au premier lancement, les fonctions liées à la base peuvent échouer avec « Permission denied while using the Eventarc Service Agent ». Google met quelques minutes à préparer ce service : relance simplement 5 minutes plus tard.

À chaque publication, un avertissement « Unhandled error cleaning up build images » peut apparaître. Les anciennes images des fonctions restent alors stockées dans Artifact Registry. C'est quelques Mo, sous la part gratuite de 0,5 Go, et l'alerte de budget à 1 € préviendrait si ça grossissait. Pour les supprimer automatiquement, il faudrait donner à `github-deploy` le rôle **Administrateur Artifact Registry**, puis lancer `npx firebase functions:artifacts:setpolicy`.

## Publier

1. La CI de la pull request doit être verte, car elle a déjà testé les règles et les fonctions. La PR doit aussi être fusionnée dans `main`.
2. Dans GitHub, ouvre l'onglet **Actions**, puis **Publier Firebase**, puis **Run workflow** sur la branche `main`.
3. Attends la coche verte, environ 5 minutes la première fois.

- Les règles de Storage (`storage.rules`) partent avec le reste, vers le bucket par défaut `familia-93286.firebasestorage.app` (US-EAST1, activé le 15/11/2026).
- Les fonctions tournent en **Europe (europe-west1)**. C'est réglé dans le code (`functions/src/index.ts`).
- Au premier déploiement, Firebase active lui-même les services nécessaires (Cloud Functions, Cloud Build, Cloud Scheduler pour le rappel de 18 h). S'il échoue en signalant une API pas encore activée, attends 2 minutes et relance.

## Publier depuis un ordinateur (secours)

Depuis le dossier `firebase`, avec Node.js 22 :

```
npm ci
npm ci --prefix functions
npx firebase login
npx firebase deploy --only firestore:rules,firestore:indexes,functions
```

Le projet Familia (`familia-93286`) est déjà choisi dans `firebase/.firebaserc`.

## Vérifier après le déploiement

1. Dans la console Firebase, ouvre **Functions** : cinq fonctions apparaissent, `onEventCreated`, `onEventUpdated`, `onEventDeleted`, `onMemberDeleted` et `dayBeforeReminder`.
2. Ajoute un événement depuis un téléphone. L'autre téléphone reçoit « Nouvel événement » en quelques secondes.
3. Le soir, à 18 h, ceux qui ont répondu « Je viens » ou « Peut-être » à un événement du lendemain reçoivent le rappel.

## Vérifier l'alerte de budget

Familia doit rester gratuite. L'alerte à 1 € te prévient bien avant tout coût réel.

1. Ouvre console.cloud.google.com, puis **Facturation** puis **Budgets et alertes**.
2. Vérifie qu'un budget « Familia » existe, avec un montant de 1 € et des alertes à 50 %, 90 % et 100 %, envoyées sur ton adresse.
3. S'il n'existe pas, crée-le avec ces valeurs.

## Alerte de consommation (récit 1 sexies)

Chaque matin à partir de 8 h, la tâche des 30 minutes (`arrivalReminder`, qui sert aussi aux voyages et à la question de la semaine) lit une seule fois dans Cloud Monitoring les lectures, écritures et suppressions de la veille, et la taille de la base. Elle les range dans `consumption/<jour>`, que la carte « Consommation » d'Administration affiche. Elle te prévient par une notification (canal « Administration ») à 80 % d'une limite gratuite, ou quand le coût estimé du mois dépasse 0,50 €. L'alerte de budget à 1 € par e-mail reste le filet de sécurité.

Pour lire Monitoring, la fonction a besoin d'un rôle, à donner une seule fois :

1. Ouvre console.cloud.google.com sur le projet Familia, puis **IAM et administration** et **IAM**.
2. Trouve la ligne `<numéro>-compute@developer.gserviceaccount.com` (le compte qui fait tourner les fonctions), puis touche le crayon au bout de la ligne.
3. Touche **Ajouter un autre rôle**, cherche **Lecteur Monitoring** (`roles/monitoring.viewer`), choisis-le, puis **Enregistrer**.

Sans ce rôle, rien ne casse : la carte affiche « Familia n'a pas pu lire la consommation ». Le lendemain matin du rôle ajouté, elle affiche les chiffres.

Pour une famille de 13 personnes, les notifications et le rappel restent dans la part gratuite. Les fonctions sont limitées à une seule instance, ce qui plafonne le coût même en cas de bug.

## Revenir en arrière

Si une nouvelle version pose problème, redéploie la version précédente : annule la PR fautive dans `main` (bouton « Revert » de GitHub), puis relance « Publier Firebase ».
