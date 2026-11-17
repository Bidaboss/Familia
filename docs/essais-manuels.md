# Essais à faire sur le téléphone

Ces essais complètent les tests automatiques : ils vérifient ce qu'un test sur ordinateur ne peut pas voir. À refaire sur l'APK de test avant d'ouvrir l'appli à toute la famille.

## Hors ligne, en mode avion (lot 5)

PRD, récit 3 et cas limite « Pas de réseau » : le planning reste consultable sans réseau, et les ajouts partent au retour du réseau.

1. Ouvre l'appli avec le réseau, va sur Planning, puis sur Anniversaires et sur Famille, pour que les données arrivent sur le téléphone.
2. Ferme l'appli complètement (glisse-la hors de la liste des applis récentes).
3. Active le mode avion.
4. Rouvre l'appli. **Attendu :** le bandeau « Hors ligne » apparaît en haut, le planning, les anniversaires et la famille s'affichent avec les dernières données, sans écran d'erreur.
5. Le planning s'ouvre sur « Mois ». Passe à « Liste », puis reviens à « Mois » et change de mois. **Attendu :** tout s'affiche normalement.
6. Ajoute un événement « Essai hors ligne » pour demain. **Attendu :** le formulaire se ferme après quelques secondes au plus, et l'événement apparaît tout de suite dans ton planning.
7. Sur un deuxième téléphone connecté, regarde le planning. **Attendu :** l'événement n'y est pas encore.
8. Désactive le mode avion sur le premier téléphone. **Attendu :** le bandeau disparaît, et l'événement apparaît sur le deuxième téléphone en moins d'une minute.
9. Supprime l'événement « Essai hors ligne ».

Au lieu du repas, où le réseau peut être faible (passe hors ligne du 16/11). Toujours en mode avion, sur le premier téléphone :

10. Accueil. **Attendu :** le bandeau « Hors ligne » et les dernières données, sans écran d'erreur.
11. Sur un événement, réponds « Je viens », puis change pour « Peut-être », deux fois de suite. **Attendu :** la réponse change tout de suite et aucun message d'erreur ne s'affiche. Au retour du réseau, le deuxième téléphone montre une seule réponse, la dernière.
12. « Qui apporte quoi » : ajoute « Essai gâteau ». **Attendu :** le champ se vide et la ligne apparaît tout de suite, une seule fois. Au retour du réseau, il n'y a qu'une ligne sur le deuxième téléphone.
13. Bonnes nouvelles : écris un texte avec une photo et publie. **Attendu :** « Il faut du réseau pour envoyer les photos », sans rien publier. Ton texte et ta photo restent dans le formulaire. Désactive le mode avion puis publie : **Attendu :** une seule bonne nouvelle.
14. Réactive le réseau. **Attendu :** le bandeau disparaît et tout arrive sur le deuxième téléphone, sans doublon.

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
9. Avec A et B dans deux foyers différents : sur A, ajoute un événement pour son foyer seulement. **Attendu :** B ne reçoit rien, mais le voit dans le planning avec « Toute la famille ». Sur B, active l'interrupteur « Mon foyer » en haut du planning. **Attendu :** l'événement disparaît, les anniversaires restent. Ferme et rouvre l'appli : le planning revient sur toute la famille, interrupteur éteint.
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

## Photo en grand (demande du 15/11)

