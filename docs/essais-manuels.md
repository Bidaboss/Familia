# Essais à faire sur le téléphone

Ces essais complètent les tests automatiques : ils vérifient ce qu'un test sur ordinateur ne peut pas voir. À refaire sur l'APK de test avant d'ouvrir l'appli à toute la famille.

## Hors ligne, en mode avion (lot 5)

PRD, récit 3 et cas limite « Pas de réseau » : le planning reste consultable sans réseau, et les ajouts partent au retour du réseau.

1. Ouvre l'appli avec le réseau, va sur Planning, puis sur Anniversaires et sur Famille, pour que les données arrivent sur le téléphone.
2. Ferme l'appli complètement (glisse-la hors de la liste des applis récentes).
3. Active le mode avion.
4. Rouvre l'appli. **Attendu :** le bandeau « Hors ligne » apparaît en haut, le planning, les anniversaires et la famille s'affichent avec les dernières données, sans écran d'erreur.
5. Passe de « Liste » à « Mois » et change de mois. **Attendu :** tout s'affiche normalement.
6. Ajoute un événement « Essai hors ligne » pour demain. **Attendu :** le formulaire se ferme après quelques secondes au plus, et l'événement apparaît tout de suite dans ton planning.
7. Sur un deuxième téléphone connecté, regarde le planning. **Attendu :** l'événement n'y est pas encore.
8. Désactive le mode avion sur le premier téléphone. **Attendu :** le bandeau disparaît, et l'événement apparaît sur le deuxième téléphone en moins d'une minute.
9. Supprime l'événement « Essai hors ligne ».

À noter si ça se passe mal : l'étape qui échoue, ce que tu vois à l'écran (une capture si possible) et la version de l'appli (en bas de l'onglet Famille).

## Passage de minuit (lot 5)

1. Le soir, laisse l'appli ouverte sur Planning un peu avant minuit, écran allumé.
2. Après minuit, attends une minute. **Attendu :** l'en-tête « Demain » devient « Aujourd'hui », et les événements de la veille disparaissent de la liste.

## Notifications (lot 8)

Il faut deux téléphones connectés avec deux membres différents, et les fonctions déployées (voir [deploiement.md](deploiement.md)).

1. Au premier lancement après la mise à jour, Android demande si Familia peut envoyer des notifications. **Attendu :** la question n'apparaît qu'une fois. Réponds « Autoriser ».
2. Sur le téléphone A, ajoute un événement « Essai notif » pour demain. **Attendu :** le téléphone B reçoit « Nouvel événement » en quelques secondes, et le téléphone A ne reçoit rien.
3. Sur B, touche la notification. **Attendu :** l'appli s'ouvre sur l'événement.
4. Sur A, change l'heure de l'événement. **Attendu :** B reçoit « Événement modifié ».
5. Sur B, réponds « Je viens ». Le soir à 18 h, **attendu :** B reçoit « Demain : Essai notif ».
6. Sur B, ouvre Famille, puis Notifications, et coupe « Rappel la veille à 18 h ». **Attendu :** le soir suivant, B ne reçoit plus de rappel.
7. Sur A, supprime l'événement. **Attendu :** B reçoit « Événement annulé ».
8. Sur B, bloque les notifications de Familia dans les réglages Android, puis rouvre Famille et Notifications. **Attendu :** un encadré dit que les notifications sont bloquées, avec un bouton pour les autoriser.

## Sauvegarde (lot 9)

1. Avec un compte admin, ouvre Famille. **Attendu :** « Sauvegarde des données » apparaît sous « Notifications ». Avec un compte qui n'est pas admin, la ligne n'apparaît pas.
2. Touche « Créer la sauvegarde », choisis Google Drive, puis Enregistrer. **Attendu :** « Sauvegarde enregistrée. » s'affiche, et le fichier `familia-sauvegarde-<date>.json` est dans ton Drive.
3. Ouvre le fichier sur un ordinateur. **Attendu :** tu y retrouves les membres, les foyers, les événements et les réponses.
4. Touche à nouveau « Créer la sauvegarde », puis ferme le sélecteur sans enregistrer. **Attendu :** rien ne change, et aucun message d'erreur n'apparaît.
5. Active le mode avion, puis touche « Créer la sauvegarde ». **Attendu :** le sélecteur ne s'ouvre pas, et l'appli affiche « Connecte-toi à Internet pour faire une sauvegarde complète. »
