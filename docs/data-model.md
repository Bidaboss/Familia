# Modèle de données Familia (Firestore)

Conventions : noms de collections et de champs en anglais, en camelCase. Dates sans heure au format `AAAA-MM-JJ` (texte). Adresses e-mail toujours en minuscules.

Les règles de sécurité sont dans `firebase/firestore.rules` et leurs tests dans `firebase/tests/`. Chaque lot qui ajoute une collection ajoute ses règles et ses tests.

## Reconnaître un membre (lot 2)

Un membre invité n'a pas encore de compte Firebase (pas d'UID) tant qu'il ne s'est pas connecté. Les règles le reconnaissent donc par **son adresse Google vérifiée** :

1. L'admin principal ou un chef de famille crée le membre dans `members/{memberId}` et l'entrée d'index `memberEmails/{email}` → `{ memberId }`.
2. À la connexion, Firebase fournit `request.auth.token.email` et `email_verified`. Les règles mettent l'adresse en minuscules et vérifient que `memberEmails/{email}` existe : c'est la fonction `isMember()`.
3. À la première connexion, l'appli écrit l'UID dans le champ `uid` du membre. C'est la seule écriture permise au membre sur son propre document, une seule fois, sans toucher aux autres champs.

Pourquoi un index séparé plutôt qu'une recherche : les règles Firestore ne peuvent pas faire de requête, seulement lire un document dont on connaît le chemin. L'adresse sert donc d'identifiant de document dans `memberEmails`.

Une personne connectée mais absente de la famille peut lire uniquement sa propre entrée `memberEmails/{son adresse}`, qui n'existe pas. L'appli affiche alors « ne fait pas partie de la famille » au lieu d'une erreur de permission.

## Collections

### `members/{memberId}`

| Champ | Type | Description |
|---|---|---|
| `firstName` | texte | Prénom affiché partout |
| `email` | texte ou null | Adresse Google, en minuscules. Null pour un enfant sans l'appli |
| `birthDate` | texte `AAAA-MM-JJ` ou null | Sert à calculer les anniversaires (lot 5) |
| `householdId` | texte | Foyer du membre |
| `profile` | `adult` \| `childWithApp` \| `childWithoutApp` | Droits du membre |
| `isAdmin` | booléen | Chef de famille de son foyer (ancien « admin »), toujours adulte. Plusieurs par foyer possibles |
| `colorIndex` | entier 0 à 12, facultatif | Couleur du membre parmi les 13 de l'appli. Sans valeur, l'appli en déduit une de l'identifiant |
| `parentIds` | liste de `memberId` | Pour un enfant : de 1 à 4 adultes responsables, qui doivent exister et être adultes (un beau-parent a les mêmes droits qu'un parent). Toujours vide pour un adulte (audit du lot 7) |
| `uid` | texte ou null | UID Firebase, écrit à la première connexion |

### `memberEmails/{email}`

| Champ | Type | Description |
|---|---|---|
| `memberId` | texte | Le membre qui a cette adresse |

Écrit uniquement par l'admin principal ou un chef de famille du foyer du membre, **dans la même écriture groupée** (batch) que le membre :

- ajouter un membre : créer `members/{id}` et `memberEmails/{email}` ensemble ;
- changer son adresse : mettre à jour le membre, supprimer l'ancienne entrée et créer la nouvelle ;
- retirer un membre : supprimer `members/{id}` et son entrée ensemble. Sinon la personne garderait l'accès, et les règles refusent l'écriture.

## Admin principal et chefs de famille (lot 3, carte DEV-8)

- **L'admin principal** est nommé dans `config/family`, que seul le workflow GitHub « Créer le premier admin » écrit. Personne ne peut lui retirer ce rôle depuis l'appli. S'il perd l'accès, relancer ce workflow avec son adresse lui rend le rôle. Il a tous les droits sur toute la famille, y compris pour un foyer sans chef, et lui seul voit le menu « Administration » : nommer ou retirer les chefs, créer ou supprimer un foyer, sauvegarder les données (PRD, récit 1 ter).
- **Un chef de famille** (`isAdmin` à vrai) a les droits d'un adulte, plus ceux-ci, limités à son foyer (PRD, « Périmètre d'un chef de famille ») : ajouter, inviter, modifier ou retirer les membres de son foyer et changer leur profil ; choisir les adultes responsables des enfants de son foyer parmi les adultes de ce foyer ; renommer son foyer ; modifier ou supprimer les événements dont l'auteur est de son foyer.
- Un chef ne peut pas : agir sur un autre foyer, déplacer un membre vers un autre foyer, créer ou supprimer un foyer, nommer ou retirer un chef (ni retirer un membre qui est chef), toucher à l'admin principal ou à son propre rôle, sauvegarder les données.
- Si l'admin principal a choisi pour un enfant un adulte responsable d'un autre foyer, un chef peut modifier le reste de la fiche, mais plus les adultes responsables : les règles le refusent, et l'appli grise la liste.
- Un foyer n'est supprimé que par l'admin principal, et l'appli ne le propose que pour un foyer sans membres. Ses anciens événements restent dans le planning.
- La fiche membre de l'appli (lot 3c) vérifie en plus ce que les règles ne peuvent pas vérifier : une adresse qui n'appartient à aucun autre membre, des adultes responsables qui sont bien des adultes, une date de naissance qui n'est pas dans le futur. Hors ligne, la fiche se ferme au bout de 4 secondes et l'écriture part au retour du réseau. Limite connue : si les règles la refusent à ce moment-là, le refus passe inaperçu (le cache revient simplement à l'état du serveur). Le risque est faible, puisque la fiche vérifie les mêmes contraintes avant d'écrire.
- Retirer un membre retire aussi son identifiant des `parentIds` des enfants qui le listaient, dans le même batch. L'appli refuse de retirer le seul adulte responsable d'un enfant, et réserve à l'admin principal le retrait d'un adulte responsable d'un enfant d'un autre foyer.
- Les rôles sont lus en direct : un chef dont le rôle est retiré perd les boutons d'ajout et de modification tout de suite.
- Personne ne peut modifier le champ `uid` : seul le membre le lie, à sa première connexion.
- Les règles vérifient la forme d'un membre : prénom de 1 à 40 caractères, adresse en minuscules, date au format `AAAA-MM-JJ`, foyer existant, un enfant a au moins un adulte responsable, un enfant sans l'appli n'a pas d'adresse, un chef est forcément adulte.
- Limite de Firestore : une écriture lit au plus 10 documents dans les règles. Chaque adulte responsable coûte une lecture, d'où un seul `getAfter` par adulte.

### `households/{householdId}`

| Champ | Type | Description |
|---|---|---|
| `name` | texte | Nom du foyer |

### `memberPhotos/{memberId}` (récit 3 bis, photo de profil)

| Champ | Type | Rôle |
|---|---|---|
| `jpeg` | bytes | Photo carrée de 512 px au plus, en JPEG, faite par l'appli (100 Ko visés, 150 Ko au maximum dans les règles) |
| `updatedAt` | timestamp | Heure du serveur au moment de l'envoi |

- **Qui la change :** le membre lui-même (enfant avec l'appli compris), tout adulte du même foyer pour un enfant (avec ou sans l'appli), et l'admin principal. Un chef de famille n'a pas de droit en plus ici.
- **Qui la lit :** toute la famille, et personne d'autre.
- **Retirer la photo** supprime le document. La suppression d'un membre retire aussi sa photo, dans le même lot d'écriture.
- **Pourquoi Firestore plutôt que Storage :** 13 photos d'environ 100 Ko tiennent largement dans un document chacune (la limite est de 1 Mo). Elles restent lisibles hors ligne, comme le reste. Les mêmes règles de sécurité les protègent. Et il n'y a aucun service ni aucune facturation de plus à activer.
- **L'export JSON ne contient pas les photos.**

### `events/{eventId}` (lot 4)

| Champ | Type | Description |
|---|---|---|
| `title` | texte, 1 à 80 caractères | Obligatoire |
| `date` | texte `AAAA-MM-JJ` | Obligatoire |
| `time` | texte `HH:mm` ou null | Heure facultative |
| `place` | texte (120 au plus) ou null | Lieu facultatif |
| `note` | texte (1 000 au plus) ou null | Note facultative |
| `targetType` | `self` \| `child` \| `household` \| `family` | Pour qui : moi, un enfant, un foyer, toute la famille |
| `targetMemberId` | `memberId` ou null | L'auteur pour `self`, l'enfant pour `child`, sinon null |
| `householdId` | `householdId` ou null | Le foyer pour `household`, sinon null |
| `authorId` | `memberId` | L'auteur, jamais modifié |
| `authorName` | texte | Prénom de l'auteur au moment de la création : les événements d'un membre retiré restent lisibles (PRD, cas « séparation ») |
| `createdAt`, `updatedAt` | horodatage serveur | `createdAt` jamais modifié, `updatedAt` à chaque écriture |

Le destinataire est écrit dans l'événement, pour que les règles vérifient les droits sans lire d'autres événements.

Droits (PRD, « Droits par profil ») :

- **Lire** : tous les membres.
- **Créer** :
  - l'admin principal, pour qui il veut ;
  - un adulte (chef de famille compris), pour lui, pour un enfant dont il est adulte responsable, pour son foyer ou pour toute la famille ;
  - un enfant avec l'appli, pour lui seulement.
- **Modifier, supprimer** :
  - l'admin principal, tous les événements ;
  - chacun, les siens ;
  - un chef de famille, ceux dont l'auteur est un membre de son foyer, même pour toute la famille ;
  - un adulte, aussi ceux de ses enfants, écrits par eux ou pour eux.
  - Un nouveau destinataire doit être un de ceux que la personne a le droit de choisir.
- Un membre retiré : ses événements restent visibles, et seul l'admin principal peut encore les modifier ou les supprimer.

### `events/{eventId}/rsvps/{memberId}` (lot 6)

Une réponse par membre et par événement (PRD, récit 4). L'identifiant du document est celui du membre concerné, donc il ne peut y avoir qu'une réponse par personne.

| Champ | Type | Description |
|---|---|---|
| `status` | `yes` \| `maybe` \| `no` | « Je viens », « Peut-être », « Je ne viens pas » |
| `answeredBy` | `memberId` | Qui a répondu : le membre lui-même ou un de ses adultes responsables |
| `updatedAt` | horodatage serveur | À chaque réponse |

Droits :

- **Lire** : tous les membres.
- **Répondre, changer ou retirer sa réponse** : le membre lui-même, ou un adulte responsable s'il s'agit d'un enfant. Personne d'autre, pas même l'admin principal, ne répond à la place de quelqu'un.
- L'événement et le membre doivent exister.
- Quand un événement est supprimé, la Cloud Function `onEventDeleted` efface ses réponses et prévient la famille. Quand un membre est retiré, `onMemberDeleted` efface ses réponses à tous les événements.


### `events/{eventId}/photos/{authorId}_{slot}` (récit 41)

L'album d'un événement, caché derrière le module « Albums des événements » (`eventAlbums`). Il a la même forme que les photos d'un voyage (`authorId`, `authorName`, `slot` de 1 à 30, `createdAt`), et les fichiers vont dans Storage à `events/{eventId}/{authorId}/{slot}.jpg` et `{slot}-thumb.jpg`.

Droits : tous les membres voient l'album, comme l'événement. Un membre qui a répondu « Je viens » ajoute ses photos à partir du jour de l'événement (minuit en France : 22 h UTC la veille en été, 23 h en hiver ; les règles ouvrent toujours à 22 h UTC, donc en hiver une heure avant l'appli, sans danger). L'auteur de la photo, l'auteur de l'événement et l'admin principal la retirent. La suppression de l'événement emporte l'album et ses fichiers (`onEventDeleted`). Tests : `firebase/tests/eventAlbum.test.mjs` et `firebase/tests/storage.test.mjs`.

