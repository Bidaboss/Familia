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
15. Remets le mode avion, crée un coup de main « Essai fermé », puis ferme l'appli (balaye-la des applis récentes). Réactive le réseau et rouvre Familia. **Attendu :** le coup de main arrive sur le deuxième téléphone, et aucune fenêtre « Pas parti » ne s'ouvre. Elle ne s'ouvre que si le serveur a refusé ce qui avait été préparé sans réseau, et elle le nomme. Annule ensuite « Essai fermé ».

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

1. Administration > « Vie de la famille » > « Ordre des foyers » : mets ton foyer en premier, puis touche « Enregistrer l'ordre ». **Attendu :** ton foyer est en face du mois prochain, les autres suivent, un mois chacun.
2. À partir de 7 jours avant le mois prochain, ouvre l'Accueil. **Attendu :** la carte de l'arbre dit « C'est à vous de lancer le défi de … ! » avec « Préparer le défi ».
3. Avec le compte enfant, touche « Préparer le défi », choisis « Cuisine », un objectif, puis un nom, un emoji et une illustration. À l'aperçu, touche « Enregistrer pour le foyer ». **Attendu :** pas de bouton « Publier » ni « Passer notre tour » ; la phrase « Un adulte du foyer le publiera. »
4. Avec le compte adulte, rouvre le guide. **Attendu :** le brouillon de l'enfant est là. Touche « Publier le défi ». **Attendu :** le guide se ferme et la bannière disparaît de l'Accueil.
5. Avec un autre foyer, refais l'étape 2 au mois de son tour, puis touche « Passer notre tour » et confirme. **Attendu :** la bannière disparaît, et le défi du mois reste celui de Familia.

## Défis : comment ça marche (récit 65, interrupteur challengeDetails)

Après « Publier Firebase », allume « Défis expliqués » dans Administration > Modules.

