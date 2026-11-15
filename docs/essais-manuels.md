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
9. Avec A et B dans deux foyers différents : sur A, ajoute un événement pour son foyer seulement. **Attendu :** B ne reçoit rien, mais le voit dans le planning avec « Toute la famille ». Sur B, choisis « Mon foyer » en haut du planning. **Attendu :** l'événement disparaît, les anniversaires restent. Ferme et rouvre l'appli : « Mon foyer » est toujours choisi.
10. Sur B, ajoute-toi en « Je viens » sur l'événement du foyer de A (repasse sur « Toute la famille »), puis sur A, change son heure. **Attendu :** B reçoit « Événement modifié ».
11. Sur B, ouvre Famille, Notifications, puis « Réglages Android des notifications ». **Attendu :** les catégories Planning, Rappels et Cadeaux apparaissent (les autres arrivent avec leur module), chacune avec son interrupteur.

## Sauvegarde (lot 9)

1. Avec un compte admin, ouvre Famille. **Attendu :** « Sauvegarde des données » apparaît sous « Notifications ». Avec un compte qui n'est pas admin, la ligne n'apparaît pas.
2. Touche « Créer la sauvegarde », choisis Google Drive, puis Enregistrer. **Attendu :** « Sauvegarde enregistrée. » s'affiche, et le fichier `familia-sauvegarde-<date>.json` est dans ton Drive.
3. Ouvre le fichier sur un ordinateur. **Attendu :** tu y retrouves les membres, les foyers, les événements et les réponses.
4. Touche à nouveau « Créer la sauvegarde », puis ferme le sélecteur sans enregistrer. **Attendu :** rien ne change, et aucun message d'erreur n'apparaît.
5. Active le mode avion, puis touche « Créer la sauvegarde ». **Attendu :** le sélecteur ne s'ouvre pas, et l'appli affiche « Connecte-toi à Internet pour faire une sauvegarde complète. »

## Tuto de démarrage (lot 11a)

1. Installe l'appli sur un téléphone où elle n'a jamais été ouverte, puis connecte-toi avec un compte adulte. **Attendu :** « Bienvenue dans Familia » s'affiche, avec « 1 sur 4 » en haut.
2. Touche « Suivant » jusqu'au 3e écran. **Attendu :** « Dire si tu viens » parle aussi de répondre pour tes enfants. Avec un compte enfant, ou un adulte sans enfant rattaché (un grand-parent par exemple), cette phrase n’apparaît pas.
3. Touche le bouton retour d'Android. **Attendu :** l'écran précédent s'affiche.
4. Touche « Passer ». **Attendu :** l'écran « Rester prévenu » s'affiche (4 sur 4), avec « Autoriser les notifications » et « Pas maintenant ».
5. Touche « Autoriser les notifications », puis accepte dans la fenêtre d'Android. **Attendu :** le planning s'ouvre.
6. Ferme l'appli, puis rouvre-la. **Attendu :** le tuto ne s'affiche plus.

## Aide (lot 11b)

1. Sur le planning, touche « ? » en haut à droite. **Attendu :** un panneau s'ouvre par le bas avec quelques phrases sur le planning et le bouton « Toute l'aide ».
2. Fais la même chose sur la fiche d'un événement, sur l'ajout d'un événement et dans l'onglet Famille. **Attendu :** chaque panneau parle de son écran.
3. Touche « Toute l'aide ». **Attendu :** la page Aide s'ouvre. Touche une fonction : elle se déplie. Avec un compte qui n'est pas admin, les deux fonctions « Réservé aux admins » n'apparaissent pas.
4. En bas de la page, **attendu :** « Un souci ? » donne les prénoms des admins.
5. Touche « Revoir le tuto ». **Attendu :** le tuto recommence au premier écran, et à la fin tu reviens dans l'appli.
6. Dans l'onglet Famille, **attendu :** une ligne « Aide » ouvre la même page.

## Message de confirmation (audit DESIGN du 14/11)

1. Ajoute un événement, puis touche « Enregistrer ». **Attendu :** le planning revient, et « C'est enregistré. » s'affiche en bas pendant 8 secondes. Le bouton « Ajouter » remonte au-dessus du message.
2. Ouvre cet événement, « Modifier », puis « Supprimer ». **Attendu :** le planning revient directement (pas la fiche de l'événement), avec « Événement supprimé. ».
3. Active le mode avion, modifie une fiche de la famille et enregistre. **Attendu :** après 5 secondes environ, la fiche se ferme avec « Enregistré, envoi au retour du réseau. ». Coupe le mode avion : la modification arrive chez les autres.
4. Touche la flèche retour d'un formulaire sans rien changer. **Attendu :** aucun message.

## Formulaire d'événement allégé (audit DESIGN du 15/11)

1. Touche « Ajouter » sur le planning. **Attendu :** « Pour qui » tient sur une ligne avec « Toute la famille », et la note est remplacée par « Ajouter une note ».
2. Touche la ligne « Pour qui ». **Attendu :** la liste s'ouvre ; choisis ton foyer, elle se ferme et la ligne affiche ton foyer.
3. Touche « Ajouter une note ». **Attendu :** le champ de note apparaît. Une fois enregistré, rouvre l'événement avec « Modifier » : la note est dépliée.
4. Avec un compte enfant, **attendu :** la ligne « Pour qui » affiche le prénom de l'enfant, sans « Changer ».

## Photo en grand (demande de Kévin, 15/11)

1. Dans l'onglet Famille, touche la photo d'un membre qui en a une. **Attendu :** elle s'affiche en plein écran sur fond noir, avec son prénom en bas.
2. Touche « Fermer » en haut à gauche, puis recommence et fais le geste retour d'Android. **Attendu :** les deux referment la photo.
3. Sur ta propre photo (ou celle d'un enfant de ton foyer), **attendu :** le bouton « Changer la photo » est en bas et ouvre le choix galerie ou appareil photo. Sur la photo d'un autre foyer, ce bouton n'apparaît pas.
4. Sur un membre sans photo que tu peux modifier, touche l'avatar. **Attendu :** le choix de la photo s'ouvre directement, comme avant.
