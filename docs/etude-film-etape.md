# Film d'étape et film du voyage : étude technique (récit 108, règles 32 à 34)

Étude courte, sans code, pour que l'admin décide. Elle s'appuie sur ce qui est déjà dans l'appli (24/11/2026).

## 1. Ce qui existe déjà

- **Un fabricant de film sur le téléphone** : `core/video/PhotoFilmMaker.kt` (récit 16). Il utilise Media3 Transformer (bibliothèque Android de Google, déjà dans l'appli). Il produit un MP4 muet en 1280 × 720, H.264, 30 images/s, chaque photo cadrée sans être coupée. Rien n'est envoyé à un serveur.
- **Le film d'un album** : `AlbumFilmViewModel`. Il dure environ une minute, chaque photo restant de 1,5 à 3 s. Il est fait à la demande, gardé dans le cache du téléphone et partagé par la feuille de partage d'Android. Les photos d'un voyage l'ont déjà : c'est le « film du voyage » actuel, sans découpage par étape.
- **Les photos d'un voyage sont liées à leur étape** (`stepId`, récit 104). Chaque participant a au plus 30 photos par voyage, de 512 Ko au plus chacune.
- **Les notes vocales** (m4a, 2 min, 2 Mo au plus) et **les mots courts** sont aussi liés aux étapes.
- **Le fil d'actualité** montre déjà les photos des voyages et « Le mot de l'étape », jamais pour un voyage caché à quelqu'un (`readerIds`).
- **Les notifications de photos** sont regroupées après 10 min de calme (`tripPhotoBursts`, récit 104).

Conséquence : fabriquer un film d'étape, c'est surtout réutiliser `PhotoFilmMaker` avec les photos d'une seule étape. La vraie nouveauté, c'est de le **publier** sur le fil.

## 2. Encodage sur Android

| Point | Proposition | Pourquoi |
|---|---|---|
| Outil | Media3 Transformer (déjà là) | Aucune nouvelle dépendance. Il utilise l'encodeur matériel du téléphone, avec un encodeur logiciel en secours. |
| Format | MP4, H.264, 1280 × 720, 30 images/s | Lisible partout (WhatsApp, galerie, téléviseur). Le 1080p double le poids pour un gain faible sur des photos de 512 Ko. |
| Débit | Environ 1,5 Mbit/s, fixé dans l'encodeur | Des photos fixes se compressent très bien. Sans débit fixé, certains téléphones montent à 8 ou 10 Mbit/s. |
| Mouvement | Léger zoom lent sur chaque photo (effet « Ken Burns »), coupe nette entre deux photos | Media3 fait le zoom avec une seule matrice par photo. Il n'a pas de fondu enchaîné tout fait entre deux photos : un fondu demande un montage à deux pistes, plus fragile. On le garde pour plus tard. |
| Son | Muet en premier ; ensuite, au choix, les notes vocales de l'étape ou une musique libre de droits fournie avec l'appli | Une musique dans l'appli ajoute environ 3 à 5 Mo à l'APK par morceau. Les notes vocales sont déjà là et parlent de l'étape. |
| Temps de fabrication | À mesurer ; estimation de 10 à 30 s pour 30 photos sur un téléphone moyen, plus le téléchargement des photos (15 Mo au plus) | Le film se fait écran ouvert, avec une barre de progression, comme le film d'album aujourd'hui. |

Risque connu : un vieux téléphone peut refuser l'encodage matériel. Media3 bascule alors sur le logiciel, plus lent. Si ça échoue malgré tout, l'appli dit « Film impossible sur ce téléphone » et garde les photos.

## 3. Durée et poids

| Film | Contenu | Durée proposée | Poids à 1,5 Mbit/s |
|---|---|---|---|
| Film d'étape | Les photos de l'étape, des plus anciennes aux plus récentes, de tous les participants | 2,5 s par photo, 15 à 45 s | 3 à 9 Mo |
| Film du voyage | Les films d'étape bout à bout, avec le nom de chaque étape en titre (2 s) | Environ 1 à 3 min | 12 à 35 Mo |

Repère : 1 minute à 1,5 Mbit/s pèse environ 11 Mo.

## 4. Stockage : deux façons de faire

**A. Chaque téléphone refait le film** (comme le film d'album aujourd'hui)
- Rien de nouveau sur le serveur : 0 € et aucune règle à changer.
- Mais chacun attend 10 à 30 s avant de voir le film. Un téléphone faible peut échouer. Et deux personnes ne voient pas toujours le même film si une photo arrive entre-temps.

**B. Le film est fait une fois, puis envoyé dans Storage** (proposition)
- Un participant fabrique le film sur son téléphone. Il l'envoie dans `trips/{voyage}/films/{étape}.mp4` (10 Mo au plus pour une étape, 40 Mo pour le voyage), et un document dans Firestore l'annonce.
- Tout le monde voit le même film tout de suite, même sur un téléphone faible, et peut le télécharger.
- Coût : le bucket des photos est en us-west1, une région où Storage a une part gratuite (5 Go stockés, environ 1 Go téléchargé par jour). Pour la famille, 20 films de 10 Mo font 200 Mo stockés ; vus par 13 personnes, environ 2,6 Go téléchargés en tout, étalés sur le voyage. On reste très probablement à 0 €, mais c'est à vérifier sur la facture du premier voyage.
- À ajouter : une règle Storage (lecture par `readerIds`, écriture par un participant, `video/mp4` seulement, taille bornée), puis la suppression des films avec l'étape et avec le voyage (dans `deleteTripContents`). Tout ça passe par le relecteur renfort, et il faudra **Publier Firebase : oui**.

## 5. Publication jour par jour sur le fil d'actualité

Contrainte du projet : pas de nouvelle tâche planifiée côté serveur (pas de `onSchedule`). Le film ne peut donc pas partir tout seul chaque soir depuis le serveur.

Proposition :
1. Dans l'étape, un participant voit **« Publier le film de la journée »** dès qu'il y a au moins 2 photos du jour. Le bouton principal reste « Voir » (règle 32, DESIGN-79 B) ; « Publier », « Télécharger » et « Exporter vers une IA » sont dans le menu.
2. Le lendemain matin, si personne ne l'a publié, l'étape le propose doucement dans une ligne, sans notification.
3. Publier fabrique le film (façon B), l'envoie, et crée une carte « Film de l'étape » sur le fil, vue seulement par les `readerIds` du voyage.
4. **Une seule notification** à tout le monde : « Du nouveau dans le voyage … », avec un lien vers le voyage (règle 34). Elle part d'un déclencheur à la création du document (`onDocumentCreated`, comme les photos d'événement), pas d'une tâche planifiée. Les photos du même jour ne déclenchent pas une deuxième annonce : la rafale de photos en cours est fusionnée avec le film.
5. Une étape de plusieurs jours a un film par jour, puis un film d'étape qui les reprend.

