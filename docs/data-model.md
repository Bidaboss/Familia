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

- **L'admin principal** (Kévin) est nommé dans `config/family`, que seul le workflow GitHub « Créer le premier admin » écrit. Personne ne peut lui retirer ce rôle depuis l'appli. S'il perd l'accès, relancer ce workflow avec son adresse lui rend le rôle. Il a tous les droits sur toute la famille, y compris pour un foyer sans chef, et lui seul voit le menu « Administration » : nommer ou retirer les chefs, créer ou supprimer un foyer, sauvegarder les données (PRD, récit 1 ter).
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

Droits : seul le membre lui-même lit et modifie ses réglages. L'appli écrit chaque réglage en fusion, pour ne pas effacer l'autre.

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

**Canaux Android** (récit 6 quinquies) : chaque notification part sur un canal, que chacun peut couper dans les réglages Android : `events` (affiché « Planning »), `reminders`, `gifts`, `helpRequests` (Entraide ; le rappel de la veille d'un coup de main reste sur `reminders`), `goodNews` (Bonnes nouvelles), `trips` (Voyages : « bien arrivé » et son rappel). Chaque module ajoute le sien en même temps que lui : `comments` (Commentaires et réactions), `weekly` (Rendez-vous de la semaine). Les identifiants ne changent jamais, car Android y garde le choix du membre. Un canal inconnu de l'appli retombe sur « Planning ».

Tests : `cd firebase && npm run test:functions` (la logique, et les fonctions contre l'émulateur Firestore, avec un faux envoi de notifications).

## Export JSON (lot 9)

L'admin principal crée une sauvegarde depuis Famille, puis « Administration » et « Sauvegarde des données » (PRD, récit 6 ter). Le sélecteur de fichiers d'Android lui laisse choisir le téléphone ou Google Drive. Le fichier `familia-sauvegarde-AAAA-MM-JJ.json` contient :

| Clé | Contenu |
|---|---|
| `app`, `formatVersion`, `appVersion`, `exportedAt` | « Familia », la version du format (1), la version de l'appli, l'heure de l'export (ISO 8601, UTC) |
| `households`, `members`, `events` | Les documents tels qu'ils sont stockés, avec leur `id` |
| `rsvps` | Les réponses, avec `eventId` et `memberId` |

Les données sont lues sur le serveur, jamais dans le cache du téléphone. Hors ligne, aucune sauvegarde n'est faite et l'appli demande de se connecter : une sauvegarde est complète ou n'existe pas. L'appli lit les données avant d'ouvrir le sélecteur, et si l'écriture échoue, elle supprime le fichier créé. Les documents sont triés par identifiant, donc deux exports des mêmes données donnent le même fichier. Ne sont pas exportés : `memberEmails`, qui se reconstruit à partir des membres, et les téléphones et réglages de chacun, que seul le membre concerné peut lire. À partir de la V2, les cadeaux s'ajouteront au fichier, avec un `formatVersion` augmenté si sa forme change.

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

- **Lire** : ceux qui voient la liste, sauf le bénéficiaire avant la révélation.
- **Réserver** : ceux qui voient la liste, sauf le bénéficiaire. Un enfant réserve seul, jamais en cadeau commun (carte PRODUIT-19).
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

### Cloud Functions des cadeaux (`firebase/functions/src/gifts.ts`)

| Fonction | Quand | Ce qu'elle fait |
|---|---|---|
| `revealGifts` | Chaque jour à 7 h, heure de Paris | Passe `revealed` à vrai pour les listes dont l'occasion est passée (pas « Mes envies »), et prévient leur bénéficiaire s'il a l'appli (PRD, récit 10 bis) |
| `birthdayGiftReminders` | Chaque jour à 10 h, heure de Paris | À J-21 d'un anniversaire, prévient tous les adultes sauf le fêté, avec ses envies. À J-14, recommence si aucun article de ses listes n'est réservé (récit 10) |
| `onGiftItemWritten` | Un article est modifié ou supprimé | S'il était réservé, prévient le réservant et les participants (jamais le bénéficiaire). Une suppression efface aussi la réservation et ses parts |
| `onGiftShareWritten` | Une part est ajoutée, modifiée ou retirée | Recalcule `sharesTotal` et `sharesCount` sur la réservation |
| `onGiftClaimDeleted` | Une réservation est relâchée | Efface les parts du cadeau commun |
| `onGiftListDeleted` | Une liste est supprimée | Efface ses articles, réservations et parts |
| `onMemberDeletedGifts` | Un membre est retiré | Efface les listes dont il est le bénéficiaire |

Une notification des cadeaux porte `listId` dans ses données à la place de `eventId`. Le bénéficiaire ne reçoit jamais de notification sur ses cadeaux, sauf celle de la révélation.

Tant que le module n'est pas allumé pour la famille (`config/app.features.gifts` différent de `true`), ces fonctions ne préviennent que l'admin principal (`config/family.ownerMemberId`), le seul à qui l'appli montre déjà le module. La révélation et le nettoyage se font quand même.

Limite connue : les règles ne vérifient pas que chaque identifiant de `viewerIds` est un membre (cela coûterait une lecture par personne). L'appli ne propose que des membres de la famille.

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
| `onGoodNewsCreated` | création | Prévient toute la famille qui a l'appli, sauf l'auteur et ceux qui ont coupé `goodNews`. Quand un enfant publie, ses adultes responsables reçoivent « Léa a publié une Bonne nouvelle », même s'ils ont coupé les autres (carte AVIS-12). Canal `goodNews`, donnée `goodNewsId`. |
| `onGoodNewsUpdated` | modification | Efface dans Storage les photos que l'auteur a retirées. |
| `onGoodNewsDeleted` | suppression | Efface toutes les photos, quel que soit l'auteur du retrait. |

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

## Voyages (V3, récits 11, 12 et 14)

### `trips/{tripId}`

| Champ | Type | Description |
|---|---|---|
| `title` | texte | Le nom du voyage, de 1 à 80 caractères |
| `startDate`, `endDate` | texte | Le départ et le retour, `AAAA-MM-JJ`, le retour au plus tôt le jour du départ |
| `participantIds` | liste de textes | Les membres qui partent (1 à 13, sans doublon), dont toujours le créateur |
| `readerIds` | liste de textes | Les membres qui suivent le voyage (1 à 40). Ils comprennent toujours les participants |
| `creatorId`, `creatorName` | texte | Le créateur et son prénom à la création, qui ne changent jamais |
| `createdAt`, `updatedAt` | horodatage | Heure du serveur |

Droits : seul un membre de `readerIds` lit le voyage. L'appli le cherche avec `readerIds array-contains <son id>`, car les règles refusent toute requête plus large. Par défaut, tous les membres proposés par `tripAudience()` sont cochés, et le créateur décoche qui ne doit pas le voir (récit 12). La liste est figée au moment où elle est enregistrée : un membre ajouté plus tard à la famille ne voit pas les anciens voyages, sauf si le créateur le coche. Seul un adulte crée un voyage (PRD rév. 131) : un enfant y participe quand un adulte l'ajoute, et il dit alors « bien arrivé » et remplit les étapes. Le créateur et l'admin principal le modifient et le suppriment. Un adulte n'ajoute comme participants que les enfants de son foyer : seule l'appli le vérifie, car contrôler chaque participant dans les règles coûterait une lecture par personne, au-delà des 10 lectures permises par écriture.

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

### Cloud Functions des voyages

- `onTripStepUpdated` : au premier « bien arrivé » d'une étape, chaque lecteur qui a l'appli reçoit « Arrivée à Ajaccio », sauf celui qui l'a touché. Le texte nomme tous les participants, sans accorder « arrivé » à un prénom.
- `arrivalReminder`, toutes les 30 minutes : les étapes dont `remindAt` est passé et qui n'ont pas de « bien arrivé ». Les participants qui ont l'appli reçoivent un rappel, puis `remindAt` est effacé pour qu'il ne parte qu'une fois. La famille n'est pas alertée. La requête porte sur tous les `steps` (index de groupe de collections dans `firestore.indexes.json`).
- `onTripDeleted` : un voyage supprimé emporte ses étapes.

Les notifications des voyages portent `tripId` au lieu de `eventId` : un appui ouvre le voyage.