Le lendemain de l'événement, ceux qui ont répondu « Je viens » et n'ont pas encore mis de photo reçoivent une invitation douce, une ligne du résumé du jour (« Tes photos d'hier à mettre dans l'album »), jamais une notification à part. Il faut les modules `eventAlbums` et `digest`, et le résumé ne doit pas être coupé dans les réglages (`weekly`). Un enfant sans appli n'en reçoit pas. La tâche des 30 minutes (`queueAlbumInvitations`, `functions/src/albumInvites.ts`) le fait une fois par jour, grâce à la marque `albumInvites/{jour}` que seul le serveur écrit. Tests : `functions/test/albumInvites.test.ts`.

### `members/{memberId}/devices/{token}` (lot 8)

Les téléphones d'un membre, pour les notifications. L'identifiant du document est le jeton de notification (FCM) du téléphone, donc un même téléphone n'est enregistré qu'une fois.

| Champ | Type | Description |
|---|---|---|
| `updatedAt` | horodatage serveur | Dernière fois que l'appli a confirmé ce jeton |

Droits : seul le membre lui-même lit, ajoute ou supprime ses téléphones. Les Cloud Functions les lisent avec leur accès serveur, et suppriment le jeton d'une appli désinstallée.

### `members/{memberId}/settings/notifications` (lot 8)

| Champ | Type | Description |
|---|---|---|
| `dayBeforeReminder` | booléen | `false` coupe le rappel de la veille (PRD, récit 6 bis). Sans document, le rappel est actif. |
| `goodNews` | booléen, facultatif | `false` coupe les notifications des Bonnes nouvelles (récit 32). Sans valeur, elles sont actives. Ne coupe pas l'avis aux adultes responsables quand un enfant publie (carte AVIS-12). |
| `recap` | booléen, facultatif | `false` coupe « La semaine de la famille », le récap du dimanche à 18 h (récit 36). Sans valeur, il est actif. |
| `birthdayWishes` | booléen, facultatif | `false` coupe les avis des vœux d'anniversaire (récit 36) : « C'est l'anniversaire de … » et, pour soi, les cartes reçues. Sans valeur, ils sont actifs. |
| `souvenirs` | booléen, facultatif | `false` coupe l'avis « Nouveau souvenir » (récit 34). Sans valeur, il est actif. |
| `tree` | booléen, facultatif | `false` coupe l'avis « Défi réussi » (récit 35). Sans valeur, il est actif. |
| `weekly` | booléen, facultatif | `false` coupe l'avis de la question de la semaine (récit 37), et plus tard les autres rendez-vous de la semaine. Sans valeur, il est actif. |

Droits : seul le membre lui-même lit et modifie ses réglages. L'appli écrit chaque réglage en fusion, pour ne pas effacer l'autre.

### `weeklyRecaps/{AAAA-MM-JJ}` (récit 36)

Un document par dimanche, posé par la tâche de 18 h quand elle envoie « La semaine de la famille » : il garantit un seul envoi par semaine (`sentAt`, `expireAt` à 40 jours, effacé par le TTL de Firestore). Aucune règle ne l'ouvre.

Le dimanche, si le module `recap` est allumé, chaque membre qui a l'appli et n'a pas coupé `recap` reçoit une notification (canal `recap`, discret, donnée `recapWeek`) qui résume la semaine : les photos des Bonnes nouvelles des 7 derniers jours, les anniversaires des 14 prochains jours, la jauge du défi (si le module `tree` est allumé) et les voyages en cours que ce membre peut voir (`readerIds` : un voyage surprise reste caché). Sans nouvelle, le texte reste simple : « Les nouvelles de la famille, en un coup d'œil. » Au toucher, l'écran « La semaine de la famille » (`feature/recap`) relit lui-même ce que le membre peut voir : les 3 photos de la semaine (les plus aimées d'abord, sans compte ni nom, sans les Bonnes nouvelles qu'il a masquées), les anniversaires des 14 prochains jours, la jauge du défi et les voyages en cours qu'il suit. Chaque partie suit son module (`goodNews`, `trips`, `tree`).

### `birthdayCards/{AAAA}-{forMemberId}-{authorId}` (récit 36, vœux d'anniversaire)

Une carte de vœux par membre et par anniversaire, derrière le module `birthdayWishes`.

| Champ | Type | Description |
|---|---|---|
| `forMemberId` | id de membre | La personne fêtée. Jamais soi-même. |
| `authorId`, `authorName` | id, texte | Qui écrit, signé de son prénom. |
| `text` | texte, 1 à 280 caractères | Le mot de la carte. |
| `year` | entier | L'année de l'anniversaire. |
| `createdAt`, `updatedAt` | horodatages serveur | |

Droits : toute la famille lit les cartes (la fiche de la personne, la Bonne nouvelle du jour). Un membre écrit la sienne seulement le jour J, c'est-à-dire dans les 24 h qui suivent la Bonne nouvelle `goodNews/birthday-{AAAA}-{forMemberId}`. L'auteur change le texte ou retire sa carte ; personne d'autre n'y touche. L'appli ne montre jamais combien il y en a, ni qui n'en a pas envoyé, et il n'y a aucune relance (avis EXPERT n° 11).

Le jour J, la tâche de 10 h (`birthdayGiftReminders`) publie la Bonne nouvelle de Familia (`birthdayOf`, sans photo, `authorId` = `familia`), une seule fois, puis prévient la famille qui a l'appli, sauf la personne et ceux qui ont coupé `birthdayWishes` : « C'est l'anniversaire de Camille », « Envoie-lui tes vœux. » (canal `birthdays`, donnée `birthdayOf`). Chaque carte prévient la personne fêtée (`onBirthdayCardWritten`) avec les prénoms de tous ceux qui ont écrit ce jour-là, jamais un nombre ; les avis du jour portent la même étiquette (`birthdayCards-{AAAA}`) et se remplacent sur le téléphone. Quand un membre part, ses cartes écrites et reçues sont effacées.

« Il y a un an » (récit 36, module `yearAgo`) : une carte de l'Accueil, sans notification ni donnée nouvelle. L'appli lit les Bonnes nouvelles publiées le même jour un an plus tôt (`createdAt` entre minuit et minuit, heure de Paris) et, sinon, les photos d'album (`trips/{tripId}/photos`) prises ce jour-là dans les voyages que le membre suit (`readerIds`). Une Bonne nouvelle qu'il a masquée, un « Défi réussi » de Familia et un souvenir qu'il a écarté ne s'affichent pas. « Ne plus me montrer ce souvenir » est gardé sur le téléphone (préférences `yearAgoDismissed-{memberId}`), rien n'est écrit dans Firestore ; il n'est pas proposé en « Voir comme ».

## Cloud Functions (lot 8)

Le code est dans `firebase/functions`, en TypeScript, et tourne en Europe (`europe-west1`), avec une seule instance au plus.

| Fonction | Quand | Ce qu'elle fait |
|---|---|---|
| `onEventCreated` | Un événement est ajouté | Prévient les membres qui ont l'appli et que l'événement concerne, sauf l'auteur (PRD, récit 6, carte AVIS-7) |
| `onEventUpdated` | Un événement est modifié | Idem, plus ceux qui ont répondu « Je viens » ou « Peut-être », seulement si un champ visible a changé |
| `onEventDeleted` | Un événement est supprimé | Prévient comme une modification, puis efface ses réponses |
| `onMemberDeleted` | Un membre est retiré | Efface ses réponses, ses téléphones et ses réglages |
| `dayBeforeReminder` | Chaque jour à 18 h, heure de Paris | Rappelle l'événement du lendemain à ceux qui ont répondu « Je viens » ou « Peut-être ». Un enfant sans l'appli est rappelé à ses adultes responsables (PRD, récit 6 bis). |

Un événement déjà passé ne prévient personne. L'auteur d'un changement est reconnu par son compte Firebase.

**Qui un événement concerne** (cartes AVIS-7 et AVIS-8) : une seule fonction le dit, `concerns` dans `functions/src/logic.ts`, et la même règle dans l'appli (`core/model/Audience.kt`), qui sert au filtre « Mon foyer » du planning. Un événement concerne un membre s'il est pour toute la famille, ou si sa cible est lui-même, son foyer, un membre ou un enfant de son foyer, ou un enfant dont il est adulte responsable (même dans un autre foyer). Un futur « cercle » ne changera que cette fonction.

**Canaux Android** (récit 6 quinquies) : chaque notification part sur un canal, que chacun peut couper dans les réglages Android : `events` (affiché « Planning »), `reminders`, `gifts`, `helpRequests` (Entraide ; le rappel de la veille d'un coup de main reste sur `reminders`), `goodNews` (Bonnes nouvelles), `trips` (Voyages : « bien arrivé » et son rappel), `comments` (Commentaires et réactions : pour l'instant les réactions groupées aux Bonnes nouvelles). Chaque module ajoute le sien en même temps que lui : `weekly` (Rendez-vous de la semaine). Les identifiants ne changent jamais, car Android y garde le choix du membre. Le canal d'un module n'est créé qu'une fois le module visible (`Feature`), pour ne pas montrer de catégorie vide dans les réglages. Un canal inconnu de l'appli retombe sur « Planning ».

