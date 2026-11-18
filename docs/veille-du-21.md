# La veille du 21 : la liste à cocher

Cette liste se suit le 20 au soir, après le gel, sur ton téléphone. Une ligne correspond à un geste. Coche chaque ligne quand elle est faite. Si un geste ne donne pas ce qui est écrit après **Attendu**, arrête-toi, fais une capture d'écran et préviens [DEV] avant de continuer.

Le repas est l'anniversaire de quelqu'un, qu'on appelle ici « la personne fêtée ». Rien sur son cadeau ne doit apparaître à l'écran.

Pour ouvrir **Administration** : touche ta photo en haut à droite de l'écran, puis « Administration », tout en bas de la feuille qui s'ouvre.

Le « deuxième téléphone » est celui de la personne qui t'aide à tester.

## 1. Publier la dernière version (sur GitHub, depuis le téléphone)

- [ ] Ouvre l'appli GitHub, puis le dépôt Familia, puis l'onglet **Actions**.
- [ ] Dans la liste de gauche (ou le menu « All workflows »), touche **Publier Firebase**.
- [ ] Touche « Run workflow », laisse la branche sur `main`, puis touche le bouton vert « Run workflow ».
- [ ] Attends la pastille verte à côté de la ligne qui vient d'apparaître. Ça prend quelques minutes. **Attendu :** une coche verte. Une croix rouge : arrête-toi et préviens [DEV].
- [ ] Reviens à la liste des workflows et touche **APK de publication**.
- [ ] Touche « Run workflow », laisse la branche sur `main`, puis touche le bouton vert « Run workflow ».
- [ ] Attends la coche verte. Ça peut prendre une quinzaine de minutes.
- [ ] Ouvre la console Firebase dans le navigateur, puis **Firestore**, la collection `config` et le document `app`.
- [ ] **Attendu :** les champs `latestVersionName` et `latestVersionCode` portent le nouveau numéro, celui de l'APK qui vient d'être construit. Note ce numéro ici : ________

## 2. Installer la version sur les deux téléphones

Fais d'abord le deuxième téléphone, puis le tien. La marche détaillée se trouve dans la section « Mise à jour du 20 au soir » de [essais-manuels.md](essais-manuels.md).

- [ ] Sur le deuxième téléphone, ouvre Familia. **Attendu :** l'encadré « Nouvelle version disponible ». S'il n'apparaît pas, touche ta photo en haut à droite, puis « Vérifier les mises à jour ».
- [ ] Touche « Mettre à jour », puis « Mettre à jour » dans la fenêtre d'Android.
- [ ] Rouvre Familia. **Attendu :** tu arrives sur l'Accueil, connecté, sans écran de connexion.
- [ ] La feuille « Quoi de neuf ? » s'ouvre : touche « C'est parti ». Ferme et rouvre l'appli. **Attendu :** la feuille ne revient pas.
- [ ] Fais les quatre mêmes gestes sur ton téléphone.
- [ ] Sur chaque téléphone, touche ta photo en haut à droite, puis « Vérifier les mises à jour ». **Attendu :** « Version installée : » suivi du numéro noté à l'étape 1, et « Tu as la dernière version. ».

## 3. Les modules

- [ ] Ouvre **Administration**, puis **Modules**.
- [ ] Allume ce que tu veux montrer demain. Sous chaque interrupteur, une phrase dit à quoi il sert, et « Voir où » ouvre l'écran concerné.
- [ ] Laisse **éteints** les modules prévus pour après le 21. Un module éteint ne laisse aucune trace chez les autres : ni pastille, ni carte, ni aide, ni notification.
- [ ] Les modules à allumer plus tard, avec leur date :
  - [ ] « Mes premiers pas » : le 21 au soir après le repas, ou le 22.
  - [ ] « Livre de recettes », « Livre photo », « Affiche de l'arbre » et « Budget cadeaux » : avant le 1er décembre, pour Noël.
  - [ ] « À découvrir » et « Question de la semaine » : avant le lundi 30 novembre.
  - [ ] « Notre année » : avant le 26 décembre.
  - [ ] Les autres, quand tu veux : lis leur phrase dans Modules, et lance Publier Firebase avant si elle le demande.
- [ ] Ferme et rouvre **Administration**, puis **Modules**. **Attendu :** les interrupteurs sont restés comme tu les as mis.

## 4. Les essais à passer avant de dormir

Passe chaque essai d'abord en mode avion, puis en « 2G seulement ». Pour la 2G : Réglages du téléphone, puis Réseau mobile, puis Type de réseau préféré, puis « 2G seulement », avec le Wi-Fi coupé. Remets le réseau normal à la fin.