1. Dans l'onglet Famille, touche la photo d'un membre qui en a une. **Attendu :** elle s'affiche en plein écran sur fond noir, avec son prénom en bas.
2. Touche « Fermer » en haut à gauche, puis recommence et fais le geste retour d'Android. **Attendu :** les deux referment la photo.
3. Sur ta propre photo (ou celle d'un enfant de ton foyer), **attendu :** le bouton « Changer la photo » est en bas et ouvre le choix galerie ou appareil photo. Sur la photo d'un autre foyer, ce bouton n'apparaît pas.
4. Sur un membre sans photo que tu peux modifier, touche l'avatar. **Attendu :** le choix de la photo s'ouvre directement, comme avant.

## Haut du planning sur une rangée (audit DESIGN n°3)

1. Ouvre le planning. **Attendu :** une seule rangée en haut, avec « Mois | Liste » à gauche (Mois choisi) et l'interrupteur « Mon foyer » éteint à droite.
2. Active « Mon foyer ». **Attendu :** l'interrupteur s'allume et seuls les événements de ton foyer restent. Éteins-le : toute la famille revient.
3. En police à 200 %, **attendu :** rien n'est coupé, et les boutons gardent au moins la hauteur d'un doigt.

## Plantages (récit 6 sexies, rév. 220)

Avec le compte de l'admin principal, après « Publier Firebase ».

1. Administration : **Attendu :** une ligne « Plantages », sans badge.
2. Ouvre-la. **Attendu :** « Aucun plantage », et « Faire un essai » en bas.
3. Touche « Faire un essai », puis « Fermer l'appli ». **Attendu :** l'appli se ferme.
4. Rouvre l'appli. **Attendu :** dans la minute, une notification « Nouveau plantage » qui parle de CrashTestException. La toucher ouvre Plantages, avec une carte « CrashTestException », « 1 fois, chez 1 personne », la version et l'écran « Crashes ».
5. Refais l'essai, puis rouvre. **Attendu :** la même carte passe à « 2 fois », et aucune notification de plus (même sorte, et une seule par jour).
6. « Copier pour Claude », puis colle le texte dans une note. **Attendu :** l'erreur, le nombre de fois, les versions, le téléphone et la trace. Aucun prénom.
7. « Marquer comme réglé », puis confirme. **Attendu :** la carte disparaît, et le badge d'Administration aussi.
8. Avec le compte d'un autre adulte : **Attendu :** pas de ligne « Plantages » dans son menu, puisqu'il n'a pas Administration.

## Un enfant suivi par Family Link se connecte (avant le 21)

À faire une fois avant le repas, avec le téléphone d'un enfant suivi par Family Link et le téléphone du parent qui le suit. Si ça bloque, il reste une solution simple pour le jour J (voir le repli à la fin).

Avant de commencer :
- Dans Familia, sur ton téléphone : onglet Famille, fiche de l'enfant. **Attendu :** son profil est « Enfant, avec l'appli », et son adresse Google est notée exactement comme sur son téléphone (Réglages Android > Google > le nom en haut).
- Le parent a l'appli Family Link ouverte et connectée.

1. Sur le téléphone de l'enfant, ouvre le lien de téléchargement et touche le fichier de Familia. **Attendu :** Android propose d'installer. S'il dit que l'installation est bloquée ou demande l'accord d'un parent, passe au repli A.
2. Ouvre Familia et touche « Se connecter avec Google », puis choisis le compte de l'enfant. **Attendu :** Google dit qu'il faut l'accord d'un parent. Selon la version, c'est le parent qui tape son mot de passe sur le téléphone de l'enfant, ou une demande qui arrive dans Family Link sur le téléphone du parent.
3. Le parent accepte, avec son mot de passe ou dans Family Link (la demande est en général dans les notifications de Family Link, ou sur la fiche de l'enfant). **Attendu :** l'enfant revient dans Familia et voit le tuto.
4. Fin du tuto. **Attendu :** l'enfant arrive sur l'appli avec son prénom, et rien de ce qui est réservé aux adultes.
5. Si l'écran dit « Le compte … ne fait pas partie de la famille », compare l'adresse affichée avec celle de la fiche, corrige-la dans Familia, puis touche « Essayer un autre compte » et recommence.
6. Si Google refuse sans proposer l'accord d'un parent, le parent regarde dans Family Link, sur la fiche de l'enfant, les réglages du compte et ceux des applis et services tiers liés au compte Google. Il autorise la connexion, puis recommence l'étape 2.

Repli si ça bloque :
- **A. L'installation est bloquée** (Family Link bloque souvent les applis qui ne viennent pas du Play Store) : sur ce téléphone, l'enfant n'aura pas l'appli pour le 21. Sur ta fiche de l'enfant, passe son profil à « Enfant, sans l'appli » (retire l'adresse Google). Ses parents répondent pour lui à « Je viens », et il est montré aux autres comme tous les enfants sans téléphone.
- **B. La connexion ne passe pas avant le repas** : même chose, profil « Enfant, sans l'appli » pour le 21, et on retente après, au calme, avec le parent.
- **Pendant la démo** : ne tente pas la connexion d'un enfant Family Link devant tout le monde. Fais-la avant, ou garde-la pour après.

## Tuto : écrans Cadeaux et Entraide (AVIS-18)

1. Dans Administration > Modules, allume Cadeaux et Entraide. Sur un autre téléphone, avec un compte qui n'est pas admin, ouvre l'appli pour la première fois. **Attendu :** après « Dire si tu viens », les écrans « Les cadeaux » puis « L'entraide » s'affichent, et « Rester prévenu » reste le dernier (6 sur 6).
2. Éteins Entraide, puis touche « Revoir le tuto » dans l'Aide. **Attendu :** l'écran « L'entraide » n'apparaît plus (5 écrans).
3. Sur un téléphone neuf, en Wi-Fi lent (ou en mode avion juste après la connexion, puis réactivé), touche « Passer » dès le premier écran du tuto. **Attendu :** tu restes sur « Rester prévenu », même quand les écrans Cadeaux et Entraide s'ajoutent (le compteur passe de « 4 sur 4 » à « 6 sur 6 »).

## Écran d'Accueil (récit 29)

1. Dans Administration > Modules, allume « Accueil ». Ferme complètement l'appli et rouvre-la. **Attendu :** l'onglet « Accueil » est le premier de la barre, et l'appli s'ouvre dessus.
2. Crée un événement pour toute la famille dans les 7 jours, sans répondre. **Attendu :** il apparaît dans « À faire pour moi » (avec « Dis si tu viens ») et dans « Les 7 prochains jours ». Réponds « Je viens » : il quitte « À faire pour moi ».
3. Touche « Voir le planning ». **Attendu :** l'onglet Planning s'ouvre.
4. Avec un anniversaire dans le mois et Cadeaux allumé, touche la ligne. **Attendu :** les envies de la personne s'ouvrent.
5. Avec une liste d'occasion à venir (pas une surprise), **attendu :** « Prochaine occasion » donne le nombre de jours et combien d'idées tu as réservées, jamais leur nom. Sur ta propre liste, aucune réservation n'est donnée. Une liste surprise n'apparaît jamais sur l'Accueil.
6. Avec un voyage en cours où quelqu'un a touché « Bien arrivé », **attendu :** « En voyage » montre « En cours » et la dernière arrivée.
7. Sur le téléphone d'un enfant, **attendu :** pas de coup de main à prendre, pas de bouton « Nouveau voyage ».
8. Une carte sans rien à dire ne s'affiche pas. Sans aucune carte, **attendu :** « Rien de prévu pour l'instant » avec « Voir le planning ».
9. Éteins « Accueil ». **Attendu :** l'onglet reste jusqu'à la prochaine ouverture de l'appli, puis l'appli s'ouvre à nouveau sur le Planning.

## Thèmes au choix (DESIGN-7)

1. Dans Administration > Modules, allume « Thèmes au choix ». Dans l'onglet Famille, **attendu :** une ligne « Apparence » apparaît sous les réglages.
2. Ouvre « Apparence ». **Attendu :** les 9 thèmes, chacun montré dans ses couleurs et sa police. Touche « Espagne ». **Attendu :** l'appli change tout de suite : fond crème avec une bande rouge, jaune, rouge en haut, titres en Abril Fatface, cartes à bordure rouge et ombre jaune.
3. Ferme complètement l'appli et rouvre-la. **Attendu :** le thème choisi est gardé. Sur un autre téléphone, le thème reste Classique.
4. Passe le téléphone en mode sombre. **Attendu :** Classique devient sombre comme avant. Arcade et Cosmos restent sombres, et les autres thèmes restent clairs.
5. Dans chaque thème, ouvre Planning, Accueil et Famille. **Attendu :** les textes restent en Nunito et lisibles, les boutons gardent leur place et leur taille, et la couleur de chaque membre ne change pas.
6. En police à 200 %, avec Arcade (police pixel), **attendu :** les titres passent à la ligne, mais rien n'est coupé.
7. Éteins le module. **Attendu :** l'appli revient au thème Classique et la ligne « Apparence » disparaît. Le choix reste gardé pour quand le module sera rallumé.

### Icône de l'appli

1. Installe la nouvelle version par-dessus l'ancienne. **Attendu :** l'appli s'ouvre depuis la liste des applis avec l'olivier. Si l'icône de l'écran d'accueil a disparu, ajoute-la de nouveau, une seule fois.
2. Dans « Apparence », tout en bas, touche « Terre ». **Attendu :** le message « L'icône change dans quelques secondes… » apparaît, et l'appli reste ouverte. Va dans la liste des applis. **Attendu :** une seule Familia, avec l'arbre sur fond terracotta.
3. Ouvre Familia depuis cette nouvelle icône, puis touche une notification. **Attendu :** les deux ouvrent l'appli normalement.
4. Reviens dans « Apparence ». **Attendu :** « Terre » est cochée. Choisis « Or », puis « Olivier ». **Attendu :** à chaque fois, une seule icône dans la liste des applis.
5. Avec un fond d'écran d'Android 13 ou plus et les « icônes à thème » activées, **attendu :** l'arbre prend la couleur du téléphone, quelle que soit l'icône choisie.

## Utilisation (récit 1 nonies)

1. Avec le module « Utilisation » éteint, utilise l'appli quelques minutes, puis ouvre Administration > Utilisation. **Attendu :** rien n'est compté, et chacun apparaît « Jamais connecté ».
2. Allume « Utilisation (temps passé) » dans Administration > Modules. Passe 2 minutes sur Planning et 1 minute sur Cadeaux, mets l'appli en arrière-plan, puis rouvre-la. **Attendu :** dans Utilisation, tu es « Dernière visite : aujourd'hui », avec environ 2 min de Planning et 1 min de Cadeaux, et une ouverture aujourd'hui sur le graphique.
3. Quitte l'appli 1 minute, puis reviens. **Attendu :** le nombre d'ouvertures ne bouge pas, car un retour en moins de 5 minutes ne compte pas.
4. Sur le téléphone d'un adulte responsable d'un enfant qui a l'appli, **attendu :** une ligne « Temps de mes enfants » apparaît dans l'onglet Famille, avec le temps de cet enfant sur 7 et 30 jours, et rien d'autre. Un adulte sans enfant avec l'appli ne voit pas cette ligne.
5. Dans la console Firebase, ouvre `usage/<ton id>/days/<aujourd'hui>`. **Attendu :** seulement `day`, `opens`, `seconds` et `expireAt`, sans aucun titre, texte ou identifiant d'élément.

## Alerte de consommation (récit 1 sexies)

1. Avant d'ajouter le rôle « Lecteur Monitoring », ouvre Administration le lendemain de la publication. **Attendu :** la carte « Consommation » dit « Familia n'a pas pu lire la consommation ». Avant le premier matin, elle dit « Le premier relevé arrive demain matin, vers 8 h ».
2. Ajoute le rôle (docs/deploiement.md), puis attends le matin suivant. **Attendu :** la carte montre le relevé de la veille, avec les lectures, écritures et suppressions sur leurs parts gratuites, et un coût estimé du mois à 0,00 €.
3. Sur un autre téléphone que celui de l'admin principal, ouvre les réglages Android de Familia, puis **Notifications**. **Attendu :** pas de catégorie « Administration ».
## Ajouter à mon agenda (récit 30)

1. Allume « Ajouter à mon agenda » dans Administration > Modules. Ouvre un événement avec une heure et un lieu, puis touche « Ajouter à mon agenda ». **Attendu :** l'agenda du téléphone s'ouvre avec le titre, le lieu, la bonne heure, une durée d'une heure et la note « Créé depuis Familia ». Valide.
2. Fais la même chose avec un événement sans heure. **Attendu :** l'agenda propose un événement « toute la journée ».
3. Dans Anniversaires, touche l'icône d'agenda au bout d'une ligne. **Attendu :** « Anniversaire de … », toute la journée, répété chaque année.
4. Depuis un autre téléphone, modifie l'événement de l'étape 1. **Attendu :** la notification « Événement modifié » se termine par « Si tu l'as ajouté à ton agenda, mets-le à jour depuis Familia. »
5. Éteins le module. **Attendu :** plus de bouton ni d'icône d'agenda pour les autres membres, et la notification de modification ne parle plus d'agenda.

## Sondages (récits 2 bis et 2 ter)

1. Allume « Sondages » dans Administration > Modules. Dans le Planning, touche l'icône des sondages en haut. **Attendu :** la page « Sondages », vide, avec « Nouveau sondage ».
2. Touche « Nouveau sondage », garde « Trouver une date », écris une question, choisis 3 dates (une avec une heure), laisse « Toute la famille » cochée et une date de fin dans une semaine. Envoie. **Attendu :** le sondage apparaît dans « En cours » ; les autres téléphones reçoivent « Nouveau sondage ».
3. Sur le téléphone d'un parent, ouvre la notification. **Attendu :** le sondage s'ouvre sur « Ma réponse », avec une carte « Réponse de … » pour chacun de ses enfants. Réponds pour chaque date, puis touche « Envoyer ma réponse ». Essaie d'envoyer la réponse d'un enfant avec une date vide. **Attendu :** « Réponds pour chaque date. », rien n'est envoyé.
4. Regarde « Résultats » et « Par foyer ». **Attendu :** les noms sous chaque date, la date en tête marquée « En tête », un tableau par foyer avec ✓ ? ✗, et « Pas encore répondu : … ».
5. Sur le téléphone de l'auteur, touche « Choisir cette date » sous une date, garde le nom proposé et touche « Ajouter au planning ». **Attendu :** l'événement apparaît dans le Planning ; ceux qui ont dit oui sont inscrits « Je viens » ; le sondage passe dans « Terminés » avec « Date retenue : … » et « Voir l'événement ».
6. Fais un « Choix libre » avec 3 réponses, sans plusieurs réponses. Réponds sur deux téléphones, puis « Terminer le sondage ». **Attendu :** une seule réponse cochable à la fois ; « Résultat du sondage » arrive aux autres ; plus personne ne peut répondre.
7. Sur le téléphone d'un enfant avec l'appli, ouvre « Sondages ». **Attendu :** il répond, mais ne voit pas « Nouveau sondage ».
8. Supprime un sondage. **Attendu :** il disparaît partout, sans notification.

## Qui apporte quoi (récit 4 bis)

1. Allume « Qui apporte quoi » dans Administration > Modules. Ouvre un événement. **Attendu :** une partie « Qui apporte quoi » sous « qui vient », vide, avec une phrase d'exemple.
2. Écris « Le dessert », laisse « Je m'en charge » coché et touche « Ajouter à la liste ». Ajoute « Le pain » en décochant. **Attendu :** « Le dessert » montre « C'est moi qui l'apporte », « Le pain » montre « Personne pour l'instant » et un bouton « Je m'en charge ».
3. Sur un autre téléphone, ouvre le même événement. **Attendu :** les deux lignes, avec ton prénom sur le dessert ; il peut prendre le pain, pas le dessert, et ne voit pas la corbeille sur tes lignes.
4. Touche « Je ne peux plus » sur le dessert. **Attendu :** la ligne redevient libre partout.
5. La veille de l'événement, à 18 h. **Attendu :** le rappel finit par « Tu apportes : … » pour chacun ; celui qui a pris une ligne sans répondre « Je viens » le reçoit aussi.
6. Éteins le module. **Attendu :** la partie disparaît pour les autres membres, et le rappel ne parle plus de ce qu'on apporte.

## Lieux et « Rejoignez-moi » (récit 33)

1. Allume « Lieux et « Rejoignez-moi » » dans Administration > Modules. Ouvre l'Accueil. **Attendu :** une carte « Rejoignez-moi » en haut, avec « Dire où je suis ».
2. Touche « Dire où je suis », puis « Prendre ma position ». **Attendu :** Android demande la localisation, avec « Pendant l'utilisation de l'appli » (jamais « Toujours »). Accepte : l'adresse où tu es s'affiche.
3. Regarde « À qui ? ». **Attendu :** personne n'est coché, et « Envoyer » reste grisé. Touche « Mon foyer » : ton foyer se coche. Touche-le encore : tout se décoche. Coche une seule personne, écris un nom et un mot, puis « Envoyer ».
4. Sur le téléphone de la personne cochée. **Attendu :** une notification « Prénom : rejoins-moi » avec le lieu et le mot. L'appui ouvre l'Accueil, où la carte montre le lieu et « Itinéraire ». « Itinéraire » ouvre Google Maps ou Waze sur le bon point.
5. Sur ton téléphone, l'Accueil montre « Tu partages ce lieu jusqu'à … ». Touche « Ne plus partager ». **Attendu :** la carte disparaît des deux téléphones.
6. Recommence avec « Un autre endroit » : tape une adresse puis « Chercher » ; puis colle le lien « Partager » d'un lieu dans Google Maps. **Attendu :** l'adresse est trouvée ; avec le lien, le nom du lieu se remplit tout seul.
7. Refuse l'autorisation de localisation (Paramètres Android > Applis > Familia). **Attendu :** un message propose de choisir un autre endroit.
8. Ouvre un événement qui a un lieu. **Attendu :** un bouton « Itinéraire » sous le lieu.
9. Le lendemain, plus de 24 h après l'envoi. **Attendu :** la carte a disparu de l'Accueil.

## Partager vers une messagerie (récit 31, première partie)

Avant ces essais : « Publier Firebase » a publié la page des liens (Hosting), et le module « Partager vers une messagerie » est allumé dans Administration > Modules.

1. Ouvre un événement, puis touche l'icône **Partager** en haut. **Attendu :** le partage d'Android s'ouvre. Dans WhatsApp, le message montre une image Argile (titre, jour, heure, lieu, « Familia » en bas), puis un texte qui se lit seul et un lien `https://familia-93286.web.app/o/event/…`. Ni « qui vient », ni la note de l'événement.
2. Fais la même chose sur un sondage, puis sur un anniversaire du Planning (icône à droite de la ligne). **Attendu :** le sondage montre sa question, sans les réponses. L'anniversaire montre le prénom, le jour et l'âge, sans photo.
3. Sur un téléphone qui a la version famille, touche le lien reçu. **Attendu :** Familia s'ouvre directement sur l'événement, le sondage ou la page du membre.
4. Ouvre le même lien dans le navigateur d'un téléphone sans Familia. **Attendu :** une page Argile dit « Un événement de la famille », sans aucun titre, nom ni date, avec « Ouvrir dans Familia » et la phrase « Demande le lien d'installation… ». Aucun lien vers le Drive.
5. Avec la version de test, touche « Ouvrir dans Familia » sur cette page. **Attendu :** Android propose d'ouvrir Familia (test), puis l'élément s'ouvre.
6. Ouvre `https://familia-93286.web.app/o/event/` (sans identifiant). **Attendu :** la page dit que le lien n'est pas complet, sans bouton « Ouvrir ».
7. Éteins le module. **Attendu :** les icônes Partager disparaissent pour tout le monde sauf l'admin principal.

## Défi lancé par un foyer (récit 35 bis)

Avec le module « Arbre » visible (admin principal, ou module allumé), deux comptes du même foyer : un adulte et un enfant avec l'appli.

1. Administration > « Ordre des foyers » : mets ton foyer en premier, puis touche « Enregistrer l'ordre ». **Attendu :** ton foyer est en face du mois prochain, les autres suivent, un mois chacun.
2. À partir de 7 jours avant le mois prochain, ouvre l'Accueil. **Attendu :** la carte de l'arbre dit « C'est à vous de lancer le défi de … ! » avec « Préparer le défi ».
3. Avec le compte enfant, touche « Préparer le défi », choisis « Cuisine », un objectif, puis un nom, un emoji et une illustration. À l'aperçu, touche « Enregistrer pour le foyer ». **Attendu :** pas de bouton « Publier » ni « Passer notre tour » ; la phrase « Un adulte du foyer le publiera. »
4. Avec le compte adulte, rouvre le guide. **Attendu :** le brouillon de l'enfant est là. Touche « Publier le défi ». **Attendu :** le guide se ferme et la bannière disparaît de l'Accueil.
5. Avec un autre foyer, refais l'étape 2 au mois de son tour, puis touche « Passer notre tour » et confirme. **Attendu :** la bannière disparaît, et le défi du mois reste celui de Familia.

## Commentaires sous un événement (récit 28)

Avant ces essais : « Publier Firebase » a publié les règles, l'index TTL et les fonctions, et le module « Commentaires sous les événements » est allumé dans Administration > Modules.

1. Ouvre un événement. **Attendu :** une partie « Commentaires » tout en bas, vide, avec une phrase d'exemple, un champ et « Publier le commentaire ».
2. Écris « Je prends le gâteau » et publie. **Attendu :** le commentaire apparaît avec ton prénom, le jour et l'heure ; le champ se vide.
3. Sur un autre téléphone qui a dit « Je viens », ouvre le même événement. **Attendu :** il voit ton commentaire, sans « Modifier » ni « Retirer ». Il a reçu une notification « Prénom a commenté « Titre » » ; l'appui ouvre l'événement.
4. Sur ton téléphone, touche « Modifier », change le texte et enregistre. **Attendu :** le nouveau texte partout, avec « · modifié ».
5. Sur l'autre téléphone, touche « Ne plus être prévenu des commentaires », puis écris un commentaire depuis le tien. **Attendu :** plus de notification là-bas. Le bouton devient « Être à nouveau prévenu des commentaires ».
6. Avec le compte d'un chef de famille, ouvre l'événement. **Attendu :** « Retirer » sur les commentaires des membres de son foyer seulement. L'admin principal peut tout retirer.
7. Écris plus de 30 commentaires (ou ouvre un événement qui en a). **Attendu :** les 30 plus récents, et « Voir les commentaires plus anciens » en haut qui montre les autres.
8. Supprime l'événement. **Attendu :** ses commentaires disparaissent aussi (console Firebase).
9. Éteins le module. **Attendu :** la partie disparaît pour tout le monde sauf l'admin principal, et seul l'admin principal reçoit encore les notifications.

## Voir comme (PRD, rôles)

Avec le compte de l'admin principal.

1. Ouvre Famille > Administration. **Attendu :** une ligne « Voir comme… » avec une phrase d'explication. Touche-la. **Attendu :** la liste des autres membres, par prénom, avec leur photo.
2. Choisis un adulte d'un autre foyer. **Attendu :** l'appli repart de son premier onglet, sous un bandeau « Tu vois l'appli comme Prénom » avec « Quitter ». Les onglets et les modules sont les siens : un module encore en test n'apparaît plus.
3. Ouvre un événement et touche « Je viens ». **Attendu :** le message « Lecture seule en mode Voir comme », et la réponse ne change pas, ni ici ni sur un autre téléphone. Essaie aussi d'ajouter un événement, de commenter et de réserver un cadeau : même message, rien n'est écrit.
4. Ouvre Famille. **Attendu :** pas de menu Administration (le membre n'est pas admin principal), et les droits de ce membre (un chef voit ceux de son foyer).
5. Touche « Quitter ». **Attendu :** l'appli revient à ton compte, sur son premier onglet, avec Administration.
6. Ouvre Aide. **Attendu :** une rubrique « Si tu as besoin d'aide » dit que l'admin principal peut voir l'appli comme toi, sans rien modifier.
7. Avec le compte d'un autre membre, ouvre Administration si tu y as accès. **Attendu :** pas de « Voir comme… ».

8. Voir comme un membre qui prépare ton anniversaire, puis ouvre Cadeaux. **Attendu :** en haut, « Les listes où tu n'es pas invité restent cachées… ». La liste surprise pour toi n'apparaît pas, et l'écran ne tombe pas en erreur. Les listes où vous êtes invités tous les deux s'affichent, ainsi que « Mes envies » de chacun. Le carnet de cadeaux n'est pas proposé.
9. Ouvre Entraide. **Attendu :** les coups de main que ce membre voit, y compris les siens.
10. Si un écran tombe en erreur en mode Voir comme, c'est qu'il demande une donnée que le serveur refuse à l'admin (une liste sans lui, le carnet, les souvenirs privés). Ce n'est pas une fuite. Note l'écran pour qu'on le traite.

Avant le 20/11 : refais tout cet essai sur l'APK de publication, puisque « Voir comme » a changé la façon dont l'appli reçoit ses données (relecture de #112).

Limites connues de cette première partie : ce que le serveur cache à l'admin (le carnet de cadeaux, l'espace cadeau de son propre anniversaire) reste caché. Le message « Masqué : c'est ta surprise » et les demandes de coup de main viendront dans une deuxième partie.

## Partager Familia avec toute la famille (récit 1 bis bis, rév. 229)

Pas d'interrupteur : le bouton n'est là que pour l'admin principal, dans la version famille (le lien vient du secret FAMILIA_DOWNLOAD_URL, comme l'invitation). Les autres adultes ne le voient plus.

1. Avec l'admin principal, touche ta photo en haut à droite, puis **Partager Familia**. **Attendu :** le partage d'Android s'ouvre, WhatsApp en tête s'il est installé.
2. Choisis WhatsApp. **Attendu :** le message est déjà écrit et tu peux le modifier avant d'envoyer : l'accueil, le lien du dossier Drive, trois étapes, puis « Si ton adresse n'est pas inscrite, demande à » suivi du prénom de l'admin principal.
3. Vérifie le lien. **Attendu :** c'est le même que dans « Envoyer l'invitation », sans rien après (aucun jeton, aucune adresse).
4. Avec l'admin principal, ouvre **Administration** et touche **Partager Familia**. **Attendu :** le même message.
5. Avec un autre adulte, même un chef de famille, touche sa photo. **Attendu :** pas de ligne Partager Familia.
6. Avec un compte enfant, touche sa photo. **Attendu :** pas de ligne Partager Familia.
7. En mode **Voir comme** un adulte. **Attendu :** pas de ligne Partager Familia (rien ne sort de l'appli dans ce mode).

## Aucune surprise sur l'écran verrouillé (audit 2.2)

1. Sur un téléphone neuf ou qui installe l'appli pour la première fois, réserve un cadeau pour ce membre depuis un autre compte, téléphone verrouillé. **Attendu :** l'écran verrouillé montre seulement « Familia » et le canal, sans le texte. Même chose pour une capsule qui s'ouvre.
2. Refais l'essai sur un téléphone **qui avait déjà l'appli** avant cette version. Android ne change pas la visibilité d'un canal déjà créé, c'est le serveur qui la demande alors à chaque notification. **Attendu :** le texte reste caché sur l'écran verrouillé. S'il s'affiche, désinstalle et réinstalle l'appli sur ce téléphone avant le 21 (ou, dans les réglages Android de Familia, mets le canal Cadeaux sur « Masquer le contenu sensible »).

## La semaine de la famille (récit 36)

Avec le module « Récap du dimanche » allumé, et deux comptes : un qui suit un voyage en cours, un autre décoché de ce voyage.

1. Un dimanche après 18 h, regarde les notifications. **Attendu :** une seule « La semaine de la famille », discrète, avec par exemple « 3 photos, 1 anniversaire à venir, défi à 40 %, 1 voyage en cours. »
2. Avec le compte décoché du voyage, lis la même notification. **Attendu :** elle ne parle pas de ce voyage.
3. Touche la notification. **Attendu :** l'écran « La semaine de la famille » s'ouvre, avec au plus 3 photos, les anniversaires des 14 prochains jours, la jauge du défi et les voyages en cours. Aucun nombre de réactions, aucun nom sous les photos.
4. Touche une photo, puis reviens, puis touche un voyage. **Attendu :** la Bonne nouvelle, puis le voyage, s'ouvrent.
5. Réglages > Notifications : coupe « La semaine de la famille ». **Attendu :** le dimanche suivant, plus de notification pour ce compte.

## Il y a un an (récit 36)

Avec le module « Il y a un an » allumé, et une Bonne nouvelle (ou une photo dans l'album d'un voyage) publiée le même jour, un an plus tôt. Pour l'essai, change la date du téléphone d'un an en avant.

1. Ouvre l'Accueil. **Attendu :** la carte « Il y a un an » montre la Bonne nouvelle (ou la photo de voyage), sans notification.
2. Touche-la. **Attendu :** la Bonne nouvelle, ou le voyage, s'ouvre.
3. Avec un compte qui ne suit pas ce voyage, ouvre l'Accueil. **Attendu :** pas de carte pour cette photo.
4. Touche « Ne plus me montrer ce souvenir ». **Attendu :** la carte disparaît (ou passe au souvenir suivant du même jour), et ne revient pas en rouvrant l'appli.

## Vœux d'anniversaire (récit 36)

Avec le module « Vœux d'anniversaire » allumé, un membre dont c'est l'anniversaire (sa date de naissance mise au jour du test), et deux autres comptes.

1. Après 10 h, regarde les notifications d'un autre compte. **Attendu :** « C'est l'anniversaire de … », « Envoie-lui tes vœux. » ; la personne fêtée ne la reçoit pas.
2. Touche la notification. **Attendu :** la fiche de la personne s'ouvre, avec « Cartes de vœux » et « Écris un mot à … ».
3. Écris un mot et touche « Envoyer ma carte ». **Attendu :** la carte s'affiche, signée « De … ». Aucun nombre de cartes nulle part.
4. Sur le téléphone de la personne fêtée. **Attendu :** une notification « … t'a envoyé une carte de vœux. » ; après une deuxième carte, une seule notification avec les deux prénoms.
5. Change ta carte, puis retire-la. **Attendu :** le texte change, puis la carte disparaît.
6. Le lendemain, rouvre la fiche. **Attendu :** les cartes sont toujours là, mais plus de champ pour écrire.
7. Réglages > Notifications : coupe « Vœux d'anniversaire ». **Attendu :** plus d'avis de vœux pour ce compte.
8. Ouvre les Bonnes nouvelles. **Attendu :** « 🎂 Joyeux anniversaire … ! » de Familia, avec un gâteau à la place de la photo de profil. Touche-la : les mêmes cartes de vœux s'affichent sous la nouvelle, avec le champ pour écrire le jour même.

## Une idée ? Un souci ? (récit 6 sexies)

Avant ces essais : « Publier Firebase » a publié les règles (Firestore et Storage) et les fonctions `onFeedbackSent`, `onFeedbackAnswered` et `onFeedbackRemoved`. Pas d'interrupteur.

1. Avec un compte qui n'est pas admin principal, ouvre **Famille** et touche **Une idée ? Un souci ?**. Choisis « Un problème », écris une phrase et touche **Envoyer**. **Attendu :** « Merci ! C'est envoyé à l'admin de la famille. »
2. Sur le téléphone de l'admin principal. **Attendu :** une notification « Souci signalé par … » avec le début du texte. La toucher ouvre **Retours**.
3. Ouvre **Administration**. **Attendu :** la ligne **Retours** montre « 1 nouvelle(s) ».
4. Touche **Retours**. **Attendu :** la demande est marquée « Un problème · Nouvelle », avec le prénom, la date, la version de l'appli, celle d'Android, le modèle du téléphone et « depuis Famille ».
5. Touche **Répondre**, choisis **Prévue**, écris « Pour la prochaine version » et touche **Enregistrer**. **Attendu :** la carte montre « Prévue » et « Réponse : Pour la prochaine version » ; le compteur d'Administration a disparu.
6. Sur le téléphone de l'auteur. **Attendu :** une notification « Ta demande est prévue » avec le mot. La toucher ouvre **Mes demandes**, qui montre la demande, son état et la réponse.
7. Avec ce même compte, ouvre **Famille > Aide**, puis **Une idée ? Un souci ?** en bas de la page. Envoie une idée. **Attendu :** dans **Retours**, elle porte « depuis l'Aide ».
8. Côté admin principal, supprime une demande. **Attendu :** elle disparaît de la liste, et de « Mes demandes » chez son auteur.
9. En mode **Voir comme**. **Attendu :** pas de ligne « Une idée ? Un souci ? », ni dans Famille ni dans l'Aide.
10. Avec un compte qui n'est pas admin principal, ouvre le formulaire, touche **Ajouter une capture d'écran** et choisis une capture. **Attendu :** elle s'affiche sous le texte, avec **Retirer la capture**. Envoie. **Attendu :** dans **Retours**, la capture s'affiche dans la carte ; elle n'est visible ni pour un autre membre ni dans un autre compte.
11. Mode avion, avec une capture. **Attendu :** au bout d'un moment, « L'envoi n'a pas marché », et le texte et la capture restent là. Sans capture, l'envoi part plus tard tout seul.
12. Dans **Retours**, touche **Copier pour Claude**, puis colle dans une note. **Attendu :** le type, la date, l'écran, le téléphone, l'état et le texte, sans le prénom de l'auteur.

## Accueil plus calme (DESIGN-11, PRD rév. 205)
1. Thème Classique clair : au plus 4 blocs, dans cet ordre : « À faire pour moi » (en relief), « Cette semaine » (3 lignes et « Tout le planning › »), « Bonnes nouvelles ». Les cartes sont blanches, avec une pastille de couleur devant le titre quand il y en a une.
2. Sous les blocs, une rangée de puces : Anniversaires d'abord, puis la question, l'arbre, le diaporama (à partir du dernier dimanche du mois), les voyages, la prochaine occasion, « Il y a un an » et « Dire où je suis ». Une puce n'apparaît que si elle a quelque chose à dire.
3. Toucher « Anniversaires », « L'arbre », « Voyages » ou « Il y a un an » ouvre sa carte sous la rangée ; la toucher encore la referme. Une seule carte est ouverte à la fois. Les autres puces ouvrent leur écran.
4. Avec un deuxième téléphone, envoyer « Rejoignez-moi » : le lieu reçu apparaît en tête de « À faire pour moi », avec « Itinéraire ». Le bloc apparaît même sans autre chose à faire.
5. Thème sombre puis Contraste fort : les cartes gardent leurs teintes, et Contraste fort ses bordures.

## La bienvenue (récit 43)

**À garder éteint pour la démo du 21/11** : ce jour-là, toute la famille se connecte pour la première fois, et chacun recevrait une notification par nouvel arrivant. Ne l'allume qu'après, pour ceux qui arrivent plus tard.

Avec le module « La bienvenue » allumé, un membre ajouté qui ne s'est jamais connecté, et deux autres comptes.

1. Connecte-toi pour la première fois avec le compte du nouveau membre. **Attendu :** les autres comptes reçoivent « … vient d'arriver », « Dis-lui bonjour ! ». Le nouveau membre ne la reçoit pas.
2. Touche la notification. **Attendu :** la fiche du nouveau membre s'ouvre.
3. Déconnecte-toi, puis reconnecte-toi avec ce compte. **Attendu :** aucune nouvelle notification.
4. Fais de même avec un membre du foyer de test. **Attendu :** aucune notification.

## Planning et fiche d'un événement (DESIGN-11)
1. Planning : les marges sur les côtés passent à 20 dp, la liste reste la même.
2. Fiche d'un événement : « Ajouter à mon agenda » et « Modifier » deviennent des liens « › ». Le seul bouton plein reste la réponse.
3. Module Dépenses allumé, sur un événement où tu as répondu « Je viens » : **Attendu :** le lien « Comptes de l'événement › » ouvre les comptes de cet événement. « Ajouter une dépense » les enrichit, et l'historique d'une dépense s'ouvre. Retour : la fiche de l'événement.
4. Même événement, réponds « Peut-être ». **Attendu :** le lien disparaît, sauf si tu es l'auteur de l'événement.
5. Avec un compte d'enfant, ou module Dépenses éteint : **Attendu :** jamais de lien « Comptes de l'événement ».

## Accueil : rien ne trahit une surprise (démo du 21/11)
1. Avec un compte A, créer une liste surprise pour B, datée dans 5 jours. Sur l'Accueil de B, et en Voir comme B depuis l'admin principal, aucune puce ne parle de cette liste.
2. B crée sa propre liste d'anniversaire datée dans 3 jours : sur son Accueil, la puce dit « Ton anniversaire · dans 3 jours », sans titre de liste, et ouvre ses envies.
3. Sur l'Accueil de A (lecteur de la liste de B), la puce montre le titre de la liste et le délai.
4. Sans voyage en cours ni à venir, la puce « Voyages » n'apparaît pas ; « Dire où je suis » reste la dernière puce.

## Famille : « Nos trésors » (DESIGN-11)
1. Onglet Famille, avec Recettes, Capsules et Arbre généalogique allumés : une seule ligne « Nos trésors » sous les membres. La toucher montre les trois entrées en retrait ; la toucher encore les cache. TalkBack lit « ouvert » ou « fermé ».
2. Éteindre les trois modules dans Administration : la ligne « Nos trésors » disparaît.
3. Les marges sur les côtés passent à 20 dp.

## Administration (DESIGN-11)
1. Administration : les marges sur les côtés passent à 20 dp, dans toutes les sections.
2. Les cartes « Consommation » et « Capsules restantes » ont leur titre avec la pastille en thème clair.

## Une phrase d'aide par écran au plus (DESIGN-11)
1. Administration : sous les titres de section, seule la phrase des modules reste (« Un module éteint est caché à la famille… »). Le « ? » en haut à droite explique les chefs, les foyers, le défi et le tour des foyers.
2. Rejoignez-moi : une seule phrase sous « Envoyer » (« Ta position est prise une seule fois… ») ; le reste est dans le « ? ».
3. Sauvegarde, Cadeaux reçus et « Une idée ? Un souci ? » : une seule phrase en tête.
4. Accueil, « ? » : l'aide parle des blocs et des puces.

## Le coin des petits (récit 43)

Avec le module « Le coin des petits » allumé, un compte adulte qui a un enfant dans son foyer, et un autre compte.

1. Publie une Bonne nouvelle « Pour moi ». **Attendu :** pas d'interrupteur « Les petits ».
2. Publie-en une pour l'enfant, et allume « Dans « Les petits » ». **Attendu :** la carte porte l'étiquette « Les petits », dans le fil et sur sa page.
3. Avec l'autre compte, ouvre les Bonnes nouvelles et touche le filtre « Les petits ». **Attendu :** seules celles des petits restent. Touche-le encore : tout revient.
4. Éteins le module. **Attendu :** ni l'interrupteur, ni le filtre, ni l'étiquette pour les membres (l'admin principal voit toujours tout).

## Voir comme : aucune écriture à l'ouverture
1. Admin principal : Administration › Voir comme › un membre. Naviguer dans tous les onglets : aucun message « Lecture seule » n'apparaît tant qu'on ne touche aucun bouton d'action.
2. Quitter Voir comme : les notifications arrivent toujours sur le téléphone de l'admin (il n'a pas été inscrit pour le membre regardé).

## Le petit geste du jour (récit 43)

Avec le module « Le petit geste du jour » allumé, deux comptes adultes.

1. Avec le premier compte, publie une Bonne nouvelle avec une photo. **Attendu :** sur l'Accueil du second compte, le bloc « À faire pour moi » montre « La photo de … attend ta réaction » et « Voir la photo ».
2. Touche « Voir la photo », puis réagis. **Attendu :** de retour sur l'Accueil, la ligne a disparu. Aucune notification n'est partie.
3. En « Voir comme », ouvre l'Accueil d'un membre. **Attendu :** la ligne s'affiche pour lui, et rien n'est écrit.
4. Éteins le module. **Attendu :** plus de ligne pour les membres.

## À découvrir (AVIS-19)
Avec l'interrupteur « À découvrir » allumé (l'admin principal le voit même éteint). Pour essayer avant le 30/11, mets la date du téléphone au lundi 30/11/2026 ou après.
1. Lundi 30/11 : l'Accueil montre « À découvrir » en 4e bloc, après « Bonnes nouvelles », avec « La question de la semaine ». **Attendu :** « Essayer » ouvre la question de la semaine.
2. Reviens sur l'Accueil. **Attendu :** la carte a disparu, et elle ne revient pas après avoir fermé l'appli.
3. Avec un autre compte, touche « Plus tard ». **Attendu :** la carte disparaît pour ce compte seulement.
4. Lundi 07/12 : **Attendu :** « Sondages et Qui apporte quoi ». L'Entraide est sautée, car le tuto la montre déjà.
5. En Voir comme : **Attendu :** pas de « Plus tard », et « Essayer » ouvre le module sans cacher la carte du membre regardé.
6. Avant le 30/11 ou interrupteur éteint : **Attendu :** pas de carte. Aucune notification n'est envoyée.

## Photos : envoi refusé par le serveur (après #167 et « Publier Firebase »)
1. Ajoute une photo à un voyage dont tu es participant, à une recette que tu as écrite et à l'album d'un événement où tu as dit « je viens ». **Attendu :** les trois passent.
2. Sur ces trois cas, si une photo échoue : **Attendu :** le message dit si la photo est illisible, si le serveur l'a refusée, ou si c'est le réseau.
3. Limites connues (une règle Storage ne lit que deux documents en production) : l'admin principal ne peut pas ajouter de photo à la recette de quelqu'un d'autre (« refusée par le serveur »). Quand il retire la photo d'un autre sans être le créateur du voyage ou de l'événement, la photo disparaît bien de l'écran, mais son fichier reste stocké jusqu'à la suppression du voyage ou de l'événement.

## « Qui est-ce ? » (récit 43, interrupteur whoIsIt)
1. Allume l'interrupteur, puis Famille > « Qui est-ce ? » > « Publier une photo » : une vieille photo, un indice, qui est dessus et l'année. **Attendu :** la photo s'ouvre, avec ta réponse et « Personne n'a encore deviné ».
2. Avec un autre compte : la puce « Qui est-ce ? » apparaît sur l'Accueil. Devine. **Attendu :** la réponse et les devinettes s'affichent juste après, sans score.
3. « Je ne sais pas » : **Attendu :** la réponse s'affiche aussi.
4. « Ne plus me montrer » : **Attendu :** la puce disparaît sur ce téléphone.
5. En Voir comme : **Attendu :** on lit, mais on ne devine pas, on ne publie pas et on ne retire rien.

## Le petit geste du jour dans « À faire pour moi »
Avec l'interrupteur « Le petit geste du jour » allumé, et une Bonne nouvelle avec photo publiée par un autre membre, sans ta réaction.
1. Accueil : **Attendu :** une ligne dans « À faire pour moi », sous ce que « Rejoignez-moi » t'a envoyé et au-dessus des réponses à donner, avec un trait entre chaque partie.
2. Touche le lien. **Attendu :** la Bonne nouvelle s'ouvre.
3. Sans rien d'autre à faire : **Attendu :** le bloc « À faire pour moi » s'affiche quand même, avec la ligne seule.

## Un sondage voté reste sur l'Accueil
1. Réponds à un sondage depuis « À faire pour moi ». **Attendu :** il reste dans le bloc, avec « Tu as voté · en tête : … (n voix) », et l'ouvrir montre les résultats.
2. Un sondage dont la date de fin est passée depuis moins de 2 jours : **Attendu :** « Terminé · en tête : … » (ou « retenu : … » si l'auteur a choisi). Au-delà de 2 jours, il disparaît.

## Des sondages faciles à trouver (BUG-1)
1. Planning : en haut, un bouton « Sondages », avec une pastille qui compte les sondages qui attendent ta réponse. TalkBack lit le nombre.
2. Accueil : un bloc « Sondages » après « Cette semaine ». **Attendu :** d'abord ceux où tu n'as pas répondu (« Sondage de … · à toi de répondre »), puis ceux où tu as voté. « Nouveau sondage » pour un adulte, et « Tous les sondages ».
3. Sans aucun sondage à montrer : **Attendu :** pas de bloc Sondages.
5. Avec 4 blocs (À faire, Cette semaine, Sondages, Bonnes nouvelles) et la carte « À découvrir » de la semaine (dès le 30/11) : **Attendu :** « À découvrir » devient la première puce, et la toucher ouvre sa carte sous la rangée.
4. « À faire pour moi » ne liste plus les sondages.

## Les recettes faciles à trouver
1. Famille : en haut, « Nos trésors » avec les tuiles Recettes, Arbre, Capsules et Qui est-ce ?, chacune seulement si son module est allumé. Avec les quatre, elles vont sur deux rangées de deux. **Attendu :** chaque tuile ouvre son écran. Avec un seul module allumé, la tuile prend toute la largeur.
2. Avec un autre compte, ajoute une recette. **Attendu :** sur ton Accueil, la puce « Nouvelle recette : … » ouvre cette recette. Elle disparaît 7 jours après. L'auteur ne la voit pas.

## « Quoi de neuf ? » (récit 6 septies)
Il faut une version publiée avec un fichier dans `docs/whats-new`, et un téléphone qui avait déjà l'appli.
1. Installe la mise à jour, puis ouvre l'appli. **Attendu :** la feuille « Quoi de neuf ? » montre les cartes de la version, les plus récentes d'abord, et on passe de l'une à l'autre en glissant.
2. « Essayer » : **Attendu :** l'écran de la carte s'ouvre, et la feuille ne revient pas à l'ouverture suivante.
3. « Plus tard » : **Attendu :** la feuille revient à l'ouverture suivante, puis plus jamais.
4. Sur un téléphone neuf : **Attendu :** le tuto, puis rien. La feuille ne s'ouvre pas.
5. Une carte d'un module éteint : **Attendu :** absente. Allume le module dans Administration, ferme et rouvre l'appli : **Attendu :** la carte apparaît.
6. Avec un compte enfant : **Attendu :** le texte pour enfant, et aucune carte réservée aux adultes.
7. En Voir comme : **Attendu :** aucune feuille.
8. Aide, ou menu de l'avatar, puis « Quoi de neuf ? » : **Attendu :** toutes les versions, la plus récente en haut.

## Ma photo en haut à droite (DESIGN-12)
1. Sur chacun des 5 onglets : **Attendu :** ta photo (ou tes initiales) à droite de la barre du haut, après le « ? ». TalkBack lit « Mon compte et réglages ».
2. Touche-la. **Attendu :** une feuille s'ouvre avec, dans l'ordre : Mon profil, Apparence, Notifications, Aide, Vérifier les mises à jour, « Une idée ? Un souci ? », puis Partager Familia pour l'admin principal seulement. Une ligne absente veut dire que son module est éteint.
3. Avec le compte de l'admin principal : **Attendu :** « Administration » en dernier, après un trait. Avec un autre compte, pas de ligne Administration.
4. Avec un parent d'un enfant qui a l'appli (pas l'admin) : **Attendu :** « Temps de mes enfants » dans la feuille.
5. Touche une ligne. **Attendu :** la feuille se ferme et l'écran s'ouvre.
6. Onglet Famille : **Attendu :** « Nos trésors » puis les membres, et plus aucun réglage en bas.
7. En « Voir comme » : **Attendu :** ni « Partager Familia » ni « Une idée ? Un souci ? » dans la feuille.

## Joindre une recette dans « Qui apporte quoi »
Avec les modules « Qui apporte quoi » et Recettes allumés, et au moins une recette dans le carnet.
1. Un événement, « Qui apporte quoi » : touche « Joindre une recette ». **Attendu :** le choix d'une recette, avec la recherche par titre.
2. Choisis-en une sans rien avoir écrit. **Attendu :** « Recette jointe : … » et le titre de la recette dans le champ. « Retirer la recette » l'enlève.
3. Ajoute la ligne. **Attendu :** la ligne montre « Voir la recette », qui ouvre la recette. Pareil sur un autre téléphone.
4. Module Recettes éteint : **Attendu :** ni « Joindre une recette » ni « Voir la recette ».

## Pensez à moi (récit 43)

Avec le module « Pensez à moi » allumé, un adulte parent d'un enfant de moins de 15 ans et d'un ado de 15 ans ou plus (date de naissance remplie), et un autre compte.

1. Sur ta propre fiche, note « Examen » pour demain. **Attendu :** il apparaît sous « Pensez à moi », avec « Retirer ».
2. Sur la fiche de l'enfant, note un moment. **Attendu :** il est accepté tout de suite. Sur celle de l'ado : « … choisira de l'accepter ou non », puis « En attente de la réponse de … ».
3. Avec le compte de l'ado, ouvre sa fiche. **Attendu :** « Noté par … » avec « Oui » et « Non merci ».
4. Le jour J vers 10 h. **Attendu :** les autres reçoivent « Pensez à … aujourd'hui ». Sur la fiche, « Aujourd'hui : « … » » et un champ pour un mot. Le membre reçoit « … pense à toi. » à chaque mot. Rien le lendemain.
5. En « Voir comme », ouvre une fiche. **Attendu :** ni champ ni bouton, la lecture seule.

## La photo d'un événement (récit 44)

Avec le module « Plus de photos » allumé, après « Publier Firebase », et un autre compte.

1. Crée un événement et touche « Ajouter une photo », puis « Prendre une photo ». **Attendu :** la photo s'affiche en large dans le formulaire, avec « Changer la photo » et « Retirer la photo ».
2. Enregistre. **Attendu :** la photo en bandeau en haut de la fiche, au-dessus du titre, une vignette sur la ligne du Planning et dans « Cette semaine » sur l'Accueil. Pareil sur l'autre téléphone.
3. Modifie l'événement et choisis une autre photo dans la galerie. **Attendu :** la nouvelle photo partout après l'enregistrement.
4. « Retirer la photo » demande une confirmation. Enregistre. **Attendu :** plus de bandeau ni de vignette, la ligne garde son icône habituelle.
5. Avec un compte qui ne peut pas modifier l'événement : **Attendu :** aucune photo à ajouter, la fiche montre seulement le bandeau.
6. Module éteint. **Attendu :** ni photo dans le formulaire, ni bandeau, ni vignette.

## L'ordre d'« À découvrir » (AVIS-19 partie 2)
Avec le compte de l'admin principal et le module « À découvrir » allumé.
1. Administration : **Attendu :** une section « Ordre d'« À découvrir » » avec les 7 modules, chacun avec sa semaine (« Semaine du 30 novembre »…). Entraide dit « Déjà dans le tuto, pas de semaine » tant que son module est allumé.
2. Monte « Recettes » en premier. **Attendu :** les semaines se recalculent, et « Enregistrer l'ordre » apparaît. Touche-le.
3. Dès le 30/11, sur un autre téléphone : **Attendu :** la carte de la première semaine est celle des recettes.

## Partager un anniversaire depuis la liste
Avec le module Partager allumé.
1. Cadeaux, « Tous les anniversaires » : **Attendu :** sur chaque ligne, un bouton Partager après « Ajouter à mon agenda ». TalkBack lit « Partager l'anniversaire de … ».
2. Touche-le. **Attendu :** la feuille d'Android, avec l'image, « Anniversaire de … : … ans le … » et le lien. Rien sur les listes ni les cadeaux.
3. Module éteint : **Attendu :** pas de bouton.


## Une surprise reste cachée en « Voir comme »
1. Avec l'admin, prépare une liste surprise pour un membre A, dont l'admin est lecteur.
2. « Voir comme » A, puis ouvre le lien partagé de cette liste (ou une notification qui y mène). **Attendu :** « Liste introuvable », comme pour A sur son téléphone. La liste ne s'affiche pas.
3. « Voir comme » un autre lecteur de la liste. **Attendu :** la liste s'ouvre.

## Le Planning à 200 %
1. Réglages Android, taille de police au maximum. Planning : **Attendu :** « Sondages » devient son icône seule, avec la pastille du nombre. Le titre « Planning », le « ? » et ta photo tiennent sur la barre. TalkBack lit « Sondages » et le nombre en attente.
2. Taille normale : **Attendu :** le bouton « Sondages » écrit, comme avant.

## Partager une bonne nouvelle ou un coup de main (récit 31)
Avec l'interrupteur « Partager » allumé :
1. Ouvre une bonne nouvelle d'un adulte avec une photo, touche Partager en haut : l'image porte la photo, le message donne le début du texte et le lien.
2. Ouvre la bonne nouvelle d'un enfant d'un autre foyer : l'image part sans la photo. Celle de ton enfant part avec.
3. Ouvre un coup de main que tu as demandé à toute la famille, encore ouvert : Partager donne le titre et le moment, sans le lieu ni la note. Le bouton n'apparaît pas sur une demande faite à quelques personnes seulement, ni une fois la demande prise, ni sur celle de quelqu'un d'autre.

## Une photo dans un coup de main (Kévin 16/11)
1. Demande un coup de main, touche « Ajouter une photo », prends la photo d'un QR code ou choisis-la dans la galerie, puis envoie.
2. Sur un téléphone prévenu, ouvre la demande : la photo est entière, sans être coupée. Touche-la : elle s'ouvre en grand sur fond noir, prête à être scannée. « Fermer » revient à la demande.
3. Sur le téléphone d'un membre non prévenu, la demande n'apparaît pas, et la photo non plus.
4. Modifie la demande, touche « Retirer la photo », enregistre : elle disparaît pour tout le monde.

## Les sondages d'un événement (récit 2 quater)
Avec l'interrupteur « Sondages » allumé :
1. Ouvre un événement de ton foyer et touche « Lancer un sondage » : les invités de l'événement sont déjà cochés. Envoie un sondage de date.
2. Reviens sur la fiche : le sondage y apparaît, avec les réponses puis le résultat une fois terminé. Il est aussi dans la liste « Sondages ».
3. Supprime l'événement : le sondage reste dans « Sondages », marqué Terminé, et personne ne reçoit de notification de résultat.