1. Ouvre Défis et touche n'importe où sur la carte du défi. **Attendu :** une feuille « Comment ça marche » : ce qui compte, l'objectif (« 7 sur 20 »), la date de fin, ce qu'on gagne, puis « Ajouter ma photo » ou « Partager une bonne nouvelle », ou « Défi réussi ». Aucun prénom.
2. Avant le 1er décembre, touche la carte du défi, puis celle de la question. **Attendu :** le principe, avec « Premier défi le 1er décembre. » et « Première question le lundi 30 novembre. »
3. Touche n'importe où sur la carte de la question (même interrupteur éteint). **Attendu :** l'écran pour répondre, ou les réponses si tu as déjà répondu.
4. Touche une tuile « Défi … réussi ». **Attendu :** l'explication de ce défi, avec « Défi réussi ».
5. Dans le guide « Préparer le défi », écris un mot à l'étape 3, puis publie. **Attendu :** le mot apparaît dans « Comment ça marche » pour toute la famille. Sans mot, le texte automatique.
6. Avec TalkBack, passe sur chaque carte. **Attendu :** TalkBack dit « bouton » et ce que fait la carte.

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
3. Touche la notification. **Attendu :** l'écran « La semaine de la famille » s'ouvre, avec au plus 3 photos (une par jour où il y en a, de personnes différentes quand c'est possible, jamais choisies selon les cœurs), les anniversaires des 14 prochains jours, la jauge du défi et les voyages en cours. Aucun nombre de réactions, aucun nom sous les photos.
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
13. Sur un retour avec capture, touche **Enregistrer la capture**. **Attendu :** « Capture enregistrée dans la galerie, album Familia ». Dans Galerie (ou Google Photos), l'album Familia contient la capture. Dans l'appli Claude, ouvre le projet, touche « + » puis Photos : elle y est. Sans capture, pas de bouton. Mode avion : « Capture non enregistrée ».

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

## L'écran Défis (R1 bis, maquette R7)

1. Ouvre « Défis » depuis Tout Familia. En haut, la carte noire : « DÉFI DE DÉCEMBRE », le défi, la jauge jaune et « On le réussit ensemble, sans classement ». Avant décembre : « Premier défi le 1er décembre ».
2. Défi lancé par un foyer : « Ajouter ma photo » ouvre une bonne nouvelle liée au défi. Si c'est au tour de ton foyer : « Préparer le défi ».
3. Carte blanche « QUESTION DU LUNDI » : la question et le nombre de réponses, jamais qui. Sans réponse de ta part : « Tu réponds pour les voir » et « Répondre ». Avant le 30/11 : « Première question le lundi 30 novembre ».
4. « Nos souvenirs gagnés ensemble » : l'arbre et ses feuilles, un souvenir par défi réussi, puis « À venir ». Rien n'appartient à une seule personne.
5. Arbre ou question éteints dans Administration : leur carte disparaît de l'écran.
6. Police à 200 % : tout défile.

## Les sondages d'un événement (récit 2 quater)
Avec l'interrupteur « Sondages » allumé :
1. Ouvre un événement de ton foyer et touche « Lancer un sondage » : les invités de l'événement sont déjà cochés. Envoie un sondage de date.
2. Reviens sur la fiche : le sondage y apparaît, avec les réponses puis le résultat une fois terminé. Il est aussi dans la liste « Sondages ».
3. Supprime l'événement : le sondage reste dans « Sondages », marqué Terminé, et personne ne reçoit de notification de résultat.

## « Lancer le diaporama » dans chaque album (R1 bis, DESIGN-18)

1. Ouvre l'album d'un voyage, d'un événement ou du mois avec au moins 2 photos : « Lancer le diaporama » est sous le titre. Avec une seule photo, il n'y est pas.
2. Touche-le : les photos défilent sur fond noir, de la plus ancienne à la plus récente, toutes les 4 secondes, en fondu, puis recommencent. L'écran ne s'éteint pas pendant ce temps.
3. « Pause » s'arrête sur la photo, « Reprendre » repart. « Fermer » ou le bouton retour revient à l'album.
4. Interrupteur presentationR1bis éteint : le bouton disparaît.

## L'écran Albums (R1 bis, Tout Familia)

1. Ouvre « Albums » depuis Tout Familia : les voyages que tu suis et les événements qui te concernent, de la dernière année, avec au moins une photo, du plus récent au plus ancien. Chaque ligne montre la dernière photo, le nombre de photos et le mois.
2. Touche un album : il s'ouvre, avec « Lancer le diaporama ».
3. Sous « Les mois » : un mois par mois de ces albums. Touche-le : le diaporama du mois s'ouvre.
4. Un voyage à venir, ou un album sans photo, n'apparaît pas. Sans aucun album : « Les premiers souvenirs arrivent bientôt », avec « Voir les voyages ».
5. Voyages ou albums d'événements éteints dans Administration : leurs albums disparaissent de la liste.

## Le voyage en chemin illustré (R1 bis, maquette R6)

Interrupteur « presentationR1bis » allumé (c'est le réglage par défaut).

1. Ouvre un voyage en cours. En haut, sur le ciel bleu : le nom du voyage et qui part, puis la frise des jours (« Jour 1 », « dim 15 »…). Le jour d'aujourd'hui est en noir.
2. Dessous, le paysage : le chemin passe par 4 jours au plus. Les jours passés sont des ronds noirs, le jour choisi est jaune avec l'étiquette « Aujourd'hui, » et le lieu de sa première étape, les jours à venir sont en pointillés.
3. Touche un autre jour dans la frise : le chemin, la carte du jour et le carnet suivent. Aujourd'hui garde un contour noir.
4. Carte du jour : « Jour 3 : » et les lieux du jour, puis « 18 photos » (ouvre l'album), « Carnet » (descend au carnet du jour) et « Comptes » (seulement si tu pars et que les comptes sont allumés).
5. Le carnet du jour montre les étapes de ce jour, avec « Bien arrivé ». Un hébergement de plusieurs nuits apparaît chaque jour. Un jour sans étape dit « Rien de noté ce jour-là ».
6. Un voyage pas encore commencé s'ouvre sur son premier jour, un voyage fini sur son dernier.
7. TalkBack : chaque jour de la frise se lit « Jour 3, mardi 17 novembre » ; le paysage n'est pas lu.
8. Police du téléphone à 200 % : tout défile, rien ne passe sous le bouton « Ajouter une étape ».
9. Interrupteur éteint : l'écran du voyage revient à l'ancienne présentation.

## La petite histoire d'une recette (récit 47, partie 1)

Interrupteur « Livre de recettes » allumé dans Administration, après « Publier Firebase ».

1. Modifie une de tes recettes : sous les étiquettes, « Sa petite histoire (facultatif) ». Écris deux phrases et enregistre. **Attendu :** l'histoire apparaît en italique sous le titre de la recette.
2. Plus de 600 caractères : « Texte trop long ». La recette n'est pas enregistrée.
3. Sous « Photos du plat », aucune case « Un enfant est sur une des photos » (retirée le 17/11), avec ou sans photo.
4. Interrupteur éteint : ni le champ, ni l'histoire. Une histoire déjà écrite n'est pas perdue quand on modifie la recette.

## La photo d'une envie (récit 44)

Avec les modules « Plus de photos » et « Liste unique » allumés, après « Publier Firebase », et le compte d'une autre personne.

1. Dans « Mes envies », ajoute une envie avec « Ajouter une photo ». **Attendu :** une vignette sur la carte de l'envie, et la photo en large quand on l'ouvre. L'autre compte la voit aussi.
2. Avec l'autre compte, ajoute une idée avec une photo dans « Idées de la famille » de la première personne. **Attendu :** la famille voit la photo, mais la personne fêtée ne voit ni l'idée ni la photo.
3. Modifie l'envie et retire sa photo, après confirmation. **Attendu :** plus de vignette.
4. Supprime une envie qui avait une photo. **Attendu :** elle disparaît, et sa photo aussi (le serveur la retire).
5. Module éteint. **Attendu :** ni bouton photo, ni vignette.

## Nouvelle présentation « R1 bis » (spec DESIGN du 17/11)

Interrupteur « Nouvelle présentation » (presentationR1bis), allumé par défaut, admin principal compris. L'éteindre dans Administration rend l'ancienne présentation à tout le monde.

1. Ouvre l'**Accueil**. **Attendu :** « Bonjour » et ton prénom en titre, puis « + », « ? » et ta photo en haut ; la frise des 7 jours à partir d'aujourd'hui, aujourd'hui entouré.
2. Touche un autre jour de la frise. **Attendu :** il passe en noir, aujourd'hui garde son contour, et la carte dessous montre ce jour : heure, trait de couleur, quoi. Un jour vide dit « Rien de prévu. Une journée tranquille. ».
3. Un jour avec 2 événements ou plus. **Attendu :** un petit rond avec le nombre en haut à droite de la case. TalkBack lit « Mercredi 18, 2 événements ».
4. Un événement auquel tu n'as pas répondu. **Attendu :** une carte dans « À faire » avec « J'y vais » et « Non ». Touche « J'y vais » : la carte disparaît, et l'événement te compte parmi ceux qui viennent.
5. Touche « + ». **Attendu :** Un événement, Une photo, Une bonne nouvelle, Une envie (2 × 2), Un coup de main sur toute la largeur, puis Annuler. « Une photo » ouvre le choix de l'album ; les autres choix ouvrent leur formulaire. Voyages et albums d'événements éteints : « Une photo » disparaît et « Un coup de main » rejoint la grille.
6. Touche « Tout Familia ». **Attendu :** « Chercher un module », puis les modules en ronds sous S'organiser, Se faire plaisir et Se souvenir. Défis et Albums y sont quand leur module est allumé. Tape « ou je » : seul « Où je suis » reste.
7. Onglet **Famille**. **Attendu :** « Nous N, en M foyers », une carte de couleur par foyer avec tous ses membres, puis « Nos trésors ». Deux prénoms qui commencent par la même lettre ont deux lettres dans leur pastille.
8. Onglet **Planning**. **Attendu :** « Semaine » d'abord, la frise du lundi au dimanche, les flèches changent de semaine, « Aujourd'hui » revient à cette semaine. Sous un événement avec des « oui » : les petites pastilles de ceux qui viennent (6 au plus, puis « +N »).
9. En Classique clair, regarde le fond. **Attendu :** de petits pépins discrets ; en Contraste fort, aucun motif. L'onglet allumé est dans une pilule jaune.
10. Taille de police à 130 % puis 200 % : la frise défile de côté, rien ne passe sous la barre du bas.
11. Sur une tablette (ou en paysage large) : le rail de navigation à gauche, « + » en tête sur l'Accueil, l'Accueil sur deux colonnes.
12. Dans Administration, éteins « Nouvelle présentation ». **Attendu :** l'ancien Accueil revient, pour toi aussi. Rallume-la.

## Bons plans dans Entraide (récit 45)
Avec l'interrupteur « Bons plans » allumé (l'admin principal le voit toujours) :
1. Entraide : **Attendu :** deux onglets en haut, « Coups de main » et « Bons plans ». Interrupteur éteint, pour un autre membre : l'onglet Entraide comme avant, sans onglets.
2. Bons plans, « Publier » : le rappel « Pas de mot de passe ni de code de porte ici. » est en tête. Le dernier jour est dans 7 jours ; le calendrier ne propose que d'aujourd'hui à dans 3 mois. Publie avec un titre, un lien tapé court (exemple.fr) et une photo. **Attendu :** la carte dit « Jusqu'au … », ton prénom, le site du lien.
3. Ouvre-le avec un autre membre, touche « Ça m'intéresse ». **Attendu :** « Intéressés : Moi », puis le bouton « Ça ne m'intéresse plus ». Le lien s'ouvre dans le navigateur.
4. Avec un enfant qui a l'appli : **Attendu :** il voit les bons plans et touche « Ça m'intéresse », mais n'a pas de bouton « Publier ».
5. L'auteur ou l'admin principal : « Modifier » et « Supprimer » (demande confirmation). Les autres n'ont pas ces boutons.
6. Le lendemain vers 18 h : **Attendu :** une notification « Nouveau bon plan » (ou « 2 nouveaux bons plans ») sur le canal Coups de main, pour les autres membres seulement.

## Bonnes adresses (récit 45)
Avec l'interrupteur « Bons plans » allumé :
1. Bons plans, « Publier » : **Attendu :** le choix « Un bon plan » ou « Une bonne adresse ». Choisis l'adresse : nom, catégorie (obligatoire), ville, téléphone, lien, « Pourquoi je la recommande ». Pas de date ni de photo.
2. **Attendu :** la section « Bonnes adresses » sous les bons plans, avec la recherche et les catégories. « ecully » trouve « Écully » ; toucher deux fois une catégorie les montre toutes.
3. Avec un autre adulte, ouvre l'adresse : « Recommandée par » l'auteur, puis « Je recommande aussi » ajoute « Moi ». « Appeler le … » ouvre le téléphone.
4. Avec l'auteur ou un enfant : **Attendu :** pas de bouton « Je recommande aussi ». Aucune notification pour une bonne adresse.

## Ajouter une envie depuis une autre appli (récit 50)

Interrupteur « Ajouter une envie depuis une autre appli » (wishFromShare) allumé, avec « Cadeaux ».

1. Dans le navigateur, sur une page de boutique, touche « Partager ». « Ajouter à mes envies » apparaît dans la liste des applis. Touche-le.
2. La feuille s'ouvre avec le titre et le lien de la page. Rien n'est lu sur la boutique : pas de photo ni de prix pré-remplis.
3. Sous « C'est pour qui ? », « Pour moi » est choisi. Enregistre : l'envie est dans « Mes envies ».
4. Recommence et choisis « Une idée pour… » quelqu'un d'autre. La phrase « … ne verra pas cet article » s'affiche. Enregistre : l'idée est dans ses « Idées de la famille ».
5. Avec le compte de cette personne, ouvre Cadeaux : l'idée n'apparaît nulle part.
6. Avec le compte d'un enfant, partage un lien : il n'y a pas « C'est pour qui ? », l'envie va dans ses propres envies.
7. Éteins l'interrupteur, ouvre Familia puis reviens au navigateur : Familia n'est plus dans « Partager ».

## Mon budget cadeaux (récit 57)

Interrupteurs « Budget cadeaux » (giftBudget) et « Carnet de cadeaux » allumés, avec « Cadeaux ».

1. Avec un compte adulte, réserve sur une liste un article avec un prix (30 €), et prends une part de 25 € dans un cadeau commun, tous deux pour Noël.
2. Ouvre « Mes cadeaux à offrir » et choisis Noël. La carte « Mon budget pour Noël 2026 » dit « 55 € déjà prévus ».
3. Touche « Fixer mon budget » et mets 100. La carte dit « Il te reste 45 € sur 100 € », avec une jauge.
4. Touche « Ajouter un achat fait ailleurs » : le formulaire s'ouvre sur Noël, déjà « Acheté ». Note un livre à 15 € pour quelqu'un et enregistre. La carte dit « Il te reste 30 € ».
5. Mets le budget à 50 : le texte et la jauge passent en orange (« 20 € de plus que tes 50 € »), sans fenêtre ni notification.
6. Avec le compte de la personne du livre, et avec celui de l'admin principal : rien de ce budget ni de cet achat n'apparaît, et l'article réservé n'a pas changé.
7. Avec le compte d'un enfant : pas de carte « Mon budget ».
8. Éteins l'interrupteur : la carte disparaît, les budgets par personne restent comme avant.

## Faire le livre de recettes (récit 47, partie 2)

Interrupteur « Livre de recettes » allumé, avec des recettes dont certaines ont une photo, une étiquette et une histoire.

1. Recettes : en haut de la liste, « Faire le livre de recettes ». Il n'apparaît pas sur la liste « Ses recettes » d'un membre.
2. L'écran montre la couverture (titre « Les recettes de la famille », modifiable, et l'année), le choix de la photo de couverture, puis toutes les recettes cochées. « Tout décocher » puis « Tout cocher » marchent ; sans recette cochée ou sans titre, « Faire le livre » est grisé.
3. Touche « Faire le livre » : la feuille de partage d'Android s'ouvre avec livre-de-recettes-2026.pdf. Enregistre-le et ouvre-le.
4. **Attendu dans le PDF :** une couverture, un sommaire par étiquette (la première de chaque recette, « Autres recettes » à la fin) avec les numéros de page, puis une recette par page : titre, « De … · Notée par … · Pour 6 personnes », l'histoire en italique, la photo, les ingrédients et les étapes. Une longue recette continue sur la page suivante.
5. EXPERT-2 : une recette qui vient d'un enfant, quand tu n'es pas un de ses adultes : un dessin de bol à la place de la photo. Une photo prise par un enfant, elle, va dans le livre de tout le monde.
6. Mode avion avec des photos jamais vues : le livre se fait quand même, avec des dessins.

## Faire l'affiche de l'arbre (récit 53)

Interrupteur « Affiche de l'arbre » allumé, avec un arbre qui relie au moins trois générations, dont un enfant de ton foyer et un enfant d'un autre foyer, chacun avec une photo de profil.

1. Arbre généalogique : en haut, « Faire l'affiche de l'arbre ». **Attendu :** absent quand l'interrupteur est éteint, et en « Voir comme ».
2. L'écran montre le titre « L'arbre de la famille » (modifiable) et l'année, puis « À partir de qui ? » avec « Toute la famille » et une ligne par personne qui a des enfants, et enfin la feuille (A4 ou A3) et « Mettre les photos », cochée. Sans titre, « Faire l'affiche » est grisé.
3. Touche « Faire l'affiche » : la feuille de partage d'Android s'ouvre avec arbre-de-la-famille-2026.pdf. Enregistre-le et ouvre-le.
4. **Attendu dans le PDF :** une seule page en paysage. En haut, un bandeau argile avec le titre et l'année. Ensuite, une rangée par génération, les plus âgés en haut : un rond par personne (sa photo, ou son initiale), son prénom et son **année** de naissance seulement. Les couples sont côte à côte, reliés par un trait, et des traits descendent des parents vers leurs enfants.
5. EXPERT-2 : l'enfant de ton foyer a sa photo, et l'enfant de l'autre foyer a son initiale. Refais l'affiche avec un adulte de l'autre foyer : c'est l'inverse.
6. « … et ses descendants » : seuls la personne, ses descendants et leurs partenaires apparaissent, et la personne est tout en haut. Décoche « Mettre les photos » : il ne reste que des initiales. Choisis A3 : même dessin, sur une page plus grande.
7. Mode avion avec des photos jamais vues : l'affiche se fait quand même, avec des initiales.

## Mode présentation (récit 48)

Avec le compte de l'admin principal, plus deux autres comptes qui n'ont pas encore ouvert l'appli. « Publier Firebase » doit être passé.

1. Ouvre **Administration**. **Attendu :** la carte « Mode présentation » est tout en haut, avec « 2 h », « 4 h » et « Jusqu'à ce que je l'éteigne ».
2. Touche « 2 h ». **Attendu :** la carte dit « Allumé jusqu'à » suivi de l'heure de fin, et le bandeau « Mode présentation jusqu'à » avec l'heure apparaît au-dessus des onglets. Il n'apparaît que sur ton téléphone.
3. Fais se connecter les deux autres comptes pour la première fois. **Attendu :** aucune notification « vient d'arriver » sur les téléphones de la famille.
4. Envoie un « Rejoignez-moi » depuis un autre compte. **Attendu :** il sonne tout de suite.
5. Touche « Éteindre », sur le bandeau ou dans la carte. **Attendu :** en moins d'une minute, chacun reçoit une seule notification « 2 personnes ont rejoint Familia », avec les deux prénoms. Le bandeau disparaît.
6. Rallume le mode pour 2 h et laisse passer l'heure de fin. **Attendu :** le bandeau disparaît de lui-même. Le résumé part au premier envoi qui suit l'heure de fin, ou au plus tard 30 minutes après.
7. Avec un compte qui n'est pas l'admin principal, et dans « Voir comme » : **Attendu :** ni la carte ni le bandeau.
8. Mode allumé, avec un autre compte : publie une bonne nouvelle, commente un événement et demande un coup de main. **Attendu :** aucun téléphone ne sonne. À l'extinction, chacun reçoit un résumé « Pendant la présentation » par sorte d'avis.
9. Mode allumé, avec le compte de l'admin principal : publie une bonne nouvelle avec une photo. **Attendu :** elle sonne tout de suite sur les téléphones de la famille.

## Le planning dans mon agenda (récit 49)

Avec un compte adulte, un compte enfant et un ordinateur. « Publier Firebase » doit être passé, et l'interrupteur « Planning dans mon agenda » allumé dans Administration.

1. Touche ta photo en haut à droite. **Attendu :** « Voir dans mon agenda » apparaît sous « Notifications ». Avec le compte enfant, et dans « Voir comme », il n'apparaît pas.
2. Touche-le, puis « Créer mon lien ». **Attendu :** le lien s'affiche, avec « Copier le lien », « M'envoyer le lien » et les étapes pour Google Agenda.
3. Touche « M'envoyer le lien » et envoie-le-toi par mail. Sur l'ordinateur, suis les étapes 2 à 4. **Attendu :** un agenda « Familia » apparaît avec les événements du planning, aux bonnes heures. Seulement ce qui te concerne (toute la famille, ton foyer, toi et tes enfants) : pas les rendez-vous d'un autre foyer. Aucune occasion de cadeau, aucune réservation.
4. Dans Familia, crée un événement. **Attendu :** il arrive dans Google Agenda dans les heures qui suivent (Google décide quand il relit).
5. Touche « Nouveau lien », puis confirme. **Attendu :** le lien change. Colle l'ancien dans la barre d'adresse de l'ordinateur : il répond « 404 ». L'agenda ajouté avec l'ancien lien ne se met plus à jour.

## On se prête dans Entraide (récit 51)
Avec l'interrupteur « On se prête » allumé (l'admin principal le voit toujours) :
1. Entraide : **Attendu :** un onglet « On se prête » après « Coups de main » (et « Bons plans » s'il est allumé). Interrupteur éteint, pour un autre membre : pas d'onglet « On se prête ».
2. Sans objet : **Attendu :** « Rien à se prêter pour l'instant ».
3. Avec des objets : **Attendu :** chaque carte dit la catégorie, le nom, puis « Prêté par … » (ou « À moi ») et « Disponible », « Demandé par … » ou « Chez … depuis le 12/12 ». Tape « racl » : seul l'appareil à raclette reste. « Disponible » ne garde que les objets libres.
4. « Ajouter un objet » (adulte) : nom, catégorie, note, photo facultative. Sans nom ni catégorie : **Attendu :** « Donne un nom à l'objet. » et « Choisis une catégorie. ». Enregistré : **Attendu :** la carte « À moi · Disponible ». Un enfant n'a pas le bouton.
5. Un autre adulte ouvre l'objet, touche « Je voudrais l'emprunter », choisit ou non des dates, puis « Demander ». **Attendu :** le propriétaire reçoit tout de suite « On voudrait t'emprunter quelque chose » ; la fiche dit « Tu l'as demandé. … va te répondre. » et propose « Retirer ma demande ».
6. Le propriétaire ouvre la fiche. **Attendu :** « … voudrait te l'emprunter. », avec « D'accord » et « Pas cette fois ». « D'accord » : l'autre reçoit « C'est d'accord » et la carte dit « Chez … depuis le … ». « Pas cette fois » : l'autre reçoit « Pas cette fois », et l'objet redevient disponible.
7. Prêté : le propriétaire ou l'emprunteur touche « Rendu ». **Attendu :** « Disponible », sans notification.
8. Un enfant ouvre un objet. **Attendu :** il voit où il est, sans aucun bouton.
9. Une date de retour dépassée de 3 jours : **Attendu :** vers 18 h, un seul « Petit rappel » à l'emprunteur, jamais un second.
10. Touche une notification d'emprunt (demande, réponse ou rappel). **Attendu :** la fiche de l'objet s'ouvre. Interrupteur éteint pour ce membre : rien ne s'ouvre.

## On y va ensemble (récit 52)

Avec trois comptes : Camille (adulte), Inès (adulte, responsable de Noé, enfant sans l'appli) et Sam. « Publier Firebase » doit être passé, et l'interrupteur « Covoiturage » allumé dans Administration.

1. Avec Camille, ouvre un événement de demain et réponds « Je viens ». **Attendu :** la rubrique « On y va ensemble » apparaît sous les réponses, avec « J'ai des places ».
2. Touche « J'ai des places », mets 2 places, « Annecy » et 10 h 30, puis « Proposer ». **Attendu :** la carte « Ma voiture » dit « 2 places libres sur 2 », « Départ : Annecy, 10 h 30 », avec « Itinéraire ».
3. Avec Inès, touche « Qui monte ? » sur la voiture de Camille, coche « Toi » et Noé, puis OK. **Attendu :** Camille reçoit tout de suite « Inès et Noé montent avec toi », avec « C'est complet ». Même la nuit, la notification sonne.
4. Avec Sam, touche « J'ai besoin d'une place ». **Attendu :** « Cherchent une place : Sam » s'affiche. La voiture est pleine, donc Sam n'a pas « Je monte ».
5. Avec Camille, touche la croix à côté de Noé. **Attendu :** Inès reçoit « Camille ne peut plus t'emmener ».
6. Avec Inès, réponds « Je ne viens pas ». **Attendu :** Inès disparaît de la voiture, et Camille reçoit « Inès ne monte plus avec toi ».
7. Le soir à 18 h, la veille. **Attendu :** le rappel de Camille dit « Tu emmènes… », et celui de chaque passager « Tu pars avec Camille ».
8. Avec Camille, touche « Retirer ma proposition », puis confirme. **Attendu :** les passagers reçoivent « Camille ne prend plus sa voiture ».

## Événements qui reviennent (récit 54)

Avec deux comptes : Camille (adulte) et Inès (adulte). « Publier Firebase » doit être passé, et l'interrupteur « Événements qui reviennent » allumé dans Administration.

1. Avec Camille, ajoute « Piscine » un mardi, puis touche « Se répète ». **Attendu :** les choix « Chaque semaine, le mardi », « Toutes les 2 semaines, le mardi », « Chaque mois, le 12 » (selon la date), « Chaque mois, le 2e mardi », « Chaque année, le 12 … ».
2. Choisis « Chaque semaine », puis « Se termine » : « Après un nombre de fois », 1. Touche « Enregistrer ». **Attendu :** le message rouge sur la fin. Mets 6, puis « Enregistrer ».
3. **Attendu :** le Planning montre 6 mardis « Piscine ». Inès reçoit une seule notification, qui finit par « Puis chaque semaine. ».
4. Avec Inès, réponds « Je viens » au deuxième mardi seulement. **Attendu :** les autres mardis n'ont pas sa réponse.
5. Avec Camille, ouvre le troisième mardi, change le lieu, « Enregistrer », puis « Cette fois seulement ». **Attendu :** seul ce mardi a le nouveau lieu.
6. Ouvre le quatrième mardi, change l'heure, « Enregistrer », puis « Celle-ci et les suivantes ». **Attendu :** les mardis 4 à 6 ont la nouvelle heure, le troisième garde son lieu à lui.
7. Ouvre le deuxième mardi, « Supprimer », puis « Cette fois seulement ». **Attendu :** il disparaît du Planning, et Inès, qui venait, reçoit qu'il est annulé.
8. Ouvre le cinquième mardi, « Supprimer », puis « Celle-ci et les suivantes ». **Attendu :** les mardis 5 et 6 disparaissent, les autres restent.
9. Dans « Voir dans mon agenda », mets à jour Google Agenda. **Attendu :** chaque mardi restant y est un événement à lui.
10. Éteins l'interrupteur. **Attendu :** « Se répète » ne s'affiche plus sur un nouvel événement, mais un mardi existant demande toujours « Cette fois seulement » ou « Celle-ci et les suivantes ».

## « Qui prévenir » rangé par foyer (règle du 17/11)

Avec deux comptes adultes de foyers différents, et un compte enfant.

1. Entraide, « Demander un coup de main ». **Attendu :** sous « Qui prévenir », le raccourci « Tout le monde », puis les adultes rangés par foyer, ton foyer d'abord. Chaque foyer a sa propre case.
2. Coche la case d'un foyer. **Attendu :** tous ses adultes sont cochés. Décoche un seul adulte : la case du foyer est à moitié cochée. Touche-la encore : tout le foyer est coché, puis décoché.
3. Avec le compte enfant, demande un coup de main. **Attendu :** son parent est marqué « Toujours prévenu pour un enfant » et reste coché, même si tu décoches son foyer.

## Mise à jour du 20 au soir (démo du 21)

But : passer le téléphone de l'admin principal de la version installée aujourd'hui à la version figée du 20 au soir, sans rien perdre. Fais-le d'abord sur le deuxième téléphone de test, puis sur le tien.

Ce que dit le code, vérifié le 17/11 :
- L'appli n'a aucune migration au démarrage. Les données sont dans Firebase, et le téléphone n'en garde qu'une copie pour le mode hors ligne.
- La seule migration côté serveur (la liste unique) ne part que quand l'interrupteur « Liste unique » change dans Administration. Une mise à jour de l'appli ne la déclenche pas, et « Publier Firebase » non plus.
- La nouvelle version a le même nom (`fr.familia.app`) et la même clé de signature : Android l'installe par-dessus et garde la connexion. L'APK de test (`fr.familia.app.test`) est une autre appli : il ne remplace rien.

Répétition sur le code, le 17/11 : de la version publiée le 17/11 à 15 h 35 (numéro 298) jusqu'à main.
- **Connexion :** gardée. Aucune clé de connexion ne change entre les deux.
- **Réglages des notifications :** ils sont dans Firebase, pas sur le téléphone. L'ancienne version n'écrivait « Calme la nuit » que quand le membre touchait ce bouton lui-même. Un membre qui n'y a jamais touché n'a donc rien d'écrit, et la nouvelle version l'allume pour lui. Celui qui l'avait éteint le garde éteint. Ce choix est lu de la même façon par l'appli, par les notifications et par les rappels (#308, #312).
- **Cache des photos et liens :** gardés. La nouvelle version note pour la première fois à qui appartiennent ces traces (#306). Au premier lancement, rien n'est encore noté, donc elle ne vide rien. Elle videra seulement si un autre membre se connecte ensuite sur ce téléphone.
- **Copie hors ligne :** gardée, car c'est le même nom d'appli.
- **Brouillons :** un formulaire laissé à moitié rempli (événement, membre, envie…) est perdu à la mise à jour, comme quand Android ferme l'appli. Termine ou annule tes formulaires avant de mettre à jour.
- **« Quoi de neuf ? » :** les 4 cartes (la question de la semaine, Qui est-ce ?, les recettes, On se prête) sont parties dans la version du 17/11. Après la mise à jour du 20, la feuille ne montre que les cartes pas encore vues sur ce téléphone. Si tu as déjà touché « C'est parti » le 17, elle ne s'ouvrira pas, et c'est normal. Les membres qui installent l'appli le 21 voient le tuto, pas la feuille. Pour revoir les cartes : menu de l'avatar, « Quoi de neuf ? ».

Avant de commencer :
1. Note la version installée : onglet **Famille**, tout en bas.
2. Note trois repères : le nombre d'événements de la semaine dans Planning, une liste d'envies avec ses lignes, et la dernière bonne nouvelle.
3. Dans **Administration**, note quels modules sont allumés.

La mise à jour :
4. Lance « APK de publication » (voir [mise-a-jour.md](mise-a-jour.md)), puis « Publier Firebase » s'il y a eu des fusions qui touchent Firebase.
5. Ferme Familia, puis rouvre-la. **Attendu :** l'encadré « Nouvelle version disponible ». S'il n'apparaît pas, onglet **Famille**, « Vérifier les mises à jour ».
6. Touche « Mettre à jour », puis « Mettre à jour » dans la fenêtre d'Android. **Attendu :** la barre d'avancement, puis l'installation. Aucune demande de connexion Google après l'installation.
7. Rouvre Familia. **Attendu :** tu arrives directement sur l'Accueil, connecté, sans écran de connexion.
8. Onglet **Famille**, tout en bas. **Attendu :** le nouveau numéro de version.
9. Vérifie tes trois repères. **Attendu :** les mêmes événements, les mêmes lignes, la même bonne nouvelle.
10. **Administration**. **Attendu :** les mêmes modules allumés qu'à l'étape 3. Aucun ne s'est allumé tout seul.
11. **Attendu :** si tu n'as pas encore vu les cartes, la feuille « Quoi de neuf ? » s'ouvre une fois (4 cartes au plus). Touche « C'est parti ». Ferme et rouvre l'appli : elle ne revient pas.
12. Sur le deuxième téléphone, encore en ancienne version, ouvre Familia. **Attendu :** il voit les mêmes données et peut toujours répondre à un événement. Puis mets-le à jour à son tour.
13. Coupe le réseau (mode avion), rouvre Familia. **Attendu :** le bandeau « Hors ligne » et les données, sans écran d'erreur.

Si quelque chose ne va pas à l'étape 7 (écran de connexion) ou 9 (données absentes), ne désinstalle pas l'appli : note la version, fais une capture et préviens [DEV]. Désinstaller effacerait la copie hors ligne, mais pas les données, qui restent dans Firebase.

## Notre année en famille (récit 55)

Avec l'interrupteur « Notre année » allumé dans Administration. Avant le 26/12, seul l'admin principal voit le récit : il montre l'année en cours jusqu'à aujourd'hui (date simulée). Un autre membre ne voit rien avant le 26/12.

1. Bonnes nouvelles, avec le compte de l'admin principal. **Attendu :** en haut du fil, « Revoir notre année 2026 ». Avec un autre compte, avant le 26/12 : pas de bouton.
2. Touche-le. **Attendu :** un récit plein écran, avec des traits en haut pour les pages et « Page 1 sur … ». La première page dit « Notre année 2026 ».
3. Touche la droite de l'écran. **Attendu :** la page suivante. La gauche revient en arrière, et le bouton retour d'Android aussi.
4. Les pages, dans l'ordre et seulement s'il y a de quoi les remplir : les chiffres de la famille (bonnes nouvelles, moments vécus ensemble, coups de main rendus), une photo par mois au plus, en variant les foyers (jamais choisie au nombre de réactions), le voyage le plus long déjà fini, les enfants nés dans l'année, une question de la semaine avec trois réponses, puis « Merci à toute la famille ». **Attendu :** aucun prénom n'est classé ni compté, et rien ne parle de cadeaux, de réservations ni de capsules.
5. Masque pour toi une bonne nouvelle avec photo, très aimée, puis rouvre le récit. **Attendu :** sa photo n'y est plus.
6. Dernière page, « Fermer ». **Attendu :** retour à Bonnes nouvelles.
7. Interrupteur éteint. **Attendu :** pas de bouton « Revoir », même pour l'admin principal.

## Une photo dès la création d'une recette

1. Onglet Famille > Recettes > « Ajouter une recette ». Remplis le titre, les ingrédients et les étapes.
2. Plus bas, sous « Photos du plat », touche « Ajouter une photo du plat ». Le sélecteur de photos d'Android s'ouvre, sans demander de permission. Choisis 2 photos.
3. Les 2 photos s'affichent en petit, chacune avec « Retirer la photo ». Le bouton dit maintenant « Ajouter des photos (encore 1) ». Après une 3e photo, il disparaît.
4. Touche « Enregistrer ». Le bouton affiche « Envoi de la photo 1 sur 2… », puis la recette s'ouvre avec ses photos dans « Photos du plat ».
5. Sans réseau : la recette s'enregistre quand même. Les photos ne partent pas ; tu peux les ajouter plus tard depuis la recette.
6. Ouvre une recette existante et touche « Modifier » : pas de champ photo dans le formulaire, les photos se gèrent sur la recette elle-même.

## « Notre année en famille » sur l'Accueil

Avec l'interrupteur « Notre année en famille » allumé.

1. Entre le 26/12 et le 10/01, ouvre l'Accueil. **Attendu :** la puce « Notre année 2026 » en tête, avant « Anniversaires ». Avec la nouvelle présentation, elle est en fin de liste, juste au-dessus de la barre « Tout Familia ».
2. Touche-la. **Attendu :** l'écran de l'année s'ouvre.
3. Hors de ces dates, avec n'importe quel compte, l'admin principal compris : **Attendu :** pas de puce sur l'Accueil (PRD rév. 259).
4. « Tout Familia », famille « Se souvenir ». **Attendu :** « Notre année ». L'admin principal l'y trouve toute l'année, pour l'essayer. Les autres l'y trouvent à partir du 26/12.
5. Interrupteur éteint, avec un autre compte : **Attendu :** ni puce, ni entrée dans le tiroir.

## Bonnes nouvelles : envoi de la photo sur un réseau faible ou coupé

1. Publier une bonne nouvelle avec 2 photos, réseau normal. Une barre se remplit au-dessus du bouton « Envoi de la photo 1 sur 2… », puis la bonne nouvelle apparaît une seule fois dans le fil.
2. Recommencer en mode avion avant de toucher « Publier ». La barre reste en attente et la phrase « Plus de réseau : l'envoi reprendra tout seul dès son retour » s'affiche. Couper le mode avion sans toucher l'écran : l'envoi repart seul et la bonne nouvelle arrive une seule fois.
3. Recommencer et passer en mode avion pendant l'envoi de la photo 2. La phrase apparaît, puis l'envoi reprend tout seul au retour du réseau. Vérifier dans le fil : une seule bonne nouvelle, avec ses 2 photos.
4. Pendant un envoi, toucher « Annuler l'envoi » : le formulaire revient avec le texte et les photos, rien n'est publié. Toucher « Publier » : la bonne nouvelle part une seule fois.
5. Si l'envoi échoue avec le réseau (rare), le message dit que le texte et les photos sont gardés. Toucher « Publier » de nouveau : une seule bonne nouvelle au bout.

## Photos orphelines des bonnes nouvelles (lecture seule)

Le rapport part chaque dimanche vers 4 h, avec la tâche `arrivalReminder`. Il ne supprime rien.
1. Console Firebase, **Functions**, `arrivalReminder`, **Journaux**, et chercher « orphanPhotos ».
2. La ligne « orphanPhotos (lecture seule) : N photo(s) orpheline(s) sur M, X Ko, rien n'est supprimé » donne le bilan. Les lignes « serait supprimée … » donnent les chemins, 50 au plus.
3. Attendu : quelques photos au plus, celles d'un envoi annulé. Une photo d'une bonne nouvelle visible dans l'appli ne doit jamais y figurer. Si c'est le cas, le signaler.

## Me le rappeler (récit 56)

Avec deux comptes : Camille (adulte) et Inès (adulte). « Publier Firebase » doit être passé, et l'interrupteur « Mes rappels » allumé dans Administration (l'admin principal le voit toujours).

1. Avec Camille, ouvre un événement à 15 h, demain. **Attendu :** sous les réponses, la ligne « Me le rappeler », « Aucun rappel ».
2. Touche-la. **Attendu :** « C'est privé : toi seul le vois. », puis « 1 h avant », « 3 h avant », « Le matin à 8 h », « 2 jours avant », « 1 semaine avant ». Coche « 1 h avant » et « Le matin à 8 h », puis « Enregistrer ». **Attendu :** la ligne dit « 1 h avant, le matin à 8 h ».
3. Avec Inès, ouvre le même événement. **Attendu :** « Aucun rappel » : elle ne voit pas les rappels de Camille.
4. Le lendemain, à 8 h puis vers 14 h. **Attendu :** Camille reçoit « Aujourd'hui : … », puis « Dans 1 h : … », à 30 minutes près. Toucher la notification ouvre l'événement.
5. Un événement « Journée » : **Attendu :** ni « 1 h avant » ni « 3 h avant ».
6. Un événement qui revient : coche « 2 jours avant », choisis « Toutes les fois », puis « Enregistrer ». **Attendu :** les autres fois disent « Toutes les fois : 2 jours avant ». Sur l'une d'elles, décoche tout avec « Cette fois seulement » : elle dit « Aucun rappel », les autres gardent le leur.
7. Change l'heure de l'événement de l'étape 2 à 17 h. **Attendu :** le rappel arrive vers 16 h, pas vers 14 h. Supprime l'événement : **Attendu :** aucun rappel.
8. Avec Inès, Notifications, « Rappels quand je viens » : coche « 1 h avant ». Réponds « Je viens » à un événement. **Attendu :** sa ligne dit « Par défaut : 1 h avant », et le rappel arrive. Réponds « Peut-être » : plus de rappel.
9. Dans « Voir comme » : **Attendu :** pas de ligne « Me le rappeler », ni de « Rappels quand je viens ».
10. Interrupteur éteint, avec un compte qui n'est pas l'admin principal : **Attendu :** ni la ligne ni la section, et aucun rappel n'arrive.

## Démo du 21 : album, diaporama, recettes, arbre et Qui est-ce ? sur un réseau faible

À faire sur un téléphone qui a déjà ouvert ces écrans une fois avec un bon réseau, puis sur un téléphone qui ne les a jamais ouverts. Pour un réseau bridé : réglages du téléphone, Réseau mobile, Type de réseau préféré « 2G seulement » (ou s'éloigner de la box), Wi-Fi coupé.
1. Réseau bridé, ouvre l'album d'un voyage puis « Voir les photos ». **Attendu :** les miniatures déjà vues s'affichent tout de suite ; les autres montrent le fond argile, puis arrivent une à une. Jamais de case blanche ni de plantage.
2. Touche une photo. **Attendu :** une petite roue tourne au milieu tant que la photo arrive, puis la photo s'affiche. Glisse vers la suivante : elle est souvent déjà là.
3. Lance le diaporama. **Attendu :** chaque photo reste 4 secondes ; une photo qui ne vient pas montre la roue, et une photo qui ne peut pas charger montre le dessin d'une image puis laisse la place à la suivante au bout d'environ 1,5 seconde. Le diaporama ne se fige jamais.
4. Passe en mode avion, diaporama ouvert. **Attendu :** les photos déjà vues continuent de défiler ; les autres montrent le dessin et sont sautées. Coupe le mode avion sans toucher l'écran : au tour suivant, les photos sautées s'affichent.
5. Mode avion, ouvre Recettes, une recette et ses photos. **Attendu :** la liste et la recette déjà vues s'affichent, le bandeau « Hors ligne » est en haut, les photos déjà vues sont là, les autres montrent le dessin. Au retour du réseau, elles arrivent seules.
6. Mode avion, ouvre l'arbre généalogique. **Attendu :** l'arbre déjà vu s'affiche, avec les photos de profil ou les initiales. Jamais d'écran vide qui tourne sans fin.
7. Mode avion, ouvre « Qui est-ce ? » sur l'Accueil puis la page de la photo. **Attendu :** la roue tourne sur la photo si elle n'a jamais été vue, puis le dessin ; le reste de la carte s'affiche. Une réponse envoyée hors ligne affiche un message d'échec, et ce qui est tapé reste dans les cases.
8. Remets le réseau normal et rouvre chaque écran. **Attendu :** tout est revenu, sans avoir fermé l'appli.

## À qui le tour ? dans Entraide (récit 58)

Avec l'interrupteur « À qui le tour ? » allumé (l'admin principal le voit toujours). Il faut deux téléphones de deux foyers différents.
1. Entraide : **Attendu :** un onglet « À qui le tour ? » après les autres. Avec 4 onglets, la rangée défile de côté. Interrupteur éteint, pour un autre membre : pas d'onglet.
2. Sans tour : **Attendu :** « Aucun tour pour l'instant », et un bouton « Créer un tour » pour un adulte. Un enfant n'a pas le bouton.
3. « Créer un tour » : écris « Recevoir à Noël », coche deux foyers, change l'ordre avec les flèches. Sans titre ou avec un seul foyer : **Attendu :** « Écris pour quoi c'est le tour. » ou « Il faut au moins deux foyers. ». Les foyers sont rangés par nom.
4. Enregistré : **Attendu :** la carte dit « C'est à ton foyer » (avec « À toi ») ou « C'est à … ». Les adultes du premier foyer reçoivent « C'est ton tour », sauf toi si tu es dans ce foyer.
5. Ouvre le tour depuis le foyer dont c'est le tour. **Attendu :** « Ensuite : … », l'ordre, « Déjà fait : Rien encore. », et les boutons « Créer l'événement », « C'est fait », « On passe notre tour ».
6. « Créer l'événement » : **Attendu :** le formulaire d'un nouvel événement du Planning, avec le titre déjà écrit. Reviens sans rien changer : il ne demande pas d'abandonner.
7. « C'est fait », écris « Noël 2026 », valide. **Attendu :** « Noël 2026 : … » dans l'historique, et c'est au foyer suivant, qui reçoit « C'est ton tour ».
8. « On passe notre tour » puis confirme. **Attendu :** c'est au foyer d'après, et ton foyer prend la place suivante dans l'ordre.
9. Depuis un foyer, « Proposer un échange », choisis l'autre foyer, puis « Envoyer la proposition ». **Attendu :** l'autre foyer reçoit la notification, et voit « Accepter » et « Refuser ». Toi, tu vois « on attend sa réponse » et « Retirer la proposition ». « Accepter » : les deux places sont échangées. « Refuser » : tu reçois la réponse, rien ne change.
10. Un enfant ouvre le tour. **Attendu :** il voit l'ordre et l'historique, sans aucun bouton.
11. Touche une notification « C'est ton tour ». **Attendu :** le tour s'ouvre. Interrupteur éteint pour ce membre : rien ne s'ouvre.
12. L'auteur ou l'admin principal : le crayon change le titre ou l'ordre, la corbeille supprime le tour après confirmation. **Attendu :** retour à la liste, le tour a disparu chez tout le monde.

## Démo du 21 : Bonnes nouvelles et Entraide sur un réseau faible

Passe chaque étape deux fois : d'abord en mode avion, puis en 2G (réglages du téléphone, Réseau mobile, Type de réseau préféré « 2G seulement », Wi-Fi coupé). Ouvre d'abord une fois le fil et Entraide avec un bon réseau. Il faut deux téléphones de deux foyers pour l'étape 6.
1. Ouvre les Bonnes nouvelles. **Attendu :** le fil déjà vu s'affiche tout de suite, avec le bandeau « Hors ligne » en mode avion. Jamais d'écran vide ni de plantage. Sur un téléphone qui n'a jamais ouvert le fil : la roue, puis « Pas de réseau pour l'instant… » en mode avion, ou « Le réseau est lent, ça arrive… » en 2G après quelques secondes, mais jamais « Pas encore de bonne nouvelle ».
2. Publie une bonne nouvelle sans photo. **Attendu :** l'écran se ferme et elle est en haut du fil avec « En attente du réseau ». Remets le réseau sans toucher l'écran : la mention disparaît, et l'autre téléphone la reçoit une seule fois. Avec une photo, l'écran reste ouvert et la phrase « Plus de réseau : l'envoi reprendra tout seul… » s'affiche (voir la section sur l'envoi de la photo).
3. Ouvre une bonne nouvelle déjà vue et ajoute une réaction. **Attendu :** la fiche s'affiche, la réaction aussi, et elle part au retour du réseau.
4. Ouvre Entraide, puis chaque onglet (Coups de main, Bons plans, Prêts, À qui le tour ?). **Attendu :** ce qui est déjà vu s'affiche. Sinon, la roue avec la phrase qui dit pourquoi. Jamais d'écran blanc.
5. Sur une demande où on a besoin de toi, touche « Je m'en occupe ». **Attendu :** pas de message d'erreur, la demande passe dans « Déjà pris en charge » avec « En attente du réseau ». Remets le réseau : la mention disparaît, et l'auteur de la demande est prévenu une seule fois.
6. Deux téléphones, une même demande ouverte. Le téléphone A en mode avion touche « Je m'en occupe ». Le téléphone B, avec le réseau, touche « Je m'en occupe » juste après. Remets le réseau sur A. **Attendu :** B l'a. Sur A, la demande montre que B s'en occupe, sans doublon, et un message en bas dit « … s'en occupait déjà. Merci d'avoir proposé ton aide ! », sans erreur rouge.
7. Sur « À qui le tour ? », touche « C'est fait » sur un tour où c'est à ton foyer. **Attendu :** le tour passe au foyer suivant avec « En attente du réseau », et les boutons restent utilisables. Au retour du réseau, la mention disparaît, et le foyer suivant reçoit « C'est ton tour » une seule fois.
8. Remets le réseau normal et rouvre chaque écran. **Attendu :** tout est à jour, sans avoir fermé l'appli.

## Mes premiers pas (récit 61)

Avec l'interrupteur « Mes premiers pas » allumé pour la famille. Éteint, personne ne voit la carte, pas même l'admin principal. Prends un membre qui n'a ni photo, ni « Goûts et passions », ni réaction.
1. Ouvre l'Accueil. **Attendu :** la carte « Mes premiers pas » juste après la carte du jour, avec trois gestes à faire. Pas de nombre, pas de pourcentage. Avec « Goûts et passions » éteint : deux gestes.
2. Touche « Mettre ma photo », choisis et cadre une photo. **Attendu :** la photo est enregistrée, et la ligne se coche toute seule.
3. Touche « Remplir « Goûts et passions » », écris un goût sur ta fiche, reviens. **Attendu :** la ligne est cochée.
4. Touche « Réagir à une bonne nouvelle », mets un cœur sur une bonne nouvelle, reviens. **Attendu :** la carte dit « Bravo, c'est parti ! ». Le lendemain, ou après « Fermer », elle a disparu.
5. Sur un autre membre, touche « Plus tard ». **Attendu :** la carte disparaît pour la journée, et revient le lendemain. « Ne plus afficher » : elle ne revient plus sur ce téléphone.
6. Allume le Mode présentation. **Attendu :** pas de carte. Éteins-le : elle revient. En « Voir comme » : jamais de carte.
7. Aucune notification n'arrive pour ces gestes. Au bout de 14 jours après sa première apparition sur le téléphone, la carte ne s'affiche plus.
## Démo du 21 : avalanche de notifications

Il faut trois téléphones de test (A, B, C) et le module « La bienvenue » allumé. Mode présentation éteint, sauf à l'étape 5. Les heures sont celles de la tâche des 30 minutes (à l'heure pile et à la demie, à une ou deux minutes près).
1. Connecte A pour la première fois. **Attendu :** B et C reçoivent tout de suite « … vient d'arriver ».
2. Dans les 30 minutes, connecte B, puis C pour la première fois. **Attendu :** rien n'arrive tout de suite.
3. Attends le passage suivant de la tâche, au moins 30 minutes après l'étape 1. **Attendu :** A reçoit une seule notification « B et C viennent d'arriver ». B ne reçoit que « C vient d'arriver », et C que « B vient d'arriver ».
4. Depuis B, publie cinq bonnes nouvelles en quelques minutes. **Attendu :** A reçoit les trois premières, puis plus rien. Une heure environ après la première, A reçoit une seule notification « Dans Familia », « 2 notifications : … ». Pendant ce temps, un « Rejoins-moi » de B arrive tout de suite.
5. Allume le Mode présentation. Connecte un nouveau membre, publie deux bonnes nouvelles et réagis à une autre depuis B. Éteins le mode. **Attendu :** pendant le mode, rien ne sonne sur A. À la fin, A reçoit au plus trois notifications : « … personnes ont rejoint Familia », une seule « Pendant la présentation » pour tout le reste, et à part une notification Cadeaux s'il y en avait.

## Écrans vides et profils enfant

Sur une famille où rien n'a encore été publié, ou en « Voir comme » sur un membre. Le téléphone a le réseau.
1. Ouvre Entraide, puis Coups de main, Bons plans, Prêts et À qui le tour ?, puis les Bonnes nouvelles. **Attendu :** chaque écran vide a une phrase et un seul bouton (« Demander le premier », « Partager le premier », « Ajouter un objet », « Créer le premier tour », « Partager la première »). Le bouton flottant n'apparaît pas en plus. Le toucher ouvre le formulaire. Défis et Question de la semaine, avant leur date : « Partager une bonne nouvelle en attendant », qui ouvre le formulaire d'une bonne nouvelle.
2. Publie une première chose sur chacun. **Attendu :** la liste remplace le message, et le bouton flottant revient.
3. Recommence en « Voir comme » sur un enfant de 11 ans, puis de 16 ans. **Attendu :** Coups de main et Bonnes nouvelles gardent leur bouton, car un enfant peut demander et publier. Bons plans, Prêts et À qui le tour ? disent que ce sont les adultes qui les remplissent, sans aucun bouton. Aucun bouton ne finit sur un refus.

## Démo du 21 : première arrivée, Accueil, Planning et événement sur un réseau faible

À faire avec le deuxième téléphone de test, une fois en mode avion, puis une fois en « 2G seulement » (réglages du téléphone, Réseau mobile, Type de réseau préféré), Wi-Fi coupé.

**Connexion (appli fraîchement installée, ou après « Utiliser un autre compte »)**
1. Mode avion, touche « Se connecter avec Google ». **Attendu :** le message « Pas de réseau, ou il est trop lent… » et le bouton devient « Réessayer ». Jamais « Aucun compte Google sur ce téléphone ».
2. 2G, touche « Se connecter avec Google », choisis le compte, puis passe en mode avion pendant que le bouton tourne. **Attendu :** au plus une vingtaine de secondes après, le même message et « Réessayer ». Jamais d'écran blanc ni de roue sans fin.
3. Coupe le mode avion et touche « Réessayer ». **Attendu :** l'appli s'ouvre, sans redemander le compte si Google l'avait déjà accepté.
4. Ferme l'appli pendant que le bouton tourne (glisse-la hors des applis récentes), puis rouvre-la avec le réseau. **Attendu :** elle reprend toute seule et s'ouvre sur l'Accueil.

**Accueil, Planning et événement (sur un téléphone déjà connecté une fois avec le réseau)**
5. Mode avion, ouvre l'appli. **Attendu :** le bandeau « Hors ligne » en haut, l'Accueil et le Planning déjà vus s'affichent. La vérification de version ne bloque rien : l'appli s'ouvre normalement.
6. Mode avion, ouvre un événement et touche « Je viens ». **Attendu :** « Je viens » est coché tout de suite, avec sous la ligne « En attente du réseau : la réponse partira toute seule à son retour. ». Aucun message d'erreur.
7. Coupe le mode avion, sans toucher l'écran. **Attendu :** la ligne « En attente » disparaît en quelques secondes. Sur ton téléphone, la réponse apparaît une seule fois dans « Qui vient ».

**Mode présentation et « Voir comme » (sur ton téléphone)**
8. Avec le réseau, allume le Mode présentation, puis passe en mode avion. **Attendu :** le bandeau « Mode présentation jusqu'à … » reste affiché.
9. Mode avion, **Administration**, « Voir comme… », choisis un membre. **Attendu :** son Accueil et son Planning déjà vus s'affichent, avec le bandeau « Tu vois l'appli comme … ». « Quitter » marche aussi sans réseau.
10. Remets le réseau et éteins le Mode présentation. **Attendu :** le résumé arrive comme d'habitude.

## Démo du 21 : écrans vides et profils enfant (voyages, albums, recettes, capsule, arbre, Qui est-ce ?, diaporama)
Le soir du 21, la famille ouvre ces écrans encore vides. Aucun ne doit rester blanc ni dire seulement « Aucun élément ».
1. Sur un compte sans voyage, ouvre Voyages. **Attendu :** « Le prochain départ s'écrit ici » et le bouton « Nouveau voyage ».
2. Ouvre les albums sans aucune photo. **Attendu :** « Les premiers souvenirs arrivent bientôt » et le lien « Voir les voyages », qui ouvre Voyages.
3. Ouvre le diaporama d'un mois sans photo. **Attendu :** une phrase et le lien « Revenir en arrière ».
4. Ouvre le carnet de recettes vide. **Attendu :** « Le carnet attend sa première recette » et le bouton « Nouvelle recette ». Fais de même pour Capsule (« Écris à la famille de demain », bouton « Nouvelle capsule ») et pour Qui est-ce ? (« Une vieille photo à deviner ? », bouton d'ajout).
5. Ouvre l'arbre vide. Avec un chef de famille, **attendu :** le bouton d'ajout. Avec un autre membre, **attendu :** le lien « Revenir en arrière ».
6. Administration › Voir comme › un enfant (sans compte admin ni chef de famille). Parcours les mêmes écrans. **Attendu :**
   - Voyages vide : pas de « Nouveau voyage », mais « Quand un adulte t'emmène en voyage… » et le lien « Voir les albums photo ».
   - Dans un voyage où l'enfant est participant : pas de « Modifier » ni de « Supprimer » du voyage. « Bien arrivé », les étapes, « À prévoir » et ses photos restent proposés.
   - Dans un voyage qu'il suit seulement : aucun bouton d'ajout de photo ni de ligne.
   - Arbre : aucun bouton pour ajouter une personne ou un lien.
   - Recettes et Capsule : le bouton d'ajout est là, puisqu'un enfant peut écrire les siens. Qui est-ce ? n'en montre pas en Voir comme, c'est voulu.
   Un bouton visible qui finirait sur un refus est un défaut : note l'écran et le bouton.
7. Toucher un bouton en Voir comme donne « Lecture seule en mode Voir comme ». C'est normal : l'essai vérifie seulement quels boutons s'affichent.

## Démo du 21 : double toucher, retour, rotation et retour dans l'appli

Sur ton téléphone, avec la rotation automatique allumée (volet des réglages rapides).

**Double toucher**
1. Planning, touche deux fois très vite un événement. **Attendu :** il s'ouvre une seule fois : un seul « Retour » ramène au Planning.
2. Fil des Bonnes nouvelles, touche deux fois très vite « Publier ». **Attendu :** un seul formulaire. Ferme-le : tu es sur le fil, pas sur un deuxième formulaire.
3. Juste après avoir changé d'onglet, touche tout de suite un bouton. **Attendu :** pendant moins d'une seconde, le toucher peut ne rien faire. Ensuite tout répond normalement.

**« Voir comme »**
4. **Administration**, « Voir comme… », choisis un membre. Ouvre son Planning, puis tourne le téléphone à l'horizontale, puis à la verticale. **Attendu :** le bandeau « Tu vois l'appli comme … » ne disparaît jamais. Un rond peut tourner un instant sous le bandeau. Ton propre Accueil ne s'affiche à aucun moment.
5. Toujours en « Voir comme », touche « Accueil » du téléphone, attends une minute, puis rouvre Familia. **Attendu :** le même membre, avec son bandeau, jamais ton propre Accueil.
6. Toujours en « Voir comme », sur son Accueil, touche « Retour » du téléphone. **Attendu :** l'appli ne se ferme pas, et le message « Pour sortir de « Voir comme », touche « Quitter » sur le bandeau. » s'affiche. Dans un écran ouvert (un événement), « Retour » revient en arrière comme d'habitude.
7. Touche « Quitter » sur le bandeau. **Attendu :** ton propre appli revient, et « Retour » sur ton Accueil ferme Familia comme avant.

## Téléphone partagé : un autre membre se connecte (note C de #264)
1. Sur un téléphone, connecte-toi avec le compte A, puis ouvre un album et un livre de recettes pour remplir le cache.
2. Touche « Utiliser un autre compte » (par exemple depuis l'écran « Cette adresse n'est pas dans la famille ») et connecte-toi avec le compte B. **Attendu :** les photos se rechargent depuis le réseau, puis s'affichent normalement. Le dernier PDF de A n'est plus proposé.
3. Reconnecte-toi avec B. **Attendu :** les photos s'affichent tout de suite, sans nouveau chargement, puisque c'est le même membre.
4. Toujours connecté avec B, ferme Familia depuis les applis récentes, coupe le Wi-Fi et les données, puis rouvre-la. **Attendu :** aucun document de A ne s'affiche (ses voyages ou ses listes que B ne voit pas). L'appli s'ouvre comme sans réseau. Remets le réseau : tout ce que B voit revient.
5. Avec B, ouvre un très gros album (plusieurs centaines de photos) et touche « Faire le livre photo ». **Attendu :** le livre se fait. Dans les réglages d'Android, le stockage de Familia (cache) ne dépasse pas environ 150 Mo pendant que l'écran est ouvert, et redescend quand tu le quittes.

## Démo du 21 : arrivée d'un nouveau membre, de l'invitation à l'Accueil

Avec le deuxième téléphone de test, sous Android 13 ou plus, et Familia désinstallée.

1. Sur ton téléphone, **Famille**, une fiche, « Envoyer l'invitation », par SMS vers le deuxième téléphone. **Attendu :** le message dit en trois étapes quoi faire : ouvrir le lien et toucher le fichier, « Télécharger » puis « Installer » (avec quoi faire si c'est bloqué), puis se connecter avec l'adresse Google notée.
2. Sur le deuxième téléphone, suis le message. Si Android dit que l'installation est bloquée, touche « Paramètres », autorise la source, puis reviens. **Attendu :** l'installation se termine.
3. Ouvre Familia et connecte-toi. Va jusqu'au dernier écran du tuto, « Rester prévenu », et touche « Autoriser ». Dans la fenêtre d'Android, touche « Ne pas autoriser ». **Attendu :** l'appli s'ouvre sur l'Accueil. Tu ne restes pas bloqué sur le tuto.
4. Ta photo en haut à droite, puis les réglages des notifications. **Attendu :** le message « Les notifications de Familia sont bloquées sur ce téléphone… », avec un bouton vers les réglages d'Android.
5. Recommence sur un téléphone où Familia vient d'être réinstallée, et touche « Autoriser » dans la fenêtre d'Android. **Attendu :** l'appli s'ouvre aussi sur l'Accueil.

## Démo du 21 : la photo du repas chez ceux qui la reçoivent

Avec ton téléphone et deux téléphones de test, A et B, déjà connectés. Sur B, coupe les notifications de Familia dans les réglages d'Android.

1. Sur ton téléphone, allume le Mode présentation (« 4 h »), puis publie une bonne nouvelle avec une photo, depuis ton propre compte. **Attendu :** A sonne tout de suite. Touche la notification sur A, l'appli fermée : la bonne nouvelle s'ouvre, avec la photo.
2. Sur B, rien ne sonne. Ouvre Familia. **Attendu :** la photo est sur l'Accueil, dans « Bonnes nouvelles ».
3. Sur un troisième téléphone, ou sur B après « Utiliser un autre compte », connecte un membre qui ne s'était jamais connecté, puis finis le tuto. **Attendu :** la photo est sur son Accueil, sans notification.
4. Mets A en économie d'énergie, écran éteint, et publie une deuxième bonne nouvelle. **Attendu :** A sonne dans la minute.
5. Sur A, réagis à la photo. Puis, sur ton téléphone, éteins le Mode présentation. **Attendu :** sur A, le résumé ne parle jamais de ta photo : elle a déjà sonné, elle ne compte pas deux fois.


## Démo du 21 : la nuit et le réveil du 22 (rév. 266)

Avec le deuxième téléphone de test, sur un compte qui n'a jamais ouvert ses réglages de notifications.

1. Ta photo en haut à droite, puis les réglages des notifications. **Attendu :** « Calme la nuit » est déjà allumé.
2. Après 22 h, depuis ton téléphone, publie une bonne nouvelle et réagis à une autre. **Attendu :** le deuxième téléphone ne sonne pas.
3. Le lendemain, entre 8 h et 8 h 30. **Attendu :** une seule notification « Cette nuit dans Familia » avec les titres de la nuit. Une surprise (cadeaux, capsule) arriverait à part.
4. Sur un compte où tu as éteint « Calme la nuit » toi-même, refais l'étape 2. **Attendu :** il sonne tout de suite, comme avant.
5. Une capsule prévue pour aujourd'hui. **Attendu :** elle s'ouvre et sonne à partir de 8 h, jamais en pleine nuit.

## Démo du 21 : l'appli arrêtée par Android pendant une saisie

Pendant le repas, on passe d'une appli à l'autre (appareil photo, WhatsApp), et Android peut arrêter Familia entre-temps. Pour le reproduire à coup sûr :

Avant de commencer :
1. Ouvre **Paramètres** > **À propos du téléphone**. Touche 7 fois **Numéro de build** pour activer les options pour les développeurs.
2. Dans **Paramètres** > **Système** > **Options pour les développeurs**, allume **Ne pas conserver les activités**.

Les étapes :
3. **Créer un événement.** Planning, puis +. Tape un titre, une date, un lieu et une note. Appuie sur le bouton d'accueil d'Android, ouvre l'appareil photo, puis reviens dans Familia. **Attendu :** le formulaire est là, avec tout ce que tu as tapé.
4. **Modifier un événement.** Fais la même chose depuis « Modifier » sur un événement existant. **Attendu :** pareil.
5. **Sondage.** Ouvre un nouveau sondage. Tape une question, deux dates (dont une avec une heure), et décoche un foyer. Passe par l'appareil photo, puis reviens. **Attendu :** la question, les dates, l'heure et les cases cochées sont là.
6. **Fiche membre** (Famille > un membre > Modifier, ou Ajouter). Change le prénom et la date de naissance, puis passe par WhatsApp et reviens. **Attendu :** tes changements sont là.
7. **Administration**, « Ajouter un foyer ». Tape un nom, passe par l'appareil photo et reviens. **Attendu :** la fenêtre est ouverte, avec le nom tapé.
8. **Dire où je suis.** Touche « Ici » et attends l'adresse. Coche un membre et écris une note, puis passe par l'appareil photo et reviens. **Attendu :** l'adresse, la note et le membre coché sont là, et « Envoyer » est actif.
9. **Pas de double envoi.** Coupe le réseau (mode avion). Dans un sondage, touche « Envoyer », puis quitte tout de suite vers l'appareil photo et reviens. **Attendu :** l'écran du sondage est fermé, et pas rouvert avec le formulaire. Rallume le réseau. **Attendu :** le sondage apparaît une seule fois. Fais la même chose avec « Dire où je suis » et avec un nouvel événement.
10. **Pas d'écran vide.** À chaque retour, l'écran montre le formulaire, ou bien il est déjà fermé. Il ne reste jamais blanc ni sur une roue qui tourne sans fin.

À la fin, éteins **Ne pas conserver les activités**.

Une limite connue : la position « Ici » revient telle qu'elle était au départ. Si tu as bougé entre-temps, touche à nouveau « Ici ».

## L'appli arrêtée par Android pendant l'appareil photo ou la galerie

Sur un téléphone de test, une seule fois : Paramètres > À propos du téléphone, touche 7 fois « Numéro de build ». Puis Paramètres > Système > Options pour les développeurs, et allume « Ne pas conserver les activités ». Android arrête alors Familia dès qu'elle passe en arrière-plan, comme il le fait sur un téléphone qui manque de mémoire. À la fin, éteins cette option.

1. Voyages, « Nouveau voyage » : écris un nom, choisis les dates et deux personnes. Appuie sur le bouton rond du téléphone, puis rouvre Familia depuis les applis récentes. **Attendu :** le formulaire est rempli comme tu l'as laissé. Touche « Enregistrer ». **Attendu :** un seul voyage dans la liste.
2. Dans ce voyage, « Ajouter une étape » : choisis le type, le lieu et une heure, puis fais la même chose qu'à l'étape 1. **Attendu :** l'étape est intacte, et il n'y en a qu'une une fois enregistrée.
3. Dans l'album du voyage, « Ajouter des photos », et choisis 5 photos. **Attendu :** au retour de la galerie, les 5 photos partent, même si Familia a été arrêtée. Choisis-en encore 10, puis quitte Familia pendant l'envoi et rouvre-la. **Attendu :** les photos restantes repartent toutes seules, et aucune n'apparaît deux fois.
4. Carnet de recettes, « Nouvelle recette » : écris le titre, deux ingrédients, une étape, puis ajoute 2 photos du plat. Quitte Familia et rouvre-la. **Attendu :** le texte et les 2 photos sont là. Touche « Enregistrer ». **Attendu :** une seule recette, avec ses 2 photos.
5. Capsule, « Nouvelle capsule » : un mot, un destinataire, une date, et une photo de la galerie. Quitte et rouvre. **Attendu :** tout est là, photo comprise. Scelle-la. **Attendu :** une seule capsule.
6. Qui est-ce ? : « Publier une photo », choisis la photo, écris qui c'est, quitte et rouvre. **Attendu :** la photo et le nom sont là.
7. Arbre : ajoute une personne, écris son prénom et son année de naissance, quitte et rouvre. **Attendu :** la fenêtre est encore ouverte avec ce que tu as écrit.
8. Ma valise : commence à écrire une ligne, quitte et rouvre. **Attendu :** le texte de la ligne est encore là.

## Animation de bienvenue (première ouverture)

Avant de commencer : **Administration**, puis **Modules**, et allume « Animation de bienvenue ». Sur ton téléphone d'admin principal, elle se joue même éteinte, pour l'essayer d'abord.

1. Sur le deuxième téléphone, installe la nouvelle version et ouvre Familia. **Attendu :** l'arbre de l'icône pousse, puis ses fruits glissent en cercle autour du soleil, chacun de la couleur d'un membre avec ses lettres. Puis « Bienvenue dans Familia » et « Bonjour {prénom}, tu es chez toi. » apparaissent. Le petit arbre pousse lentement pendant 2 secondes, puis les fruits deviennent les photos, et la dernière image reste 2 secondes. Vers 8 secondes, l'appli continue : le tuto sur une nouvelle installation, l'Accueil sinon.
2. **Attendu :** il y a autant de fruits que de membres, ton foyer en premier, en partant du haut.
   - Pour un membre qui a une photo de profil, **attendu :** sa photo apparaît dans son fruit, ronde, avec un bord de sa couleur. Les membres sans photo gardent leurs lettres.
3. Ferme et rouvre Familia. **Attendu :** l'animation ne revient pas.
4. Touche ta photo en haut à droite, puis « Apparence », puis « Revoir l'animation de bienvenue » tout en bas. **Attendu :** elle se rejoue, puis tu reviens sur Apparence.
5. Pendant qu'elle se joue, touche l'écran n'importe où. **Attendu :** elle s'arrête tout de suite.
6. Avec TalkBack allumé, rejoue-la. **Attendu :** TalkBack lit une seule fois « Bienvenue dans Familia. Bonjour {prénom}, tu es chez toi. », sans lire les fruits un par un.
7. Dans Réglages, puis Accessibilité, allume « Supprimer les animations », et rejoue-la. **Attendu :** le cercle, le titre et la phrase s'affichent d'un coup, sans mouvement, pendant 3 secondes. Éteins ensuite ce réglage.
8. En mode avion, rejoue-la après avoir fermé l'appli. **Attendu :** si la famille n'est pas encore lue, l'appli s'ouvre normalement au bout de 2 secondes et demie au plus, et l'animation attend la prochaine ouverture.
9. Éteins le module, puis ouvre « Apparence » sur le deuxième téléphone. **Attendu :** la ligne « Revoir l'animation de bienvenue » n'y est plus.
10. Module éteint, sur un téléphone qui ne l'a jamais vue : ferme et rouvre Familia deux fois. **Attendu :** à la deuxième ouverture, l'Accueil arrive presque tout de suite, sans écran vide. Rallume le module, puis rouvre Familia. **Attendu :** l'animation se joue.

## Beaucoup de photos lourdes d'un coup, depuis un petit téléphone

À faire de préférence sur le téléphone le plus ancien de la famille, en Wi-Fi puis en 4G. Prépare dans la galerie : une trentaine de photos, une photo prise au zoom ou en très haute définition (50 Mpx si le téléphone le permet), une photo d'iPhone reçue par message ou mail (fichier HEIC), une capture d'écran, une photo prise téléphone tenu en hauteur, et une vidéo.

1. Dans l'album d'un voyage, touche « Ajouter des photos » et choisis 30 photos d'un coup. **Attendu :** « Encore 30 photos à envoyer » s'affiche, puis le nombre baisse et la barre avance photo après photo. Familia ne se ferme pas toute seule.
2. Pendant l'envoi, touche la flèche retour, va sur l'Accueil, puis reviens dans l'album. **Attendu :** l'envoi a continué : la barre et le nombre restant ont avancé, et les photos arrivent une à une. Aucune n'apparaît deux fois.
3. Ajoute la photo très haute définition, la photo HEIC et la capture d'écran. **Attendu :** les trois arrivent dans l'album, nettes, et la capture d'écran garde sa forme toute en hauteur.
4. Ajoute la photo prise téléphone en hauteur. **Attendu :** elle est debout dans l'album, sur ton téléphone et sur un autre.
5. Si la galerie te laisse choisir la vidéo (certains téléphones anciens), choisis-la avec deux photos. **Attendu :** les deux photos partent, et un message dit qu'une photo n'a pas pu être lue. Rien ne bloque.
6. Recommence l'étape 1 dans l'album d'un événement, puis avec 3 photos dans une recette. **Attendu :** pareil.

Une limite connue : un téléphone sous Android 8 ne sait pas ouvrir les photos HEIC. Elles sont refusées avec le message « n'a pas pu être lue », les autres partent.

## Livre photo, livre de recettes et affiche : gros fichiers et coupures

1. Dans un album d'au moins 100 photos (ou le plus gros que tu aies), touche « Faire le livre photo », garde toutes les photos et touche « Faire le livre ». **Attendu :** le livre se fait sans que Familia se ferme, puis le rappel sur l'accord des parents s'affiche avant le partage.
2. Recommence, et touche la flèche retour pendant que le livre se fait. Rouvre « Faire le livre photo » et fais-le jusqu'au bout. **Attendu :** le nouveau livre s'ouvre normalement dans l'appli de partage, jamais un fichier abîmé.
3. Sur le rappel « Avant de partager », touche « Continuer », puis ferme l'appli de partage sans rien choisir. **Attendu :** retour sur l'écran du livre, sans message d'erreur ; « Faire le livre » refait un livre.
4. Mode avion, puis « Faire le livre » avec des photos jamais ouvertes sur ce téléphone. **Attendu :** « Le livre n'a pas pu être fait. Vérifie ta connexion, puis réessaie. »
5. Sur un téléphone presque plein (moins de 100 Mo libres), « Faire le livre photo ». **Attendu :** « Il n'y a plus assez de place sur le téléphone pour ce fichier… ». Libère de la place, puis réessaie : le livre se fait.
6. Fais aussi le livre de recettes et l'affiche de l'arbre avec photos. **Attendu :** les deux se font et s'ouvrent dans l'appli de partage, et les photos d'enfants n'y sont que pour leurs parents.

## L'arbre qui pousse au démarrage (Android 12 et plus)

Sur un téléphone en Android 12 ou plus récent. Pour le savoir : Paramètres, puis « À propos du téléphone », puis « Version d'Android ».

1. Ferme Familia complètement : ouvre les applis récentes et balaie Familia vers le haut.
2. Touche l'icône de Familia. **Attendu :** sur le fond crème, le soleil apparaît, puis le tronc pousse de bas en haut, le feuillage s'ouvre avec un petit rebond, et les fruits sortent en quatre vagues, en un peu moins d'une seconde et demie. L'arbre va jusqu'au bout avant que l'appli s'ouvre.
   - Tourne le téléphone une fois l'appli ouverte. **Attendu :** l'écran se redessine tout de suite, sans revoir l'arbre ni attendre.
   - Mets Familia en arrière-plan avec le bouton d'accueil, puis rouvre-la. **Attendu :** l'appli revient tout de suite.
3. Recommence en mode sombre (Paramètres, puis Affichage, puis « Thème sombre »). **Attendu :** le même arbre, sur un fond brun foncé.
4. Dans Paramètres, puis Accessibilité, allume « Supprimer les animations », puis recommence l'étape 1. **Attendu :** l'arbre entier s'affiche d'un coup, sans mouvement, et l'appli s'ouvre sans attendre. Éteins ensuite ce réglage.
5. Sur un téléphone en Android 11 ou plus ancien, s'il y en a un dans la famille. **Attendu :** le démarrage est le même qu'avant.

Si quelque chose ne va pas le 20 au soir, préviens [DEV] : on revient à l'icône fixe en retirant un seul changement.

## Ouverture rapide d'un compte déjà connecté

1. Sur un téléphone déjà connecté, ferme Familia en la balayant des applis récentes. Coupe le Wi-Fi et la 4G, puis rouvre Familia. **Attendu :** l'Accueil s'ouvre tout de suite, sans écran de connexion ni « Réessayer ».
2. Remets le réseau. Depuis un autre téléphone d'admin, change le prénom de ce membre. Ferme et rouvre Familia sur le premier téléphone. **Attendu :** l'appli s'ouvre tout de suite, et le nouveau prénom apparaît quelques secondes après au plus (par exemple dans Mon profil).
3. Sur un téléphone partagé, déconnecte-toi puis connecte un autre membre. **Attendu :** c'est bien l'autre membre qui entre, jamais le précédent.

## Planning allégé : les compteurs « viennent »

1. Ouvre le Planning. **Attendu :** chaque événement à venir montre, comme avant, combien de personnes viennent ou viendront peut-être, avec leurs photos.
2. Sur un autre téléphone, réponds « Je viens » à un événement. **Attendu :** le compteur du premier téléphone monte en quelques secondes, sans rouvrir le Planning.
3. Crée un nouvel événement, puis réponds-y. **Attendu :** son compteur apparaît aussi.
4. Coupe le réseau et rouvre le Planning. **Attendu :** les événements s'affichent, avec les compteurs déjà lus.

## Liens d'agenda dans Administration

Avec le module « Voir le planning dans mon agenda » allumé (ou sur le téléphone de l'admin principal, qui le voit toujours).

1. Sur le téléphone d'un adulte, crée son lien d'agenda et ajoute-le à Google Agenda.
2. Sur le téléphone de l'admin principal : **Administration**, puis la carte « Liens d'agenda ». **Attendu :** tous les adultes, rangés par foyer, chacun avec un bouton « Couper le lien de {prénom} ».
3. Touche le bouton de cet adulte, puis « Couper ». **Attendu :** « Le lien de {prénom} est coupé. » s'affiche. Sur son téléphone, l'écran du lien d'agenda propose d'en créer un nouveau.
4. Coupe le lien d'un adulte qui n'en avait pas. **Attendu :** le même message, et rien ne change pour lui.

## Bon plan mis en avant sur l'Accueil

Avec le module « Bons plans » allumé. Deux téléphones : celui qui publie, celui qui regarde.

1. Sur le premier téléphone, publie un bon plan avec une photo. Sur le second, ouvre l'Accueil. **Attendu :** une carte « BON PLAN » avec la photo en large, coupée proprement aux bords arrondis, le titre, puis « {prénom} · Aujourd'hui à … ».
2. Touche la carte. **Attendu :** le bon plan s'ouvre.
3. Publie un bon plan sans photo. **Attendu :** la carte montre le titre, l'auteur et le début du texte, sans cadre vide.
4. Sur le téléphone qui a publié. **Attendu :** pas de carte : on ne voit pas son propre bon plan.
5. Deux jours plus tard, ou après la fin du bon plan. **Attendu :** la carte disparaît.

## Arbre vide : relier deux membres (retour d'essai du 17/11)
1. En admin principal, avec un arbre sans aucun lien ni proche, ouvre Tout Familia › Arbre généalogique. **Attendu :** l'écran vide propose « Relier deux membres » (bouton plein) et « Ajouter un proche sans compte ».
2. Touche « Relier deux membres ». **Attendu :** « Qui veux-tu relier ? » liste les membres, ton foyer d'abord. Choisis-en un, puis « Suivant ».
3. Choisis « Le conjoint ou la conjointe de … » (ou un parent, un enfant), puis la seconde personne, et « Relier ». **Attendu :** l'arbre s'affiche avec tous les membres et le lien tracé.
4. En chef de famille (ou « Voir comme » un chef), même écran vide. **Attendu :** « Qui veux-tu relier ? » ne liste que son foyer. En simple adulte, aucun de ces boutons, seulement « Revenir ».
5. Ajoute un proche sans compte « en souvenir » qui porte le même prénom qu'un membre, puis ouvre « Relier deux membres », ou « Ajouter un lien » sur une fiche. **Attendu :** les deux lignes se distinguent : le membre avec le nom de son foyer, le proche avec son nom de famille, sinon ses années, sinon « En souvenir ». Un prénom unique reste seul.

## « À venir » des Cadeaux : anniversaires des 30 prochains jours, Noël dès le 1er novembre

Présentation R1 bis et liste unique allumées.

1. Ouvre Cadeaux. **Attendu :** sous « Prochaine occasion », seuls les anniversaires des 30 prochains jours, et Noël à partir du 1er novembre s'il n'est pas déjà la prochaine occasion.
2. Si aucun anniversaire ne tombe dans les 30 jours. **Attendu :** aucune carte d'anniversaire sous la prochaine occasion. « Tous les anniversaires » les montre tous.
## Le nouvel olivier : icône, démarrage, bienvenue

1. Installe l'APK et regarde l'icône sur l'écran d'accueil du téléphone. **Attendu :** l'olivier final, avec un feuillage dense, 13 fruits de même taille, des racines en éventail aux bouts arrondis et un anneau rouge fin autour du soleil. Les icônes Terre et Or ne changent pas.
2. Ferme l'appli complètement, puis rouvre-la (Android 12 ou plus). **Attendu :** l'arbre pousse dans cet ordre : le soleil, les racines, le tronc, le feuillage, puis les fruits.
3. Avec « Animation de bienvenue » allumée, ouvre l'appli sur un compte neuf. **Attendu :** le même olivier, avec son anneau rouge fin. Chaque fruit est un membre : sa photo de profil, dans un anneau blanc et un contour de sa couleur, ou son initiale sur sa couleur s'il n'a pas de photo. Les fruits partent ensuite en cercle comme avant.

## Mise à jour en cours : la ronde de la famille

1. Publie une version plus récente, puis touche « Mettre à jour ». **Attendu :** « Mise à jour en cours », la phrase « La nouvelle version arrive. », puis l'olivier sans fruits dans son soleil. Autour, les membres de la famille (photo ou initiale) tournent lentement, en restant droits, et le feuillage se balance.
2. **Attendu :** sous le dessin, le pourcentage avance avec le téléchargement, puis la barre verte. Le texte d'aide dit de toucher « Installer sans analyser » si Android propose d'analyser l'appli.
3. Avec TalkBack. **Attendu :** le titre, « Téléchargement : 42 % » (le vrai pourcentage), puis le texte d'aide. Le dessin n'est pas lu.
4. Coupe les animations du téléphone (Options pour les développeurs, échelle d'animation désactivée). **Attendu :** la ronde est fixe. Seuls le pourcentage et la barre avancent.

## Annonces de l'admin : envoyer une notification (récit 69)

Après « Publier Firebase ». Deux téléphones : celui de l'admin principal et celui d'un autre adulte.

1. Administration, module « Annonces de l'admin » éteint. **Attendu :** pas de ligne « Envoyer une notification », même pour l'admin principal.
2. Allume le module. **Attendu :** la ligne « Envoyer une notification » apparaît en haut d'Administration, sous « Démo et présentation ».
3. Ouvre-la. **Attendu :** tout le monde est coché, rangé par foyer, sauf toi et les enfants sans l'appli. Décocher un foyer décoche tous ses membres.
4. Écris un titre (60 caractères au plus) et un message (300 au plus). **Attendu :** l'aperçu montre le titre et le début du message ; « Envoyer à N personnes » s'allume.
5. Touche « Envoyer », puis « Envoyer » dans la fenêtre. **Attendu :** « Message envoyé. », le message dans « Déjà envoyées » avec « envoyé ». L'autre téléphone reçoit la notification, même avec toutes ses notifications coupées dans les réglages de Familia.
6. Sur l'autre téléphone, touche la notification. **Attendu :** le message entier, signé « Prénom (admin) », sans rien pour répondre.
7. Après 22 h, sans « Urgent ». **Attendu :** la fenêtre dit qu'il partira à 8 h, « Déjà envoyées » dit « part à 8 h », et la notification arrive seule à 8 h, pas dans « Cette nuit dans Familia ».
8. Après 22 h, avec « Urgent ». **Attendu :** elle arrive tout de suite.
9. Allume le mode présentation. **Attendu :** l'écran dit que rien ne part, et le bouton reste éteint.

## Tout Familia : Bons plans, On se prête et À qui le tour ? (PRODUIT-118)

1. Avec Entraide et les trois modules allumés, ouvre « Tout Familia ». **Attendu :** sous « Entraide », trois icônes de plus : « Bons plans », « On se prête », « À qui le tour ? ».
2. Touche « On se prête ». **Attendu :** l'onglet Entraide s'ouvre directement sur son sous-onglet « On se prête ». Même chose pour les deux autres.
3. Reviens à l'Accueil, puis touche l'onglet Entraide en bas. **Attendu :** il garde le sous-onglet où tu étais, sans sauter ailleurs.
4. Cherche « prêt », puis « tour ». **Attendu :** « On se prête », puis « À qui le tour ? ».
5. Éteins « On se prête » dans Administration > Modules. **Attendu :** son icône disparaît de « Tout Familia ».

## Fil d'actualité : Bonnes nouvelles et leurs commentaires, coups de main, question (récit 67)

Module « Fil d'actualité » allumé dans Administration > Modules, après « Publier Firebase ». Éteint, la Bonne nouvelle ne montre pas de commentaires.

1. Ouvre une Bonne nouvelle d'un autre membre. **Attendu :** sous la nouvelle, « Commentaires », « Pas encore de commentaire… », un champ et « Envoyer ».
2. Écris « Bravo ! » et envoie. **Attendu :** le commentaire apparaît, signé de ton prénom et de l'heure. L'auteur de la nouvelle reçoit « Prénom a commenté ta Bonne nouvelle ».
3. Sur un troisième téléphone, commente aussi. **Attendu :** tu reçois « Prénom a aussi commenté la Bonne nouvelle ». Touche « Ne plus être prévenu des commentaires », puis fais commenter encore : plus rien n'arrive.
4. Modifie ton commentaire, puis retire-le. **Attendu :** « modifié » s'affiche, puis le commentaire disparaît. L'auteur de la nouvelle et l'admin principal ont aussi « Retirer ».
5. Retire la nouvelle. **Attendu :** ses commentaires partent avec elle.
6. En mode présentation, puis en « Voir comme ». **Attendu :** les commentaires se lisent, sans champ, sans « Modifier » ni « Retirer ».

Dans « Le fil » de l'Accueil :

7. Publie une Bonne nouvelle avec une photo et un long texte. **Attendu :** une carte « Bonne nouvelle » avec une vignette carrée, le texte coupé à 3 lignes et « Lire la suite », les réactions, puis « Commenter ». Toucher la carte ouvre la nouvelle.
8. Touche « ❤️ », puis « Commenter », écris et envoie. **Attendu :** le cœur compte 1 ; le champ se ferme, la carte montre « Prénom : ton texte » sur une ligne et « 💬 1 ». « 💬 1 » ouvre la nouvelle avec ses commentaires.
9. Masque pour toi une nouvelle qui te nomme. **Attendu :** elle quitte ton fil, pas celui des autres.
10. Sur un autre téléphone, demande un coup de main en te prévenant. **Attendu :** une carte « Coup de main » avec « Je m'en occupe » et « Pas dispo ». « Je m'en occupe » la change en une ligne « Prénom s'en occupe : … ✓ ». Un coup de main annulé disparaît.
11. Un lundi, après 9 h. **Attendu :** une carte « Question de la semaine · N réponses » avec « Répondre », qui devient « Voir les réponses » une fois répondu.
12. En mode présentation, puis en « Voir comme ». **Attendu :** les cartes se lisent, sans réaction possible, sans « Commenter », « Je m'en occupe » ni « Répondre ».

## Fil d'actualité : une tuile par actualité (PRODUIT-127)

Module « Fil d'actualité » allumé.

1. Ouvre « Le fil ». **Attendu :** chaque actualité est une carte blanche aux coins arrondis comme celles du Planning, avec une ombre très douce et un fin trait de sa couleur dessous. Il n'y a plus de liseré coloré à gauche ; la pastille du type reste en haut à droite.
2. **Attendu :** 8 dp entre deux actualités d'un même jour, un peu plus avant et après « Aujourd'hui », « Hier ».
3. En thème sombre, puis à 130 % de taille de texte. **Attendu :** les cartes restent lisibles et séparées, rien n'est coupé.

## Fil d'actualité : la bascule « Aujourd'hui | Le fil » (PRODUIT-128)

Interrupteur newsFeed allumé.

1. Accueil, côté Aujourd'hui : la date à gauche (« Mercredi 18 novembre »), à droite un bouton vert pâle « Le fil » avec la pastille jaune des nouveautés et une flèche. Plus de segment noir.
2. Toucher le bouton : le fil s'ouvre. À gauche « Le fil de la famille », à droite un bouton blanc « < Aujourd'hui », sans pastille.
3. Toucher « Aujourd'hui » : retour à l'Accueil du jour.
4. TalkBack : « Voir le fil, 4 nouveautés » puis « Revenir à Aujourd'hui ».
5. Taille du texte à 130 % : les deux boutons restent lisibles ; si la place manque, le bouton passe sous le titre.
6. Interrupteur newsFeed éteint : aucune bascule, l'Accueil est inchangé.

## Fil d'actualité de l'Accueil (récit 67)

Module « Fil d'actualité » allumé dans Administration > Modules (éteint, l'Accueil ne change pas, même pour l'admin principal).

1. Ouvre l'appli. **Attendu :** l'Accueil s'ouvre sur « Aujourd'hui », avec « Aujourd'hui | Le fil » sous « Bonjour ». Un chiffre jaune à côté de « Le fil » compte ce que les autres ont ajouté depuis ta dernière visite.
2. Touche « Le fil ». **Attendu :** les cartes du plus récent au plus ancien, sous « Aujourd'hui », « Hier », puis « Lundi 16 novembre ». « Tout Familia » reste en bas. Le chiffre disparaît.
3. Sur un autre téléphone, ajoute un événement au Planning. **Attendu :** une carte « Planning » arrive en haut du fil, avec la date, l'heure, « J'y vais » et le dernier commentaire s'il y en a. « J'y vais » répond « Oui » et disparaît. Toucher la carte ouvre l'événement.
4. Un jour d'anniversaire. **Attendu :** une carte « Anniversaire · Prénom a N ans », avec « Lui souhaiter » qui ouvre sa fiche (si les vœux d'anniversaire sont allumés).
5. Touche « Voir plus ancien ». **Attendu :** les 14 jours d'avant s'ajoutent en bas.
6. En mode présentation, puis en « Voir comme ». **Attendu :** le fil se lit, sans « J'y vais » ni « Lui souhaiter ». Jamais rien des Cadeaux.
7. Ajoute trois photos à l'album d'un événement passé, ou d'un voyage que toute la famille suit. **Attendu :** une carte verte « Photos » avec le titre, « 3 nouvelles photos » et les vignettes. Les photos d'un même jour et d'un même album font une seule carte. Toucher la carte ouvre l'album sur la photo la plus récente.
8. Ajoute des photos à un voyage que quelqu'un ne suit pas (un voyage surprise). **Attendu :** aucune carte, même sur le téléphone de quelqu'un qui le suit.
9. Ajoute une recette. **Attendu :** une carte orange « Recette » avec son titre, « La recette de … » si l'origine est notée, la photo du plat si elle existe et le dernier commentaire. Toucher la carte ouvre la recette.