- [ ] « Démo du 21 : première arrivée, Accueil, Planning et événement sur un réseau faible », dans [essais-manuels.md](essais-manuels.md).
- [ ] « Démo du 21 : Bonnes nouvelles et Entraide sur un réseau faible ».
- [ ] « Démo du 21 : album, diaporama, recettes, arbre et Qui est-ce ? sur un réseau faible », si tu montres ces modules.
- [ ] « Bonnes nouvelles : envoi de la photo sur un réseau faible ou coupé ».
- [ ] « Voir comme » sur un enfant : **Administration**, puis « Démo et présentation », puis « Voir comme… », puis un enfant d'environ 11 ans. Parcours Accueil, Planning, Entraide et Bonnes nouvelles. **Attendu :** aucun bouton qui finit sur un refus, et aucun texte d'adulte. Recommence avec un enfant d'environ 16 ans. C'est l'étape 3 de « Écrans vides et profils enfant ».
- [ ] Touche « Quitter » sur le bandeau « Tu vois l'appli comme … ».
- [ ] Un enfant suivi par Family Link : installe Familia sur son téléphone et connecte son compte, avec le parent qui le suit à côté. C'est « Un enfant suivi par Family Link se connecte (avant le 21) », dans [essais-manuels.md](essais-manuels.md). **Si ça bloque**, ne retente pas le jour J : sur sa fiche, passe son profil à « Enfant, sans l'appli ». Ses parents répondent pour lui, et on retente après le repas, au calme.

## 5. Préparer le repas, encore ce soir

- [ ] Fais une répétition : **Administration**, puis « Démo et présentation », puis « Voir comme… », puis un membre qui n'est **pas** la personne fêtée. Fais la visite guidée de [scenario-demo.md](scenario-demo.md) (étape 5). Puis regarde l'onglet Cadeaux en « Voir comme » la personne fêtée. **Attendu :** rien ne lui montre ce qui est réservé pour elle. Note ce qui pourrait gêner.
- [ ] Toujours en « Voir comme », ouvre l'événement du repas. **Attendu :** rien n'y parle du cadeau.
- [ ] Touche « Quitter » sur le bandeau.
- [ ] Avec le réseau, ouvre une fois Accueil, Planning, Entraide et Famille. Ils resteront lisibles si le réseau tombe demain.
- [ ] Vérifie que chaque membre a son adresse Google dans sa fiche (onglet **Famille**, une fiche après l'autre).
- [ ] Télécharge l'APK du Drive sur ton téléphone, dans « Téléchargements ». C'est ton plan B si le réseau du lieu est mauvais.
- [ ] Ouvre un événement à venir où la famille se retrouvera, par exemple le repas de Noël. Dans « Qui apporte quoi », ajoute trois ou quatre lignes, **sans les prendre** : « Une entrée », « Un dessert », « Les boissons », « Le pain ». Demain, au café, chacun pourra en prendre une depuis son téléphone (visite guidée, étape 5). C'est un premier geste simple et utile. Ne le fais pas sur l'événement du repas d'anniversaire.
- [ ] Recharge les deux téléphones à 100 %.

## 6. Le jour du repas (à relire le matin)

**Avant de partir**
- [ ] Mets ton téléphone en « Ne pas déranger » : glisse du haut de l'écran vers le bas, puis touche « Ne pas déranger ».

**Pendant le repas**
- [ ] Prends la photo de la table avec l'appareil photo, **sans ouvrir Familia**. Le repas reste celui de la personne fêtée.

**Au café** (le déroulé complet est dans [scenario-demo.md](scenario-demo.md))
- [ ] Ouvre Familia. **Attendu :** pas de bandeau « Hors ligne ». S'il apparaît, active le partage de connexion 4G.
- [ ] **Administration**, puis « Démo et présentation » tout en haut, puis la carte « Mode présentation », puis « 4 h ». **Attendu :** le bandeau « Mode présentation jusqu'à … » au-dessus des onglets.
- [ ] Envoie les invitations, puis laisse chacun se connecter et suivre le tuto sur son téléphone.
- [ ] Fais la visite guidée de 5 minutes : Accueil, un événement (« Je viens », « Qui apporte quoi »), Cadeaux.
- [ ] Ne tourne jamais **ton propre** Accueil ni **ton propre** onglet Cadeaux vers la table : l'occasion de la personne fêtée y apparaît, et « Surprises en préparation » est tout en haut de Cadeaux.
- [ ] N'ouvre pas **ta propre** fiche hors mode présentation : la carte « Mes cadeaux à offrir », ton carnet privé, y est.
- [ ] N'ouvre pas la fiche de la personne fêtée (ni d'un autre membre) hors mode présentation : sa fiche montre « Ce que j'offre à … », avec tes réservations et les surprises en cours.
- [ ] Publie la photo du repas depuis le fil des Bonnes nouvelles, **écran tourné vers toi**, puis ajoute-la à l'album de l'événement (étape 6 de [scenario-demo.md](scenario-demo.md)).
- [ ] « Voir comme » ne sert qu'à aider quelqu'un de perdu. Touche « Quitter » sur le bandeau ensuite.

**À la fin**
- [ ] **Administration**, puis « Démo et présentation », puis « Éteindre » sur la carte « Mode présentation ».
- [ ] **Attendu :** en moins d'une minute, chacun reçoit le résumé de la présentation, en trois notifications au plus. S'il n'arrive pas, il part au plus tard 30 minutes après. Après 22 h, il attend le lendemain à 8 h pour ceux qui ont « Calme la nuit », c'est-à-dire presque tout le monde : éteins le Mode présentation avant 22 h si tu veux qu'il arrive le soir même.
- [ ] Enlève « Ne pas déranger ».
- [ ] Le soir ou le lendemain, allume les modules de ta liste « à allumer plus tard » (étape 3).