Tests : `cd firebase && npm run test:functions` (la logique, et les fonctions contre l'émulateur Firestore, avec un faux envoi de notifications).

## Export JSON (lot 9)

L'admin principal crée une sauvegarde depuis Famille, puis « Administration » et « Sauvegarde des données » (PRD, récit 6 ter). Le sélecteur de fichiers d'Android lui laisse choisir le téléphone ou Google Drive. Le fichier `familia-sauvegarde-AAAA-MM-JJ.json` contient :

| Clé | Contenu |
|---|---|
| `app`, `formatVersion`, `appVersion`, `exportedAt` | « Familia », la version du format (1), la version de l'appli, l'heure de l'export (ISO 8601, UTC) |
| `households`, `members`, `events` | Les documents tels qu'ils sont stockés, avec leur `id` |
| `rsvps` | Les réponses, avec `eventId` et `memberId` |
| `helpRequests` | Les demandes de coup de main, avec leur `id` |
| `weeklyQuestions`, `weeklyAnswers` | Les questions de la semaine, et leurs réponses avec `questionId` et `memberId`. Les réponses d'une question n'y sont qu'à partir de son `revealAt` (48 h après sa publication), comme le veulent les règles |
| `capsules` | Les capsules temporelles que l'admin principal peut lire : celles qu'il a écrites et celles déjà ouvertes pour lui (PRD rév. 184). Les autres restent scellées, même dans la sauvegarde |
| `trips`, `tripSteps`, `tripItems` | Les voyages que l'admin principal suit (`readerIds`), avec leurs étapes et leur liste « à emporter » ; chaque étape et chaque objet porte son `tripId`. Les photos de voyage n'y sont pas, comme celles des albums |

Les données sont lues sur le serveur, jamais dans le cache du téléphone. Hors ligne, aucune sauvegarde n'est faite et l'appli demande de se connecter : une sauvegarde est complète ou n'existe pas. L'appli lit les données avant d'ouvrir le sélecteur, et si l'écriture échoue, elle supprime le fichier créé. Les documents sont triés par identifiant, donc deux exports des mêmes données donnent le même fichier. Ne sont pas exportés : `memberEmails`, qui se reconstruit à partir des membres, et les téléphones et réglages de chacun, que seul le membre concerné peut lire. À partir de la V2, les cadeaux s'ajouteront au fichier, avec un `formatVersion` augmenté si sa forme change. Ajouter une clé ne change pas la forme : `formatVersion` reste à 1.

### `config/app` (lot 10)

| Champ | Type | Description |
|---|---|---|
| `latestVersionCode` | nombre | Le `versionCode` de la dernière version publiée |
| `latestVersionName` | texte | Son nom affiché, par exemple « 0.2.0 » |
| `apkFileId` | texte | L'identifiant Drive de l'APK, pour le télécharger depuis l'appli (récit 6 quater) |
| `apkSize` | nombre | Sa taille en octets, vérifiée après le téléchargement |
| `apkSha256` | texte | Son empreinte SHA-256 (64 caractères hexadécimaux), vérifiée avant l'installation |
| `features` | map texte → booléen | Les modules allumés pour la famille (`gifts`, `helpRequests`, `trips`, `home`…). Un module absent ou à `false` reste caché, sauf pour l'admin principal |

Le lien du dossier Drive n'est pas dans ce document : il est fixé dans l'appli (`BuildConfig.DOWNLOAD_URL`). Si un champ de l'APK manque ou paraît faux, l'appli ouvre ce dossier comme avant.

Droits : tous les membres le lisent. Le workflow « APK de publication » écrit les champs de version et d'APK. Depuis l'appli, seul l'admin principal modifie `features`, et rien d'autre (menu Administration, carte « Modules »). Voir [mise-a-jour.md](mise-a-jour.md).

### `config/family` (carte DEV-8)

| Champ | Type | Description |
|---|---|---|
| `ownerMemberId` | texte | Le membre qui est admin principal |

Droits : tous les membres le lisent, et personne ne l'écrit depuis l'appli. Le workflow « Créer le premier admin » l'écrit avec le compte de service. Si le document manque ou ne peut pas être lu, personne n'est admin principal : l'appli reste utilisable, et les chefs gardent leurs droits sur leur foyer.

## Listes et cadeaux (V2)

PRD, récits 7 à 10 bis. **La surprise est gardée par le serveur** : les règles refusent au bénéficiaire, même en dehors de l'appli, la lecture des listes surprises faites pour lui, des articles que d'autres ajoutent à ses listes, des réservations et des parts. Ce qui est caché vit donc dans des documents à part, jamais dans un champ d'un document qu'il peut lire. Tests : `firebase/tests/gifts.test.mjs`.

### `giftLists/{listId}`

| Champ | Type | Description |
|---|---|---|
| `kind` | `wishes` \| `own` \| `surprise` | « Mes envies », ma liste d'occasion, ou une liste surprise pour un autre membre |
| `title` | texte, 1 à 60 caractères | « Noël 2026 », « Anniversaire de … » |
| `occasion` | `christmas` \| `birthday` \| `birth` \| `other`, ou null | Null pour « Mes envies » |
| `date` | texte `AAAA-MM-JJ` ou null | Date de l'occasion. Null pour « Mes envies » |
| `beneficiaryId` | `memberId` | Celui qui reçoit les cadeaux |
| `viewerIds` | liste de `memberId` (40 au plus) | Ceux qui voient la liste. Vide pour « Mes envies », que toute la famille voit |
| `creatorId`, `creatorName` | `memberId`, texte | Qui a créé la liste, jamais modifiés |
| `revealed` | booléen | Faux à la création. Passé à vrai par la Cloud Function le lendemain de l'occasion : la liste est alors archivée et le bénéficiaire découvre tout |
| `createdAt`, `updatedAt` | horodatage serveur | |

- « Mes envies » d'un membre a l'identifiant `wishes-{memberId}`, il n'y en a donc qu'une. Elle est créée à la première envie ajoutée, et ne se supprime pas.
- Une liste `own` liste toujours son bénéficiaire et les adultes responsables d'un enfant parmi les `viewerIds`. Une liste `surprise` ne liste jamais son bénéficiaire, et son créateur n'en est pas le bénéficiaire.
- **Qui tient les listes d'un membre** : le membre lui-même, ou les adultes responsables d'un enfant sans l'appli (PRD, récit 7). Un enfant sans l'appli ne se connecte jamais, donc rien ne lui est caché côté serveur.
- **Lire** : toute la famille pour « Mes envies » ; les `viewerIds` pour les autres ; le bénéficiaire d'une liste surprise une fois `revealed` vrai.
- **Créer** : une liste `own` ou « Mes envies » pour un membre dont on tient les listes ; une liste `surprise` pour n'importe quel autre membre.
- **Modifier** (titre, occasion, date, `viewerIds`) et **supprimer** : le créateur, et pour une liste `own` ou « Mes envies », celui qui tient les listes du bénéficiaire. Une liste révélée ne se modifie plus.

Requêtes de l'appli, toutes acceptées par les règles : `viewerIds array-contains moi`, `kind == 'wishes'`, et `beneficiaryId == moi` avec `revealed == true` pour retrouver les surprises révélées.

### `giftLists/{listId}/items/{itemId}`

| Champ | Type | Description |
|---|---|---|
| `title` | texte, 1 à 80 caractères | |
| `url` | texte `http(s)://` (500 au plus) ou null | Lien facultatif |
| `price` | nombre de 0 à 100 000 ou null | Prix indicatif, en euros |
| `note` | texte (500 au plus) ou null | Taille, couleur… |
| `imageUrl` | texte `https://` (1 000 au plus), null ou absent | Facultatif : image lue dans l'aperçu d'un lien partagé (carte PRODUIT-34). Familia ne stocke aucune image |
| `authorId`, `authorName` | `memberId`, texte | Qui l'a ajouté, jamais modifiés |
| `visibleToBeneficiary` | booléen | Vrai seulement si l'auteur tient les listes du bénéficiaire et que la liste n'est pas une surprise. Les règles l'imposent |
| `createdAt`, `updatedAt` | horodatage serveur | |

- **Lire** : ceux qui voient la liste. Le bénéficiaire ne lit que `visibleToBeneficiary == true` (l'appli filtre sa requête ainsi) jusqu'à la révélation.
- **Ajouter** : ceux qui voient la liste, tant qu'elle n'est pas révélée.
- **Modifier, supprimer** : l'auteur, tant que l'article n'est pas réservé et que la liste n'est pas révélée. Exception : le côté du bénéficiaire peut toujours modifier ou retirer ses propres articles, car un refus lui apprendrait que l'article est réservé. La Cloud Function prévient alors le réservant (PR des fonctions).

### `giftLists/{listId}/claims/{itemId}`

La réservation d'un article. Son identifiant est celui de l'article, donc un article n'est réservé qu'une fois.

| Champ | Type | Description |
|---|---|---|
| `type` | `solo` \| `shared` | « Je le prends » ou cadeau commun |
| `holderId`, `holderName` | `memberId`, texte | Le réservant, ou le responsable du cadeau commun |
| `createdAt` | horodatage serveur | |
| `sharesTotal`, `sharesCount` | nombre, entier | Cadeau commun : total et nombre des parts, tenus par une Cloud Function (absents tant qu'aucune part n'a changé) |
| `occasion` | `birthday` \| `christmas`, facultatif | « Mes envies » seulement (PRD rév. 186) : le prochain anniversaire ou le prochain Noël du bénéficiaire après la réservation, choisi par le réservant depuis son carnet. Absent, c'est celui des deux qui vient en premier. L'envie est dévoilée le lendemain. Les parts d'un cadeau commun suivent le choix du responsable |

- **Lire** : ceux qui voient la liste, sauf le bénéficiaire avant la révélation.
- **Réserver** : ceux qui voient la liste, sauf le bénéficiaire. Un enfant réserve seul, jamais en cadeau commun (carte PRODUIT-19).
- **Modifier** : le réservant, seulement `occasion`, sur « Mes envies ». Une Cloud Function (`onGiftClaimUpdated`) remet alors à jour le carnet du réservant et des participants.
- **Relâcher** : le réservant seulement, tant que la liste n'est pas révélée. Les parts d'un cadeau commun relâché sont effacées par une Cloud Function.

### `giftLists/{listId}/claims/{itemId}/shares/{memberId}`

Une part d'un cadeau commun, une par adulte. Aucun paiement ne passe par l'appli.

| Champ | Type | Description |
|---|---|---|
| `amount` | nombre de 0 (exclu) à 10 000 | Part déclarée, en euros |
| `memberName` | texte | Prénom de celui qui participe |
| `received` | booléen | Faux à la création. Le responsable le passe à vrai quand il a reçu la part |
| `updatedAt` | horodatage serveur | |

- **Lire** : la part elle-même, par son auteur ; toutes les parts, par le responsable du cadeau commun. Personne d'autre, même après la révélation (PRD rév. 151) : les autres participants voient le total sur la réservation.
- **Rejoindre** : un adulte qui voit la liste, pour lui-même, sur un cadeau commun.
- **Modifier** : le membre change son montant ; le responsable marque la part reçue ou non. Personne d'autre.
- **Retirer** : le membre ou le responsable.

### `giftLists/{listId}/received/{itemId}` (« On m'a offert », PRD, récit 10 bis)

Une envie offerte, sortie de « Mes envies » le lendemain de l'occasion de son bénéficiaire. Seule la liste `wishes-{memberId}` en a. Derrière l'interrupteur `config/app.features.wishesReveal`, en plus de `features.gifts`.

| Champ | Type | Description |
|---|---|---|
| `title`, `url`, `imageUrl` | texte | Repris de l'article |
| `givers` | liste de textes | Prénoms de ceux qui l'ont offerte : le responsable, puis les participants. Jamais les montants |
| `giverIds` | liste de `memberId` | Les mêmes donateurs, pour leur envoyer le « Merci » (PRD rév. 189) |
| `joint` | booléen | Vrai pour un cadeau commun |
| `occasion` | `birthday` ou `christmas` | L'occasion de la réservation |
| `year` | nombre | Année de l'occasion |
| `thanked` | booléen | Faux à la création, vrai quand le bénéficiaire a dit merci |
| `thankedAt` | horodatage serveur | Présent une fois le merci dit |
| `revealedAt` | horodatage serveur | |

- **Lire** : le bénéficiaire, et les adultes qui gardent ses listes (un enfant sans l'appli n'a rien de dévoilé).
- **Modifier** : le bénéficiaire, seulement pour passer `thanked` à vrai avec `thankedAt`.
- **Créer, supprimer** : le serveur seul.

### Cloud Functions des cadeaux (`firebase/functions/src/gifts.ts`)

| Fonction | Quand | Ce qu'elle fait |
|---|---|---|
| `revealGifts` | Chaque jour à 7 h, heure de Paris | Passe `revealed` à vrai pour les listes dont l'occasion est passée (pas « Mes envies »), et prévient leur bénéficiaire s'il a l'appli (PRD, récit 10 bis) |
| `revealGifts`, ensuite (`wishesReveal.ts`) | Chaque jour à 7 h, heure de Paris, après les listes | Passe dans `received` chaque envie réservée dont l'occasion (`occasion` de la réservation) était la veille ou avant, efface l'article et sa réservation, et prévient le bénéficiaire. Le carnet des donateurs garde le cadeau, et personne ne reçoit « Réservation annulée ». Une envie non réservée reste |
| `birthdayGiftReminders` | Chaque jour à 10 h, heure de Paris | À J-21 d'un anniversaire, prévient tous les adultes sauf le fêté, avec ses envies. À J-14, recommence si aucun article de ses listes n'est réservé (récit 10) |
| `onGiftItemWritten` | Un article est modifié ou supprimé | S'il était réservé, prévient le réservant et les participants (jamais le bénéficiaire), et met à jour leur carnet. Une suppression efface aussi la réservation et ses parts |
| `onGiftShareWritten` | Une part est ajoutée, modifiée ou retirée | Recalcule `sharesTotal` et `sharesCount` sur la réservation, et met à jour le carnet du participant |
| `onGiftClaimCreated` | Une réservation est faite | La reprend dans le carnet de son auteur |
| `onGiftClaimDeleted` | Une réservation est relâchée | Efface les parts du cadeau commun, et la retire du carnet de son auteur |
| `onReceivedGiftUpdated` | Le bénéficiaire dit « Merci » dans « On m'a offert » | Prévient chaque donateur, une seule fois : « X te dit merci ! » (canal gifts, PRD rév. 189) |
| `onGiftListDeleted` | Une liste est supprimée | Efface ses articles, réservations, parts et cadeaux reçus |
| `onMemberDeletedGifts` | Un membre est retiré | Efface les listes dont il est le bénéficiaire, et son carnet |

Une notification des cadeaux porte `listId` dans ses données à la place de `eventId`. Le bénéficiaire ne reçoit jamais de notification sur ses cadeaux, sauf celle de la révélation.

Tant que le module n'est pas allumé pour la famille (`config/app.features.gifts` différent de `true`), ces fonctions ne préviennent que l'admin principal (`config/family.ownerMemberId`), le seul à qui l'appli montre déjà le module. La révélation et le nettoyage se font quand même.

Limite connue : les règles ne vérifient pas que chaque identifiant de `viewerIds` est un membre (cela coûterait une lecture par personne). L'appli ne propose que des membres de la famille.

### `notebooks/{memberId}/entries/{entryId}` (récit 9 bis, « Mon carnet de cadeaux »)

Ce que le membre prévoit d'offrir. Seul ce membre lit et écrit son carnet : ni la personne qui recevra, ni l'admin principal, ni quelqu'un qui agit pour lui. Le carnet n'est pas dans l'export.

| Champ | Type | Description |
|---|---|---|
| `source` | `idea`, `claim` ou `share` | Une idée privée, une réservation « Je le prends », ou une part d'un cadeau commun (ou un cadeau commun dont le membre est responsable) |
| `listId`, `itemId` | texte | Seulement pour `claim` et `share` : l'article repris. L'identifiant est alors `gift_{listId}_{itemId}` |
| `personId` | texte ou null | Le membre à qui offrir, ou null pour une personne hors famille |
| `personName` | texte de 1 à 40 caractères | Son prénom |
| `occasion`, `year` | comme une liste, et entier de 2020 à 2100, ou null | L'occasion. Celle d'une liste reprise vient de la liste ; pour « Mes envies », le membre la choisit |
| `title`, `url`, `price` | comme un article | Le prix d'une réservation est celui de l'article ; celui d'une part, le montant du membre |
| `note` | texte de 500 caractères au plus, ou null | Pour soi |
| `status` | `idea`, `bought`, `wrapped` ou `given` | Idée, Acheté, Emballé, Offert |
| `createdAt`, `updatedAt` | horodatage serveur | |

- **Idée privée** : le membre la crée, la modifie et la retire. Elle ne réserve rien.
- **Réservation ou part reprise** : écrite par la fonction, qui garde le titre, le lien, le prix et la personne à jour. Le membre ne change que `status`, `occasion`, `year` et `note`, et ne la retire pas : elle part quand la réservation ou la part est retirée.

### `notebooks/{memberId}/budgets/{budgetId}`

Ce que le membre compte dépenser pour une personne et une occasion : `personId` (ou null), `personName`, `occasion`, `year`, `amount` (de 0 exclu à 100 000 euros), `updatedAt`. Seul le membre le lit et l'écrit.

### `events/{eventId}/bring/{itemId}` (« Qui apporte quoi », PRD, récit 4 bis)

Une chose à apporter à l'événement. Visible dans l'événement une fois le module `bring` allumé.

| Champ | Type | Description |
|---|---|---|
| `label` | texte (1 à 60 caractères) | Ce qu'il faut apporter. Ne change plus : une ligne fausse se retire |
| `authorId` | `memberId` | Qui a ajouté la ligne |
| `takerId`, `takerName` | `memberId` et texte, ou null | Qui s'en charge, et son prénom à ce moment-là ; null tant que la ligne est libre |
| `createdAt`, `updatedAt` | horodatage serveur | |

Droits : tous les membres avec l'appli (enfants compris) lisent, ajoutent une ligne (libre ou prise par eux) et prennent une ligne libre. Une ligne prise se libère par celui qui l'a prise, l'auteur de la ligne, ou ceux qui gèrent l'événement (« Modifier »). La ligne se retire par son auteur ou ceux qui gèrent l'événement.

Serveur : le rappel de la veille (`dayBeforeReminder`) ajoute « Tu apportes : … » quand le module est allumé, et prévient aussi celui qui a pris une ligne sans avoir répondu « Je viens » (pas s'il a dit non). La liste part avec l'événement ; ce qu'un membre retiré avait pris redevient libre (`releaseBringOf`, index `bring.takerId` en groupe de collections).

### `events/{eventId}/comments/{commentId}` (commentaires, PRD, récit 28)

Un commentaire sous un événement. Visible dans l'événement une fois le module `comments` allumé.

| Champ | Type | Description |
|---|---|---|
| `text` | texte (1 à 500 caractères) | Le commentaire |
| `authorId`, `authorName` | `memberId` et texte | Qui l'a écrit, et son prénom à ce moment-là (le commentaire reste signé après son départ) |
| `createdAt` | horodatage serveur | |
| `expireAt` | horodatage | 365 jours après l'écriture, posé par le téléphone (les règles acceptent 360 à 370 jours). Firestore efface le commentaire à cette date (TTL, groupe de collections `comments`) |
| `editedAt` | horodatage serveur, absent tant que le texte n'a pas changé | Affiche « modifié » |

Droits : tous les membres lisent et écrivent les leurs. Seul l'auteur change le texte (et rien d'autre). L'auteur, l'admin principal et un chef du foyer de l'auteur retirent un commentaire. L'appli montre les 30 plus récents, puis 30 de plus à chaque « Voir les commentaires plus anciens ».

### `events/{eventId}/commentMutes/{memberId}`

« Ne plus être prévenu des commentaires » de cet événement : un seul champ `mutedAt` (horodatage serveur). Seul ce membre le lit, le crée et le retire.

Serveur (`firebase/functions/src/comments.ts`) : un nouveau commentaire prévient l'auteur de l'événement, ceux qui ont déjà commenté et ceux qui ont répondu « Je viens » ou « Peut-être » (les parents pour un enfant sans appli), jamais celui qui écrit ni ceux qui ont coupé, sur le canal `comments`. Tant que le module est éteint, seul l'admin principal est prévenu. Les commentaires et les « ne plus être prévenu » partent avec l'événement. Pour un membre retiré, ses « ne plus être prévenu » partent, ses commentaires restent jusqu'à leur expiration.

## Bonnes nouvelles (PRD, récit 32)

Une Bonne nouvelle a un texte court, une à trois photos et peut nommer des membres. Toute la famille la voit (quand les cercles de la rév. 124 arriveront, la vérification d'audience ira dans la règle de lecture et dans le dossier Storage).

### `goodNews/{postId}`

| Champ | Type | Description |
|---|---|---|
| `text` | texte, 1 à 500 caractères | Obligatoire, pas seulement des espaces |
| `photoIds` | liste de 1 à 3 identifiants (`[A-Za-z0-9_]`, 40 au plus) | Le nom des photos dans Storage, dans l'ordre d'affichage |
| `authorId`, `authorName` | `memberId`, texte | Qui a publié, et son prénom à ce moment-là (l'auteur peut quitter la famille) |
| `forMemberId` | `memberId` | De qui c'est la nouvelle : l'auteur, ou pour un adulte un enfant de son foyer |
| `householdId` | `householdId` | Le foyer de `forMemberId`, pour la modération des chefs de famille |
| `mentionIds` | liste de `memberId` (20 au plus) | Les membres nommés |
| `reactions` | table `memberId` → `heart` \| `clap` \| `party` \| `laugh` \| `wow` \| `thumbsUp` | Une réaction par membre (❤️ 👏 🎉 😂 😮 👍), rangée par nom |
| `hiddenByIds` | liste de `memberId` (21 au plus) | Les membres concernés qui ont choisi « Masquer pour moi » (PRD rév. 158) : ils ne la voient plus dans le fil ni sur l'Accueil, les autres si |
| `createdAt`, `updatedAt` | horodatage serveur | `updatedAt` change quand l'auteur modifie, pas pour une réaction |
| `forChallenge` | `AAAA-MM`, facultatif | Compte pour le défi lancé par un foyer ce mois-là (récit 35 bis). Fixé à la publication, l'auteur ne le change pas en modifiant |

Le fil se lit par `orderBy('createdAt', 'desc')` et pages de 20 ; la carte d'accueil prend les 3 dernières des 14 derniers jours (même requête, filtrée par l'appli).

Photos dans Storage : `goodNews/{authorId}/{postId}/{photoId}.jpg` (1600 px au plus, environ 300 Ko, 512 Ko au plus) et `{photoId}-thumb.jpg` (400 px, environ 30 Ko, 64 Ko au plus). L'appli envoie les photos avant d'écrire le document.

Droits :

- **Lire** : tous les membres, photos comprises.
- **Publier** : un membre avec l'appli, pour lui (un enfant aussi, sans validation, carte AVIS-12) ; un adulte, pour un enfant de son foyer. Le document naît sans réaction.
- **Réagir** : chacun pose, change ou retire sa propre réaction, rien d'autre.
- **Masquer pour moi** : le membre concerné (`forMemberId`) ou un membre nommé, ado compris, ajoute ou retire son propre identifiant dans `hiddenByIds`, rien d'autre. Le document naît sans personne qui le masque.
- **Modifier** : l'auteur seul, pour le texte, les photos et les membres nommés.
- **Retirer** : l'auteur, les adultes responsables de l'auteur ou de l'enfant concerné (carte AVIS-12), un chef de famille de `householdId` et l'admin principal.
- **Photos** : chacun envoie seulement dans son dossier, un JPEG de la bonne taille, jamais en remplacement d'une photo existante. L'auteur peut les supprimer ; pour un retrait par quelqu'un d'autre, c'est la Cloud Function qui efface les photos.
- Limite connue : comme pour les coups de main, les identifiants de `mentionIds` ne sont pas vérifiés un par un ; l'appli ne propose que des membres.

Cloud Functions (`functions/src/goodNews.ts`) :

| Fonction | Déclencheur | Rôle |
|---|---|---|
| `onGoodNewsCreated` | création | Prévient toute la famille qui a l'appli, sauf l'auteur et ceux qui ont coupé `goodNews`. Quand un enfant publie, ses adultes responsables reçoivent « Camille a publié une Bonne nouvelle », même s'ils ont coupé les autres (carte AVIS-12). Canal `goodNews`, donnée `goodNewsId`. |
| `onGoodNewsUpdated` | modification | Efface dans Storage les photos que l'auteur a retirées. À la première nouvelle réaction, note dans `goodNewsDigests/{postId}` l'envoi groupé prévu une heure plus tard. |
| `goodNewsReactionDigests` | toutes les 15 min | Envoie à l'auteur les avis groupés arrivés à échéance : « Inès et 3 autres ont réagi à ta Bonne nouvelle » (PRD rév. 174). Chaque personne n'est nommée qu'une fois. Canal `comments`, hors résumé du jour. |
| `onGoodNewsDeleted` | suppression | Efface toutes les photos, quel que soit l'auteur du retrait, et l'avis groupé en attente. |

`goodNewsDigests/{postId}` (fonctions seules, aucune règle ne l'ouvre à l'appli) : `authorId`, `dueAt` (horodatage, absent quand rien n'attend) et `notifiedIds` (ceux déjà nommés).

Le seul endroit où se décide l'audience est `goodNewsAudience` : c'est là que se branchera la fonction des cercles.

## Coups de main (PRD, récits 13 à 18)

Spec : « Spec Familia : coups de main » (tenue par [PRODUIT]), avec les cartes PRODUIT-8, PRODUIT-22 et PRODUIT-23.

### `helpRequests/{requestId}`

| Champ | Type | Description |
|---|---|---|
| `kind` | `childcare` \| `ride` \| `other` | Garde, trajet, autre |
| `title` | texte, 1 à 80 caractères | Obligatoire |
| `date` | texte `AAAA-MM-JJ` | Obligatoire, pas dans le passé à la création ni quand on la change |
| `startTime`, `endTime` | texte `HH:mm` ou null | Facultatives ; la fin après le début |
| `place` | texte (120 au plus) ou null | Lieu facultatif |
| `note` | texte (1 000 au plus) ou null | Note facultative |
| `authorId`, `authorName` | `memberId`, texte | Le demandeur et son prénom au moment de la demande, jamais modifiés |
| `forMemberId` | `memberId` | Le membre aidé : le demandeur, ou un de ses enfants pour un adulte |
| `notifyIds` | liste de `memberId`, 1 à 30 | Les personnes prévenues, jamais le demandeur. Pour un enfant, ses adultes responsables y sont toujours (sauf s'il est lui-même le demandeur) |
| `viewerIds` | liste de `memberId` | Toujours `[authorId]` suivi de `notifyIds` : sert à la requête de l'onglet (`array-contains`) |
| `status` | `open` \| `taken` \| `cancelled` | Ouverte, prise, annulée. Une demande annulée le reste |
| `volunteerId`, `volunteerName` | `memberId` et texte, ou null | Le volontaire et son prénom |
| `volunteerNote` | texte (200 max), ou null, facultatif | Le mot du volontaire à l'auteur, posé avec « Je m'en occupe » ou plus tard par lui seul, effacé quand il se retire |
| `declinedBy` | `memberId` ou null | « Je ne peux pas » de la seule personne prévenue (carte PRODUIT-22). Remis à null quand le demandeur élargit |
| `thankedAt` | horodatage serveur ou null | Le « Merci » du demandeur, une seule fois |
| `createdAt`, `updatedAt` | horodatage serveur | `createdAt` jamais modifié, `updatedAt` à chaque écriture |

« Dupliquer » (carte PRODUIT-23) crée simplement une nouvelle demande avec les mêmes personnes prévenues : il n'y a pas de demande récurrente.

Droits :

- **Lire** : le demandeur et les personnes prévenues (`viewerIds`), donc aussi les adultes responsables de l'enfant aidé (carte PRODUIT-8). L'admin principal lit toutes les demandes, pour la sauvegarde et pour « modifier toutes » ; l'onglet ne lui montre pourtant que celles où il est dans `viewerIds`. Un chef de famille n'a pas de droit en plus.
- **Créer** : un membre avec l'appli, pour lui ; un adulte, aussi pour un de ses enfants ; l'admin principal, pour n'importe qui. La demande naît ouverte, sans volontaire.
- **« Je m'en occupe »** : un adulte prévenu, jamais le demandeur, sur une demande ouverte. L'appli l'écrit dans une transaction, qui demande le réseau : de deux volontaires en même temps, seul le premier l'emporte, l'autre voit « déjà prise ».
- **Se retirer** : le volontaire seulement ; la demande redevient ouverte.
- **« Je ne peux pas »** : la seule personne prévenue, sur une demande ouverte.
- **Modifier, élargir, annuler, « Merci », supprimer** : l'admin principal, le demandeur, et un adulte responsable du demandeur ou de l'enfant aidé. Le volontaire reste parmi les personnes prévenues ; le « Merci » n'existe que pour une demande prise.
- Limite connue : les règles ne vérifient pas `notifyIds` identifiant par identifiant, ni que chaque membre existe, ni qu'il est adulte : il faudrait lire jusqu'à 30 documents, au-delà de la limite de 10. Elles vérifient seulement la taille, l'absence du demandeur et la présence des adultes responsables d'un enfant. L'appli ne propose que des adultes de la famille, et un enfant prévenu par erreur pourrait seulement lire la demande, pas la prendre.
- Les règles de mise à jour sont regroupées en une seule, dont chaque cas commence par des vérifications légères, pour rester loin de la limite de 1 000 expressions par écriture.

Écrans (onglet « Entraide », titre « Coups de main », DESIGN-4) : la liste des demandes où l'on est dans `viewerIds` (à prendre, puis déjà prises), le formulaire (type, titre, date, heures, lieu, note, pour qui, qui prévenir avec « Tout le monde » et un raccourci par foyer, dernières personnes recochées) et le détail avec « Je m'en occupe », « Je ne peux pas », « Me retirer », « Dire merci », « Dupliquer », « Modifier » et « Annuler ». Une demande passée quitte la liste le lendemain, sauf pour qui peut encore dire merci (7 jours). Le badge de l'onglet compte les demandes ouvertes que l'on peut prendre.

Cloud Functions (`functions/src/helpRequests.ts`), qui ne préviennent que les personnes concernées (AVIS-7). Un toucher sur la notification ouvre la demande (`helpRequestId`).

| Fonction | Quand | Qui est prévenu |
|---|---|---|
| `onHelpRequestWrite` | Création | Les personnes prévenues |
| | « Je m'en occupe » | Le demandeur et les adultes responsables de l'enfant aidé, sauf le volontaire |
| | Retrait du volontaire | Le demandeur, les adultes responsables et les personnes prévenues |
| | « Je ne peux pas » | Le demandeur |
| | Élargissement | Les nouvelles personnes prévenues seulement |
| | Date, heure ou lieu changés sur une demande prise | Le volontaire |
| | Annulation | Le volontaire, s'il y en a un |
| | « Merci » | Le volontaire, même après la date |
| `helpRequestStillOpen` | Chaque jour à 18 h, heure de Paris | Le demandeur d'une demande du lendemain toujours ouverte (récit 18) |
| `onMemberDeletedHelp` | Un membre est retiré | Ses demandes à venir redeviennent ouvertes, ce qui prévient le demandeur |

Une demande passée ne prévient personne, sauf pour le « Merci ». La sauvegarde de l'admin principal inclut les demandes (`helpRequests`).

## Arbre généalogique (PRD, récit 40)

L'arbre se dessine à partir des membres, des proches sans compte (`kin`) et de leurs liens (`kinLinks`). Tout membre le lit. L'admin principal le modifie en entier, et un chef de famille modifie les proches de son foyer et les liens qui touchent son foyer : un conjoint d'un côté ou de l'autre, mais un parent seulement pour un enfant de son foyer (PRD rév. 195). Il est derrière le module « Arbre généalogique » (`genealogy`), à ne pas confondre avec l'arbre des feuilles du récit 35.

### `kin/{kinId}`

Un proche sans compte : un arrière-grand-parent, un défunt, ou un parent vivant qui n'est pas dans l'appli. Il ne reçoit rien et ne compte pas parmi les membres.

| Champ | Type | Description |
|---|---|---|
| `firstName` | texte | De 1 à 40 caractères |
| `lastName` | texte, facultatif | 40 caractères au plus |
| `living` | booléen | `true` pour un proche vivant |
| `born`, `died` | texte, facultatif | Une année `AAAA` ou un jour `AAAA-MM-JJ`. `died` seulement si `living` est `false` |
| `note` | texte, facultatif | Quelques mots, 500 caractères au plus |
| `householdId` | texte | Le foyer qui l'a ajouté : ses chefs le modifient |
| `updatedBy`, `updatedAt` | texte, horodatage | Qui l'a écrit en dernier, et l'heure du serveur |

### `kinLinks/{linkId}`

Un lien entre deux personnes de l'arbre, membres ou proches. Son identifiant est `{kind}_{aId}_{bId}`, donc un même lien n'existe jamais deux fois. Un lien ne se modifie pas : on le retire, puis on le refait.

| Champ | Type | Description |
|---|---|---|
| `kind` | texte | `parent` (`aId` est un parent de `bId`) ou `partner` (deux conjoints, dans l'ordre alphabétique) |
| `aId`, `bId` | texte | Deux personnes différentes, qui existent |
| `updatedBy`, `updatedAt` | texte, horodatage | Qui l'a écrit, et l'heure du serveur |

Un proche et son premier lien partent dans le même batch. Quand un proche est supprimé, `onKinDeleted` retire ses liens. Quand un membre est retiré, `onMemberDeletedKin` retire ses liens et ses mots.

### `kinWords/{memberId}`

Les mots qu'un membre a choisis pour les personnes de l'arbre (« bonus-maman », « Mamie Jeanne »…), au lieu d'un mot imposé (avis [EXPERT] n° 14). Lui seul les lit et les écrit.

| Champ | Type | Description |
|---|---|---|
| `words` | table | Identifiant de la personne vers son mot, 60 au plus |
| `updatedAt` | horodatage | Heure du serveur |

## Capsule temporelle (PRD, récit 39)

### `capsules/{capsuleId}`

Un message pour plus tard : un texte et jusqu'à 3 photos, pour des membres choisis, scellé jusqu'à un jour donné.

| Champ | Type | Description |
|---|---|---|
| `text` | texte | Le message, de 1 à 4 000 caractères |
| `photoCount` | entier | De 0 à 3. Storage : `capsules/{authorId}/{capsuleId}/{n}.jpg` (512 Ko au plus) et `{n}-thumb.jpg` (64 Ko au plus), envoyées avant la capsule |
| `recipientIds` | liste de textes | Les destinataires, de 1 à 13, sans doublon |
| `openAt` | texte | Le jour d'ouverture, `AAAA-MM-JJ`, entre un mois et cinq ans après l'écriture |
| `openedFor` | liste de textes | Vide jusqu'à l'ouverture, puis la copie de `recipientIds`, écrite par le serveur seul |
| `openedAt` | horodatage | Ajouté par le serveur à l'ouverture |
| `authorId`, `authorName` | texte | L'auteur et son prénom |
| `createdAt` | horodatage | Heure du serveur |

Droits : jusqu'au jour dit, seul l'auteur lit la capsule. Les destinataires ne savent même pas qu'elle existe, et l'admin principal ne la lit pas non plus. Le jour venu (heure de Paris), la tâche des 30 minutes (`openDueCapsules`, `functions/src/capsules.ts`) copie les destinataires dans `openedFor` et les prévient une fois (canal `capsules`), si le module « Capsules » est allumé. Ensuite, ils la lisent avec `openedFor array-contains <leur id>`, photos comprises. Une capsule ne se modifie jamais. Son auteur la supprime tant qu'elle est scellée (PRD rév. 187 : ouverte, elle devient un souvenir de ses destinataires), et `onCapsuleDeleted` efface alors ses photos. Si l'appli n'arrive pas à sceller la capsule après avoir envoyé des photos, elle les efface elle-même.

### `capsuleLeftovers/{memberId}`

Ce qu'un auteur retiré de la famille a laissé de scellé (PRD rév. 184). Écrit par le serveur (`onMemberDeletedCapsules`), seulement s'il reste au moins une capsule scellée.

| Champ | Type | Description |
|---|---|---|
| `memberName` | texte | Le prénom du membre retiré |
| `count` | entier | Ses capsules encore scellées. Il baisse à chaque ouverture, et la fiche disparaît à zéro |
| `deleteAsked` | booléen | `false`, puis `true` quand l'admin principal confirme |
| `updatedAt` | horodatage | Heure du serveur |

Droits : l'admin principal lit la fiche, jamais les capsules, et ne peut que passer `deleteAsked` à `true`. Le serveur (`onCapsuleLeftoversUpdated`) supprime alors les capsules encore scellées de ce membre, photos comprises, puis la fiche. Si le membre est revenu entre-temps, rien n'est supprimé. Sans cette demande, les capsules s'ouvrent à leur date comme prévu. Un destinataire retiré ne reçoit rien.

## Carnet de recettes (PRD, récit 38)

### `recipes/{recipeId}`

| Champ | Type | Description |
|---|---|---|
| `title` | texte | Le nom de la recette, de 1 à 80 caractères |
| `originMemberId` | `memberId` ou `null` | De qui vient la recette, quand c'est un membre de la famille |
| `origin` | texte | Sinon, qui l'a transmise, librement (« Mamie Jeanne »), 80 caractères au plus. Vide avec `originMemberId` |
| `servings` | entier ou `null` | Le nombre de parts, de 1 à 50 |
| `ingredients` | texte | Un ingrédient par ligne, de 1 à 3 000 caractères |
| `steps` | texte | Une étape par ligne, de 1 à 6 000 caractères |
| `tags` | liste de textes | 6 au plus (« dessert », « Noël ») |
| `authorId`, `authorName` | texte | Le membre qui l'a écrite et son prénom, qui ne changent jamais |
| `createdAt`, `updatedAt` | horodatage | Heure du serveur |

Droits : tous les membres lisent toutes les recettes, et chacun écrit les siennes. L'auteur et l'admin principal les modifient et les retirent. Le module se montre avec l'interrupteur « Carnet de recettes ». Le partage envoie la recette en texte, sans photo. Depuis la fiche d'un membre, « Voir ses recettes » montre celles dont il est l'origine. Une étiquette est un texte de 1 à 24 caractères. Si le membre nommé dans `originMemberId` quitte la famille, la recette garde son id et reste modifiable.

### `recipes/{recipeId}/photos/{authorId}_{slot}`

Les photos du plat, sur le modèle des photos d'un voyage (`authorId`, `authorName`, `slot`, `createdAt`), avec 3 places par personne (`slot` de 1 à 3). Les fichiers sont dans Storage, à `recipes/{recipeId}/{authorId}/{slot}.jpg` (512 Ko au plus) et `{slot}-thumb.jpg` (64 Ko au plus), envoyés avant la fiche.

Droits : tous les membres les voient. L'auteur de la recette et l'admin principal les ajoutent et les retirent. Quand la recette est supprimée, la fonction `onRecipeDeleted` efface ses photos et ses commentaires, les fiches comme les fichiers. Chaque partie est tentée même si une autre échoue.

### `recipes/{recipeId}/comments/{commentId}`

| Champ | Type | Description |
|---|---|---|
| `text` | texte | Le commentaire, de 1 à 500 caractères, jamais modifié |
| `authorId`, `authorName` | texte | Le membre qui l'a écrit et son prénom |
| `createdAt` | horodatage | Heure du serveur |

Droits : tous les membres lisent les commentaires et en écrivent en leur nom. Son auteur, l'auteur de la recette ou l'admin principal retire un commentaire.

## Voyages (V3, récits 11 à 15)

### `trips/{tripId}`

| Champ | Type | Description |
|---|---|---|
| `title` | texte | Le nom du voyage, de 1 à 80 caractères |
| `startDate`, `endDate` | texte | Le départ et le retour, `AAAA-MM-JJ`, le retour au plus tôt le jour du départ |
| `participantIds` | liste de textes | Les membres qui partent (1 à 13, sans doublon), dont toujours le créateur |
| `readerIds` | liste de textes | Les membres qui suivent le voyage (1 à 40). Ils comprennent toujours les participants |
| `creatorId`, `creatorName` | texte | Le créateur et son prénom à la création, qui ne changent jamais |
| `createdAt`, `updatedAt` | horodatage | Heure du serveur |

Droits : seul un membre de `readerIds` lit le voyage. L'appli le cherche avec `readerIds array-contains <son id>`, car les règles refusent toute requête plus large. Par défaut, tous les membres proposés par `tripAudience()` sont cochés, et le créateur décoche qui ne doit pas le voir (récit 12). La liste est figée au moment où elle est enregistrée : un membre ajouté plus tard à la famille ne voit pas les anciens voyages, sauf si le créateur le coche. Seul un adulte crée un voyage (PRD rév. 131) : un enfant y participe quand un adulte l'ajoute, et il dit alors « bien arrivé » et remplit les étapes. Le créateur et l'admin principal le modifient et le suppriment. Le créateur peut ajouter comme participant n'importe quel membre de la famille, de tous les foyers, enfants compris, car plusieurs foyers partent souvent ensemble (PRD rév. 190). L'appli propose aussi « Ajouter toute la famille » et « Ajouter tout le foyer … ».

### `trips/{tripId}/steps/{stepId}`

| Champ | Type | Description |
|---|---|---|
| `type` | texte | `flight`, `train`, `car`, `lodging` ou `activity` |
| `place` | texte | Le lieu, de 1 à 120 caractères |
| `departDate`, `departTime` | texte | Le départ, `AAAA-MM-JJ`, et son heure `HH:MM` ou `null` |
| `arriveDate`, `arriveTime` | texte ou `null` | L'arrivée prévue, au plus tôt le jour du départ |
| `note` | texte ou `null` | 1 000 caractères au plus |
| `remindAt` | horodatage ou `null` | L'arrivée prévue plus 3 heures, calculée sur le téléphone avec son fuseau horaire. `null` sans heure d'arrivée |
| `arrivedAt`, `arrivedBy`, `arrivedByName` | horodatage, textes ou `null` | « Bien arrivé » : l'heure du serveur, le membre et son prénom |
| `updatedAt` | horodatage | Heure du serveur |

Droits : les lecteurs du voyage lisent ses étapes. Chaque participant les ajoute, les modifie et les supprime, et l'admin principal peut en supprimer une. « Bien arrivé » est signé du membre qui le touche, à l'heure du serveur, et efface `remindAt`. Un participant peut l'annuler. Aucune position n'est enregistrée : les règles refusent tout champ en plus.

### `trips/{tripId}/items/{itemId}`

La liste « À prévoir » du voyage (récit 13) : ce qu'il faut apporter ou réserver, et qui s'en occupe.

| Champ | Type | Description |
|---|---|---|
| `label` | texte, 1 à 80 caractères | « Crème solaire », « Réserver la voiture » |
| `assigneeId` | `memberId` ou null | Un participant qui s'en occupe, ou personne |
| `assigneeName` | texte ou null | Son prénom, gardé pour que la ligne reste lisible s'il quitte le voyage. Null avec `assigneeId` |
| `done` | booléen | Coché quand c'est fait |
| `updatedAt` | horodatage serveur | |
| `photoId` | texte ou null, facultatif | La photo de la réponse, jamais pour « Passer ». Storage : `weeklyQuestions/{weekId}/{memberId}/{photoId}.jpg` (512 Ko au plus) et `{photoId}-thumb.jpg` (64 Ko au plus), envoyées par le membre avant la réponse. Tous les membres lisent les photos. |

Droits : les lecteurs du voyage lisent la liste. Les participants, enfants compris, ajoutent, cochent, modifient et retirent les lignes. L'admin principal peut retirer une ligne. La personne en charge est forcément un participant. La suppression du voyage supprime sa liste sur le serveur. Tests : `firebase/tests/trips.test.mjs`.

### `trips/{tripId}/photos/{authorId}_{slot}`

Les photos du voyage (récit 15). Chaque participant a 30 places, numérotées de 1 à 30. L'identifiant du document est l'auteur suivi de la place, par exemple `m1_7`. Ainsi, les règles limitent les photos à 30 par personne sans avoir à les compter.

| Champ | Type | Description |
|---|---|---|
| `authorId`, `authorName` | texte | L'auteur et son prénom, gardé s'il quitte le voyage |
| `slot` | entier de 1 à 30 | La place, la même que dans l'identifiant |
| `createdAt` | horodatage serveur | Les photos s'affichent de la plus récente à la plus ancienne |

Photos dans Storage : `trips/{tripId}/{authorId}/{slot}.jpg` (1600 px au plus, environ 300 Ko, 512 Ko au plus) et `{slot}-thumb.jpg` (400 px, environ 30 Ko, 64 Ko au plus), sans données EXIF, donc sans lieu. L'appli envoie les deux fichiers avant d'écrire le document. Si un envoi a été coupé, l'auteur peut renvoyer une place dont le document n'existe pas.

Droits : les lecteurs du voyage voient les photos, dans Firestore comme dans Storage. Chaque participant, enfant compris, ajoute les siennes. L'auteur, le créateur du voyage et l'admin principal les retirent, les fichiers d'abord, puis le document. Une photo n'est jamais modifiée : une fois son document écrit, ses fichiers ne peuvent plus être renvoyés. Tests : `firebase/tests/trips.test.mjs` et `firebase/tests/storage.test.mjs`.

### Cloud Functions des voyages

- `onTripStepUpdated` : au premier « bien arrivé » d'une étape, chaque lecteur qui a l'appli reçoit « Arrivée à Ajaccio », sauf celui qui l'a touché. Le texte nomme tous les participants, sans accorder « arrivé » à un prénom.
- `arrivalReminder`, toutes les 30 minutes : les étapes dont `remindAt` est passé et qui n'ont pas de « bien arrivé ». Les participants qui ont l'appli reçoivent un rappel, puis `remindAt` est effacé pour qu'il ne parte qu'une fois. La famille n'est pas alertée. La requête porte sur tous les `steps` (index de groupe de collections dans `firestore.indexes.json`).
- `onTripDeleted` : un voyage supprimé emporte ses étapes, sa liste, ses photos et leurs fichiers dans Storage (tout le dossier `trips/{tripId}/`).

Les notifications des voyages portent `tripId` au lieu de `eventId` : un appui ouvre le voyage.

## Souvenirs (PRD, récit 34)

Une liste fixe de souvenirs, gagnés par ses propres gestes (`functions/src/souvenirs.ts`) : première Bonne nouvelle, 10 réactions données, 5 coups de main rendus, premier « bien arrivé », première réponse et 10 réponses à la question de la semaine, un an dans Familia. Les clés ne changent jamais : l'appli dessine chaque souvenir à partir de sa clé (composant SouvenirMedal de [DESIGN]). Pas de score, pas de total, pas de classement (carte EXPERT-3). Aucun souvenir ne vient des réservations de cadeaux.

### `members/{memberId}/souvenirs/{key}`

| Champ | Type | Description |
|---|---|---|
| `key` | texte | La clé du souvenir |
| `earnedAt` | horodatage | Quand il a été gagné |
| `shown` | booléen | `false` à l'arrivée. Le membre le passe à `true` pour le montrer sur sa fiche |

Droits : le serveur seul crée et supprime. Le membre lit ses souvenirs et change `shown`, rien d'autre. La famille lit un souvenir seulement s'il est montré : une requête doit filtrer `shown == true`.

### `souvenirCounters/{memberId}` et `souvenirMarks/{markId}`

`souvenirCounters` garde les gestes comptés (`reactions`, `helpGiven`, `weeklyAnswers`), que seul le membre lit, pour afficher « Encore 5 coups de main ». `souvenirMarks` note chaque geste compté (`reaction-{postId}-{memberId}`, `weekly-{weekId}-{memberId}`, `help-{requestId}`), pour qu'un geste défait puis refait compte une fois. Personne ne les écrit depuis l'appli, et personne ne lit les marques. Quand un membre part, ses souvenirs, son compteur et ses marques sont effacés (carte EXPERT-1).

Les souvenirs sont donnés par les fonctions qui existent déjà, sans nouvelle tâche planifiée : `onGoodNewsCreated` (première Bonne nouvelle), `onGoodNewsUpdated` (réactions), `onTripStepUpdated` (premier « bien arrivé »), `onWeeklyAnswerWritten` (réponses), `helpRequestStillOpen` à 18 h (les coups de main pris de la veille, et un an après la création de la fiche du membre). Une notification « Nouveau souvenir » (canal `souvenirs`, donnée `souvenirKey`) part une fois, seulement si le module `souvenirs` est allumé, si le membre a l'appli et s'il n'a pas coupé `souvenirs` dans ses réglages. Module éteint, les souvenirs sont quand même gardés. `onMemberDeletedAnswers` retire aussi les souvenirs, le compteur et les marques d’un membre parti.

## Le résumé du jour (PRD, carte AVIS-14)

Module `digest` (Administration > Modules, « Résumé du jour »). Tant qu'il est éteint, tout part tout de suite, comme avant.

Une fois allumé, ces notifications attendent le résumé :

- les souvenirs, sauf le tout premier de chaque membre ;
- « Défi réussi » (la Bonne nouvelle de Familia, elle, paraît tout de suite) ;
- la question de la semaine.

La tâche de toutes les 30 minutes envoie le résumé une fois par jour, à partir de 18 h, sur le canal `weekly`. Il ressemble par exemple à « Aujourd'hui dans Familia : nouveau souvenir, question de la semaine, défi à 60 %, 12 nouvelles feuilles. ». La jauge et les feuilles n'accompagnent qu'une nouvelle qui attend : elles ne font jamais partir un résumé seules.

Les événements, les Bonnes nouvelles, les vœux et « Rejoignez-moi » restent instantanés.

### `engagementDigests/{memberId}`

| Champ | Type | Sens |
|---|---|---|
| `pending` | bool | Quelque chose attend. |
| `souvenirs` | liste de clés | Les souvenirs gagnés depuis le dernier résumé. |
| `challengeDone` | `AAAA-MM` ou `null` | Un défi réussi. |
| `weekly` | `AAAA-MM-JJ` ou `null` | La question de la semaine publiée. |
| `albums` | liste d'ids d'événements | Les albums d'hier où le membre est invité à mettre ses photos (récit 41). |
| `sentDay` | `AAAA-MM-JJ` | Le dernier jour où le résumé est parti, une fois par jour au plus. |
| `leavesSeen` | int | Les feuilles de l'arbre au dernier résumé, pour dire combien ont poussé depuis. |

Seules les fonctions s'en servent, aucune règle ne l'ouvre. Il est effacé avec le membre. Chaque réglage (`souvenirs`, `tree`, `weekly`) est vérifié au moment où la nouvelle entre dans le résumé.

À savoir avant d'allumer le module (relecture de #100) : le résumé arrive sur le canal Android « Question de la semaine » (`weekly`). Un membre qui a coupé ce canal dans les réglages d'Android ne reçoit plus ses souvenirs. Il faudra le dire dans l'aide, ou donner au résumé son propre canal.

## L'arbre de la famille et le défi du mois (PRD, récit 35)

Un arbre commun grandit avec les gestes de tous, et un défi par mois a sa jauge (`functions/src/tree.ts`). Le serveur seul écrit. Rien ne dit qui a le plus contribué (carte EXPERT-3).

### `familyTree/current`

| Champ | Type | Sens |
|---|---|---|
| `leaves` | int | Les feuilles de l'arbre. Le nombre ne baisse jamais. |
| `bloomedMonths` | liste de `AAAA-MM` | Les mois où le défi a été réussi : l'arbre fleurit. |
| `updatedAt` | horodatage | Le dernier changement. |

Les paliers visibles sont à 20, 60, 120, 250, 500 et 1000 feuilles (`TREE_LEVELS`). L'appli dessine l'arbre de son palier et l'habille selon la saison.

Une feuille s'ajoute pour chacun de ces gestes :

- une Bonne nouvelle, pour son auteur ;
- une réaction, pour celui qui réagit ;
- une réponse « Je viens » à un événement, pour la personne qui vient ;
- un coup de main pris, pour le volontaire ;
- un « bien arrivé », pour celui qui l'envoie.

Chaque geste compte une seule fois, et chaque personne apporte au plus 5 feuilles par jour, pour éviter la triche. Les votes et les vœux du récit viendront avec leurs modules.

### `challenges/{AAAA-MM}`

| Champ | Type | Sens |
|---|---|---|
| `title`, `emoji` | texte | Par exemple « 20 photos de Noël », 🎄. |
| `measure` | `photos` \| `goodNews` \| `linked` | Ce qui fait avancer la jauge : les photos des Bonnes nouvelles du mois, le nombre de Bonnes nouvelles, ou (défi d'un foyer) les Bonnes nouvelles avec photo liées au défi par `forChallenge`. |
| `target`, `progress` | int | L'objectif et la jauge, qui s'arrête à l'objectif. |
| `achievedAt` | horodatage ou `null` | Le moment où le défi a été réussi. |
| `choiceId` | texte | L'entrée de la liste des défis (`CATALOG` dans `tree.ts`, `ChallengeCatalog.kt` dans l'appli : 16 défis). |
| `source` | `default` \| `admin` \| `household` | Le défi par défaut du mois, celui choisi par l'admin principal, ou celui publié par un foyer (récit 35 bis ; `choiceId` vaut alors `null` et `householdId` nomme le foyer). |
| `month`, `createdAt` | texte, horodatage | Le mois, et la création. |

Le défi est créé la première fois qu'il sert, à partir du choix de l'admin principal s'il y en a un, sinon du défi publié par le foyer du mois, sinon du défi par défaut du mois. La tâche de 18 h le crée aussi, pour qu'il soit là dès le 1er du mois.

### `challengePicks/{AAAA-MM}`

Le choix de l'admin principal dans Administration, pour ce mois ou le suivant : `choiceId` (une entrée de la liste) et `pickedAt` (l'heure du serveur). Seul l'admin principal le lit et l'écrit, et personne ne l'efface. La fonction `onChallengePicked` l'applique à `challenges/{mois}` : elle crée le défi s'il n'existe pas encore, ou le remplace tant que personne n'a fait avancer la jauge. Une fois la jauge partie, le défi reste jusqu'à la fin du mois, pour qu'aucune photo ne compte pour rien, et l'appli l'indique.

### `challengeRotation/current` (récit 35 bis)

L'ordre dans lequel les foyers lancent le défi, un mois chacun : `householdIds` (1 à 20 foyers, dans l'ordre), `startMonth` (`AAAA-MM`, le mois du premier) et `updatedAt` (l'heure du serveur). Le foyer d'un mois est `householdIds[(mois - startMonth) % taille]`. La famille le lit, seul l'admin principal l'écrit depuis Administration (« Ordre des foyers » : il monte ou descend les foyers ; un premier ordre commence le mois suivant, un ordre déjà là reprend au foyer du mois). Supprimer un foyer le retire de l'ordre, et les suivants avancent d'un mois. Sans ordre, aucun foyer ne prépare de défi.

### `householdChallenges/{AAAA-MM}` (récit 35 bis)

| Champ | Type | Sens |
|---|---|---|
| `householdId` | `householdId` | Le foyer dont c'est le tour ce mois-là. |
| `status` | `draft` \| `published` \| `skipped` | En préparation, publié, ou « Passer notre tour ». |
| `kind` | `photo` \| `outing` \| `cooking` \| `kindness` \| `creative` | Le genre de défi choisi à l'étape 1. |
| `title`, `emoji` | texte (60 au plus), emoji | Par exemple « 15 photos de nos balades », 📸. |
| `illustration` | `sun` \| `heart` \| `leaf` \| `star` \| `camera` \| `pot` | Le dessin de la carte. |
| `target` | int, 3 à 50 | L'objectif : le nombre de Bonnes nouvelles avec photo liées au défi. |
| `updatedBy`, `updatedAt` | `memberId`, horodatage serveur | Le dernier qui l'a touché. |

Tout le foyer du mois le prépare, enfants compris, pour le mois en cours ou le suivant. Seul un adulte du foyer le publie ou passe le tour. Une fois publié ou passé, il ne change plus depuis l'appli et personne ne l'efface ; l'admin principal peut toujours imposer un autre défi par `challengePicks`. Toute la famille le lit.

La fonction `onHouseholdChallengeWritten` l'applique quand il passe à `published` : pour le mois en cours, il remplace le défi tant que personne n'a fait avancer la jauge et que l'admin principal n'a rien choisi ; pour le mois suivant, il servira à créer le défi le 1er. Rien ne change tant que le module `tree` est éteint. Rien de publié le 1er : le défi par défaut prend le relais, sans prévenir la famille.

Sept jours avant son mois, la tâche de 18 h prévient le foyer (ses membres avec l'appli, sauf ceux qui ont coupé `tree`) : « C'est à vous de lancer le défi de janvier ! Préparez-le ensemble dans l'appli, ou passez votre tour. » Une seule fois (`treeMarks/hostReminder-{AAAA-MM}`), pas si le foyer a déjà publié ou passé, ni quand le module `tree` est éteint.

Quand l'objectif est atteint :

- l'arbre fleurit (`bloomedMonths`) ;
- si le module `tree` est allumé, toute la famille qui a l'appli reçoit « Défi réussi ! » (canal `tree`), sauf ceux qui ont coupé `tree` ;
- Familia publie une Bonne nouvelle `goodNews/challenge-{AAAA-MM}`, une seule fois. Son auteur est `familia`, elle n'a pas de photo, elle porte `challengeMonth` et ne nomme personne. Pour le défi d'un foyer, elle et la notification citent le foyer hôte par son nom (« …, lancé par les Martin »), sans nommer de membre (récit 35 bis, avis [EXPERT] n° 6).

Module éteint, l'arbre et la jauge avancent quand même, mais rien n'est envoyé ni publié.

Droits : la famille lit `familyTree`, `challenges`, `challengeRotation` et `householdChallenges`. Personne n'écrit `familyTree` ni `challenges` depuis l'appli : `challengePicks` et `challengeRotation` sont ouverts à l'admin principal seul, `householdChallenges` au foyer du mois.

`treeMarks/{markId}` (`goodNews-{postId}`, `reaction-{postId}-{memberId}`, `rsvp-{eventId}-{memberId}`, `help-{requestId}`, `arrival-{tripId}-{stepId}`, `challenge-{postId}`) et `treeDays/{memberId}_{AAAA-MM-JJ}` (`count`) servent aux fonctions seules. Aucune règle ne les ouvre. Un TTL sur `expireAt` les efface 40 jours après. Quand un membre part, ses marques et ses jours sont effacés (carte EXPERT-1), mais ses feuilles restent à la famille.

Fonctions : il n'y a pas de nouvelle tâche planifiée. Ces déclencheurs, qui existent déjà, ajoutent la feuille ou font avancer la jauge : `onGoodNewsCreated`, `onGoodNewsUpdated`, `onHelpRequestWrite`, `onTripStepUpdated` et `helpRequestStillOpen` (le défi du mois). Un seul déclencheur est nouveau : `onRsvpWritten` (`events/{eventId}/rsvps/{memberId}`), pour « Je viens ». Une erreur de l'arbre n'arrête jamais ce que faisait le déclencheur.

## Question de la semaine (PRD, récit 37)

Chaque lundi à 9 h (heure de Paris), le serveur publie une question tirée de la banque de départ (`functions/src/questionBank.ts`, 60 questions validées par l'admin principal, [banque](https://claude.ai/artifact/SX2LFwekq2v8GzGeRJC8wF)). La première sort le lundi 30/11/2026, et c'est la première de la banque. Une question de saison sort d'abord pendant sa saison ; sinon le tirage se fait parmi les questions pas posées depuis 330 jours, toujours le même pour un lundi donné. Rien ne sort tant que le module `weeklyQuestion` est éteint dans `config/app.features`.

### `weeklyQuestions/{weekId}`

`weekId` est la date du lundi, `AAAA-MM-JJ`. Le document est écrit seulement par les Cloud Functions.

| Champ | Type | Description |
|---|---|---|
| `weekId` | texte | La date du lundi, aussi rangée en champ pour les requêtes |
| `text` | texte | La question |
| `bankId` | texte (`q01` à `q60`) | Sa place dans la banque, pour ne pas la reposer trop tôt |
| `proposedByName` | texte ou null | Le prénom du membre qui l'a proposée (« Proposée par Sam », plus tard) |
| `publishedAt` | horodatage | Lundi 9 h |
| `revealAt` | horodatage | 48 h plus tard : à partir de là, tout le monde lit les réponses |
| `answerCount` | entier | Le nombre de réponses, sans « Passer », tenu par le serveur. La carte d'accueil montre ce nombre, jamais les noms (carte EXPERT-3) |

### `weeklyQuestions/{weekId}/answers/{memberId}`

| Champ | Type | Description |
|---|---|---|
| `memberId`, `memberName` | `memberId`, texte | Qui répond, et son prénom à ce moment-là |
| `text` | texte (1 à 500 caractères) ou null | La réponse ; null pour « Passer » |
| `skipped` | booléen | `true` pour « Passer » (avis [EXPERT] n° 8) |
| `updatedAt` | horodatage serveur | |

Droits :

- **Question** : tous les membres la lisent ; personne ne l'écrit depuis l'appli.
- **Répondre ou passer** : chaque membre avec l'appli, enfants compris, pour lui seul, sur une question publiée. Il peut changer ou retirer sa réponse.
- **Lire les réponses** : sa propre réponse toujours ; celles des autres une fois qu'on a répondu ou passé, et pour tous à partir de `revealAt`. Avant, une requête sur `answers` est refusée : l'appli lit seulement la réponse du membre.

Cloud Functions (`functions/src/weeklyQuestion.ts`) :

| Fonction | Déclencheur | Rôle |
|---|---|---|
| `publishWeeklyQuestion`, appelée par `arrivalReminder` | toutes les 30 minutes, agit le lundi à partir de 9 h | Publie la question de la semaine et prévient chaque membre qui a l'appli, sauf ceux qui ont coupé `weekly`. Canal `weekly`, donnée `weeklyQuestionId`. Ne fait rien deux fois le même lundi. Pas de tâche planifiée à part : Cloud Scheduler n'en offre que 3 gratuites. |
| `onWeeklyAnswerWritten` | réponse écrite ou retirée | Recompte `answerCount`, et supprime de Storage la photo qu'une réponse ne nomme plus (photo changée ou retirée, « Passer », réponse retirée ou membre parti). |
| `onMemberDeletedAnswers` | suppression d'un membre | Retire ses réponses (carte EXPERT-1). |

Plus tard : le choix et l'ajout de questions dans Administration, les questions proposées par les membres, la feuille de l'arbre (récit 35) et le regroupement avec les autres rendez-vous en une notification par jour (carte AVIS-14).

## Sondages (PRD, récits 2 bis et 2 ter)

Un adulte pose une question à toute la famille ou aux membres qu'il coche : « Trouver une date » (2 à 5 dates, chacun répond oui, peut-être ou non à chaque date) ou « Choix libre » (2 à 6 réponses, une seule ou plusieurs). Les parents répondent pour leurs enfants. Les réponses sont nommées : tous ceux qui voient le sondage voient qui a répondu quoi. L'entrée est dans la barre du Planning, derrière le module `polls`.

### `polls/{pollId}`

| Champ | Type | Description |
|---|---|---|
| `kind` | `date` ou `choice` | Le type de sondage |
| `question` | texte (1 à 120 caractères) | Ne change plus une fois posée |
| `options` | liste (2 à 5 dates, 2 à 6 textes) | Une date s'écrit `AAAA-MM-JJ` ou `AAAA-MM-JJ HH:MM` ; un texte fait au plus 80 caractères. Ne change plus |
| `multiple` | booléen | Plusieurs réponses permises (choix libre seulement) |
| `askedIds` | liste de `memberId` (1 à 30) | Qui répond |
| `viewerIds` | liste de `memberId` | L'auteur en premier, puis ceux qui répondent et les adultes responsables des enfants interrogés. L'appli lit ses sondages par `array-contains` |
| `eventId` | `eventId` ou null | L'événement auquel le sondage est rattaché (plus tard) |
| `closesAt` | horodatage ou null | Fin des réponses : minuit (heure de Paris) après le dernier jour choisi |
| `closed` | booléen | Terminé par l'auteur, ou par le serveur à `closesAt` |
| `chosen` | entier ou null | La date retenue (son rang dans `options`) |
| `createdEventId` | `eventId` ou null | L'événement créé pour la date retenue, par le même membre |
| `authorId`, `authorName` | `memberId`, texte | L'auteur et son prénom au moment de poser la question |
| `createdAt`, `updatedAt` | horodatage serveur | |

### `polls/{pollId}/votes/{memberId}`

| Champ | Type | Description |
|---|---|---|
| `memberId` | `memberId` | Qui répond (identique à l'id) |
| `answers` | liste de `yes`, `maybe`, `no` | Sondage de date : une réponse par date, dans l'ordre |
| `choices` | liste d'entiers | Choix libre : les rangs choisis (un seul si `multiple` est faux) |
| `votedBy` | `memberId` | Le membre lui-même, ou l'adulte qui répond pour son enfant |
| `updatedAt` | horodatage serveur | |

Droits :

- **Poser** : un adulte (un enfant avec l'appli répond mais ne pose pas).
- **Lire** : ceux de `viewerIds`, et l'admin principal.
- **Répondre** : le membre interrogé ou un de ses adultes responsables, tant que le sondage est ouvert. On peut changer sa réponse.
- **Terminer, choisir la date, supprimer** : l'auteur ou l'admin principal. Choisir la date le termine aussi. L'appli crée d'abord l'événement (au nom de celui qui choisit), puis écrit `chosen` et `createdEventId` ; la règle vérifie que cet événement existe et qu'il est bien de lui.

Cloud Functions (`functions/src/polls.ts`) :

| Fonction | Déclencheur | Rôle |
|---|---|---|
| `onPollCreatedNotify` | sondage créé | Prévient ceux qui répondent, sauf l'auteur : « Nouveau sondage ». Canal `events`, donnée `pollId`. |
| `onPollUpdated` | sondage terminé | Date choisie : ceux qui ont dit oui sont inscrits « Je viens » à l'événement créé. Choix libre terminé : le résultat part à tous ceux qui voient le sondage, sauf celui qui l'a terminé. Sondage de date terminé par sa fin : l'auteur est invité à choisir la date. |
| `closeDuePolls`, appelée par `arrivalReminder` | toutes les 30 minutes | Termine les sondages dont `closesAt` est passé. Pas de tâche planifiée à part : Cloud Scheduler n'en offre que 3 gratuites. |
| `onPollDeleted` | sondage supprimé | Supprime ses réponses. |
| `onMemberDeletedAnswers` | suppression d'un membre | Retire aussi ses réponses aux sondages (index `votes.memberId` en groupe de collections). |

Plus tard : rattacher un sondage à un événement (`eventId`) et le rappel « À faire pour moi » sur l'Accueil.

## Lieux et « Rejoignez-moi » (PRD, récit 33)

Interrupteur `places`. Un lieu (`core/place/Place.kt`) a toujours une position (`lat`, `lng`), et peut avoir un nom choisi par le membre et une adresse trouvée par le géocodeur Android (gratuit). Il n'y a pas de carte dans l'appli, donc pas de clé d'API : « Itinéraire » passe un lien `geo:` à Google Maps ou Waze. Le même composant servira à une Bonne nouvelle, à un événement et à une étape de voyage. Pour l'instant, le lieu d'un événement (texte libre) gagne seulement un bouton « Itinéraire ».

La position est lue une seule fois, quand le membre touche « Prendre ma position ». L'autorisation demandée est « pendant l'utilisation de l'appli » seulement (`ACCESS_FINE_LOCATION` et `ACCESS_COARSE_LOCATION`, jamais `ACCESS_BACKGROUND_LOCATION`). Le membre peut aussi taper une adresse, ou coller un lien Google Maps : un lien court `maps.app.goo.gl` est ouvert une fois par le téléphone pour lire le lien complet.

### `joinMe/{shareId}`

| Champ | Type | Description |
|---|---|---|
| `authorId`, `authorName` | `memberId` et texte | Qui l'envoie, et son prénom |
| `recipientIds` | liste de `memberId` (1 à 30) | Ceux qui le reçoivent, cochés à chaque fois (personne au départ, carte AVIS-13). Jamais l'auteur |
| `name` | texte (1 à 60) ou null | Le nom du lieu |
| `address` | texte (1 à 200) ou null | L'adresse trouvée par le téléphone |
| `lat`, `lng` | nombres | La position |
| `note` | texte (1 à 200) ou null | Un mot (« On est près du poste de secours ») |
| `createdAt` | horodatage serveur | |
| `expireAt` | horodatage | 24 h après l'envoi, écrit par le téléphone (la règle accepte entre 22 h et 26 h, pour une horloge un peu fausse). Firestore supprime le document ensuite (TTL déclarée dans `firestore.indexes.json`) ; l'appli le cache dès `expireAt` |

Droits : tout membre avec l'appli l'envoie, en son nom. Seuls l'auteur et les destinataires le lisent, pas même l'admin principal. Personne ne le modifie. Seul l'auteur le retire (« Ne plus partager »).

À savoir : Firestore efface les documents expirés avec un retard qui peut aller jusqu'à environ 24 h. Pendant ce temps, un partage expiré reste lisible par l'auteur et les destinataires (les règles ne regardent pas `expireAt` à la lecture). L'appli le cache, mais quelqu'un qui lirait la base directement pourrait encore voir la position.

Cloud Functions (`functions/src/joinMe.ts`) :

| Fonction | Déclencheur | Rôle |
|---|---|---|
| `onJoinMeCreatedNotify` | partage créé | Prévient les destinataires tout de suite, sans regroupement (carte AVIS-14) : « Camille : rejoins-moi », le lieu et le mot. Canal `joinMe` (sonne), donnée `joinMeId` : un appui ouvre l'Accueil. Tant que le module est éteint pour la famille, seul l'admin principal est prévenu, et seulement s'il est coché. |
| `onMemberDeletedAnswers` | suppression d'un membre | Retire aussi les partages qu'il avait envoyés. |

## Utilisation (PRD, récit 1 nonies)

### `usage/{memberId}/days/{day}`

Le temps passé dans l'appli par un membre, un jour donné. Le téléphone le compte (appli au premier plan, partie par partie) et l'envoie lui-même : chaque jour terminé une fois, et le jour en cours au plus toutes les 3 heures. Rien n'est compté tant que le module « Utilisation » est éteint pour la famille.

| Champ | Type | Sens |
|---|---|---|
| `day` | string `AAAA-MM-JJ` | Le jour, identique à l'id du document. |
| `opens` | int (0 à 500) | Les ouvertures de l'appli. Un retour en moins de 5 minutes n'en compte pas une nouvelle. |
| `seconds` | map | Les secondes par partie de l'appli : `home`, `planning`, `gifts`, `help`, `goodNews`, `weeklyQuestion`, `trips`, `family`, `other` (0 à 86 400 chacune). |
| `expireAt` | timestamp | Le jour plus 13 mois. La règle TTL de `firestore.indexes.json` efface le document à cette date. |

Le document ne contient jamais ce que la personne a lu, écrit ou ouvert. Seul le membre écrit ses propres jours. L'admin principal lit les jours de tout le monde, et les adultes responsables d'un enfant (ses `parentIds`) lisent ceux de cet enfant (carte EXPERT-5). Personne ne supprime un jour depuis l'appli : le TTL s'en charge, et la fonction qui suit le retrait d'un membre efface aussi tous ses jours.

## Consommation (PRD, récit 1 sexies)

### `consumption/{day}`

L'usage de la base un jour donné, lu une fois chaque matin à partir de 8 h dans Cloud Monitoring par la tâche des 30 minutes `arrivalReminder` (`checkConsumptionIfDue` de `firebase/functions/src/consumption.ts`), seulement si le document de la veille n'existe pas encore. Seul le serveur l'écrit ; seul l'admin principal le lit, dans Administration › Consommation.

| Champ | Type | Sens |
|---|---|---|
| `day` | string `AAAA-MM-JJ` | Le jour lu (la veille), identique à l'id. |
| `reads`, `writes`, `deletes` | int | Les opérations de la journée, heure de Paris. |
| `storageBytes` | int ou null | La taille de la base ce jour-là, si Monitoring la donne. |
| `monthCost` | number | Le coût estimé du mois jusqu'à ce jour, en euros, au-delà des parts gratuites (prix volontairement hauts). |
| `alerts` | liste | Les limites atteintes à 80 % (`reads`, `writes`, `deletes`, `storage`) et `cost` au-delà de 0,50 €. Une alerte n'est envoyée qu'une fois par jour. |
| `checkedAt` | timestamp | L'heure du relevé. |
| `error` | string | `monitoring` quand Monitoring n'a pas pu être lu (rôle « Lecteur Monitoring » manquant, docs/deploiement.md). Les autres champs manquent alors. |

Ces documents sont petits (un par jour) et ne contiennent rien sur la famille.

## Comptes du voyage (PRD rév. 193, récit 16 bis)

Une brique commune : un carnet appartient à un contexte (un voyage aujourd'hui, un événement plus
tard). Il est rangé hors de `trips/` et supprimé avec son voyage (`onTripDeletedAccounts`). Seuls
les adultes participants le lisent et l'écrivent. Les montants sont en centimes d'euro entiers.

### `expenseBooks/{containerType}_{containerId}`

`containerType` (`trip`), `containerId`, `creatorId` (le créateur du voyage, qui change les parts,
corrige tout et clôture), `shares` (foyer → nombre de parts : 1 par adulte et 0,5 par enfant par
défaut), `closed`, `createdAt`, `updatedAt`. Ouvert par le premier adulte participant qui affiche les comptes.

### `expenseBooks/{bookId}/expenses/{expenseId}`

`title`, `amountCents` (en euros), `date` (`AAAA-MM-JJ`), `payerId`, `payerHouseholdId`, `category`
(`lodging`, `transport`, `groceries`, `restaurant`, `activity`, `other`), `mode` (`shares`, `equal`,
`amounts`), `householdIds`, `amounts` (foyer → centimes, en mode `amounts` seulement), `currency`,
`originalCents`, `rate` (euros pour une unité, tapé à la main ; 1 en euros), `authorId`,
`authorName`, `createdAt`, `updatedAt`.

### `expenseBooks/{bookId}/expenses/{expenseId}/history/{entryId}`

Écrit par le serveur seul à chaque changement (`onExpenseWritten`) : `action` (`created`, `edited`,
`deleted`), `byId`, `byName`, `title`, `amountCents`, `changed` (les champs modifiés), `at`.

### `expenseBooks/{bookId}/settlements/{settlementId}`

« C'est remboursé », coché par un adulte du foyer qui reçoit : `fromHouseholdId`, `toHouseholdId`,
`amountCents`, `confirmedBy`, `confirmedByName`, `createdAt`.
