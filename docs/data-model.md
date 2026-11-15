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

Droits : seul le membre lui-même lit et modifie ses réglages.

## Cloud Functions (lot 8)

Le code est dans `firebase/functions`, en TypeScript, et tourne en Europe (`europe-west1`), avec une seule instance au plus.

| Fonction | Quand | Ce qu'elle fait |
|---|---|---|
| `onEventCreated` | Un événement est ajouté | Prévient tous les membres qui ont l'appli, sauf l'auteur (PRD, récit 6) |
| `onEventUpdated` | Un événement est modifié | Idem, seulement si un champ visible a changé |
| `onEventDeleted` | Un événement est supprimé | Efface ses réponses, puis prévient la famille |
| `onMemberDeleted` | Un membre est retiré | Efface ses réponses, ses téléphones et ses réglages |
| `dayBeforeReminder` | Chaque jour à 18 h, heure de Paris | Rappelle l'événement du lendemain à ceux qui ont répondu « Je viens » ou « Peut-être ». Un enfant sans l'appli est rappelé à ses adultes responsables (PRD, récit 6 bis). |

Un événement déjà passé ne prévient personne. L'auteur d'un changement est reconnu par son compte Firebase.

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

Le lien de téléchargement n'est pas dans ce document : il est fixé dans l'appli (`BuildConfig.DOWNLOAD_URL`, le dossier Drive).

Droits : tous les membres le lisent, et personne ne l'écrit depuis l'appli. Kévin le modifie dans la console Firebase (voir [mise-a-jour.md](mise-a-jour.md)).

### `config/family` (carte DEV-8)

| Champ | Type | Description |
|---|---|---|
| `ownerMemberId` | texte | Le membre qui est admin principal |

Droits : tous les membres le lisent, et personne ne l'écrit depuis l'appli. Le workflow « Créer le premier admin » l'écrit avec le compte de service. Si le document manque ou ne peut pas être lu, personne n'est admin principal : l'appli reste utilisable, et les chefs gardent leurs droits sur leur foyer.
