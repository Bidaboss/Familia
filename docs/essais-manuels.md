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

## Photo en grand (demande du 15/11)

1. Dans l'onglet Famille, touche la photo d'un membre qui en a une. **Attendu :** elle s'affiche en plein écran sur fond noir, avec son prénom en bas.
2. Touche « Fermer » en haut à gauche, puis recommence et fais le geste retour d'Android. **Attendu :** les deux referment la photo.
3. Sur ta propre photo (ou celle d'un enfant de ton foyer), **attendu :** le bouton « Changer la photo » est en bas et ouvre le choix galerie ou appareil photo. Sur la photo d'un autre foyer, ce bouton n'apparaît pas.
4. Sur un membre sans photo que tu peux modifier, touche l'avatar. **Attendu :** le choix de la photo s'ouvre directement, comme avant.

## Haut du planning sur une rangée (audit DESIGN n°3)

1. Ouvre le planning. **Attendu :** une seule rangée en haut, avec « Liste | Mois » à gauche et « Mon foyer » à droite.
2. Touche « Mon foyer ». **Attendu :** une coche apparaît et seuls les événements de ton foyer restent. Touche-le à nouveau : toute la famille revient.
3. En police à 200 %, **attendu :** rien n'est coupé, et les boutons gardent au moins la hauteur d'un doigt.

## Tuto : écrans Cadeaux et Entraide (AVIS-18)

1. Dans Administration > Modules, allume Cadeaux et Entraide. Sur un autre téléphone, avec un compte qui n'est pas admin, ouvre l'appli pour la première fois. **Attendu :** après « Dire si tu viens », les écrans « Les cadeaux » puis « L'entraide » s'affichent, et « Rester prévenu » reste le dernier (6 sur 6).
2. Éteins Entraide, puis touche « Revoir le tuto » dans l'Aide. **Attendu :** l'écran « L'entraide » n'apparaît plus (5 écrans).

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
2. Ouvre « Apparence ». **Attendu :** les 9 thèmes, chacun montré dans ses couleurs et sa police. Touche « Espagne ». **Attendu :** l'appli change tout de suite : fond sable avec azulejos, titres en Abril Fatface, cartes à bordure bleue et ombre dorée.
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

## Partager Familia avec toute la famille (récit 1 bis bis)

Pas d'interrupteur : le bouton est là pour tous les adultes, dans la version famille (le lien vient du secret FAMILIA_DOWNLOAD_URL, comme l'invitation).

1. Avec un compte adulte, ouvre l'onglet **Famille** et touche **Partager Familia**. **Attendu :** le partage d'Android s'ouvre, WhatsApp en tête s'il est installé.
2. Choisis WhatsApp. **Attendu :** le message est déjà écrit et tu peux le modifier avant d'envoyer : l'accueil, le lien du dossier Drive, trois étapes, puis « Si ton adresse n'est pas inscrite, demande à » suivi du prénom de l'admin principal.
3. Vérifie le lien. **Attendu :** c'est le même que dans « Envoyer l'invitation », sans rien après (aucun jeton, aucune adresse).
4. Avec l'admin principal, ouvre **Administration** et touche **Partager Familia**. **Attendu :** le même message.
5. Avec un compte enfant, ouvre l'onglet Famille. **Attendu :** pas de bouton Partager Familia.
6. En mode **Voir comme** un adulte. **Attendu :** pas de bouton Partager Familia (rien ne sort de l'appli dans ce mode).

## La semaine de la famille (récit 36)

Avec le module « Récap du dimanche » allumé, et deux comptes : un qui suit un voyage en cours, un autre décoché de ce voyage.

1. Un dimanche après 18 h, regarde les notifications. **Attendu :** une seule « La semaine de la famille », discrète, avec par exemple « 3 photos, 1 anniversaire à venir, défi à 40 %, 1 voyage en cours. »
2. Avec le compte décoché du voyage, lis la même notification. **Attendu :** elle ne parle pas de ce voyage.
3. Touche la notification. **Attendu :** l'écran « La semaine de la famille » s'ouvre, avec au plus 3 photos, les anniversaires des 14 prochains jours, la jauge du défi et les voyages en cours. Aucun nombre de réactions, aucun nom sous les photos.
4. Touche une photo, puis reviens, puis touche un voyage. **Attendu :** la Bonne nouvelle, puis le voyage, s'ouvrent.
5. Réglages > Notifications : coupe « La semaine de la famille ». **Attendu :** le dimanche suivant, plus de notification pour ce compte.