## 6. Télécharger sur le téléphone (règle 32)

Le film va dans le dossier Films de la galerie (`MediaStore`). Il n'y a besoin d'aucune autorisation depuis Android 10. Pour Android 8 et 9 (minSdk 26), il faut une autorisation d'écriture, ou on passe par la feuille de partage d'Android, comme aujourd'hui. Proposition : la feuille de partage sur Android 8 et 9, et la galerie au-delà.

## 7. Exporter vers une IA (règle 33)

Ce n'est pas de l'encodage : c'est un prompt à copier, selon les critères de l'admin et une ambiance choisie (« road trip », « carnet doux », « souvenirs d'enfance »…), plus les photos ou le film à partager vers l'appli d'IA de son choix. Ça peut venir après les films, dans une PR à part.

## 8. Découpage proposé (une PR à la fois)

1. Film d'étape sur le téléphone, « Voir » et partager (façon A, aucun serveur).
2. Film du voyage fait des films d'étape, avec les titres.
3. Publication sur le fil (façon B) : Storage, Firestore, règles, suppression, une notification. PR mixte, avec renfort, **Publier Firebase : oui**.
   Deux conditions dès la conception (relecture de #554) : une règle Storage qui borne `contentType` et taille, et un seul film publié par étape et par jour, avec un identifiant de document déterministe `{étape}_{date}`. Ainsi, deux participants qui publient en même temps ne produisent pas deux annonces.
4. Télécharger dans la galerie.
5. Son : notes vocales ou musique, selon le choix de l'admin.
6. Export vers une IA (règle 33).

## 9. Ce que l'admin doit trancher

1. **Stockage** : B, le film envoyé une fois et vu par tous (proposé), ou A, chaque téléphone le refait (aucun serveur, mais lent et parfois différent) ?
2. **Publication** : un bouton « Publier le film de la journée » avec un rappel discret le lendemain (proposé), ou publication automatique à l'ouverture de l'appli par un participant le lendemain ?
3. **Son** : muet d'abord (proposé), puis notes vocales de l'étape, ou une musique libre de droits fournie avec l'appli ?
4. **Durée** : 2,5 s par photo et un film de voyage de 3 min au plus (proposé) ?
